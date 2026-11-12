package pl.edu.bidroom.server;

import org.junit.jupiter.api.*;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.persistence.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class ServerIT {
    private ServerApp server;private ServerConfig config;private MutableClock clock;
    private final List<RpcConnection> sockets=new ArrayList<>();
    private final String password=UUID.randomUUID().toString();
    private static final class MutableClock extends Clock {
        final AtomicReference<Instant> value=new AtomicReference<>(Instant.parse("2026-11-10T10:00:00Z"));
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return value.get();}
    }
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("BIDROOM_TEST_DB_URL");assertNotNull(url,"Test integracyjny wymaga BIDROOM_TEST_DB_URL i prawdziwego PostgreSQL.");
        assertTrue(url.matches("jdbc:postgresql://[^/]+/bidroom_test"),"Dozwolona wyłącznie baza bidroom_test.");
        config=new ServerConfig("local","127.0.0.1",0,url,System.getenv("BIDROOM_DB_USER"),System.getenv("BIDROOM_DB_PASSWORD"),4,16,8,false,null,null,System.getenv("BIDROOM_COMMAND_HMAC_SECRET"),Path.of(System.getenv("BIDROOM_TEST_DATA_DIR")),"admin_test",password);
        try(Database db=new Database(config)) {
            assertEquals("bidroom_test",db.read(c->Sql.one(c,"SELECT current_database()",r->r.getString(1))));
            db.transaction(c->{Sql.update(c,"DROP TRIGGER IF EXISTS bidroom_test_failure ON auction_event");Sql.update(c,"TRUNCATE auction_photo,auction_report,diagnostic_log,security_audit,command_result,auction_event,bid,auction,app_user");return null;});
        }
        clock=new MutableClock();server=new ServerApp(config,clock);
    }
    @AfterEach void cleanup() { sockets.forEach(RpcConnection::close);if(server!=null)server.close(); }
    private RpcConnection connect() throws Exception {
        RpcConnection c=new RpcConnection("127.0.0.1",server.port(),false,e->{});sockets.add(c);assertTrue(c.request("HELLO",Map.of("installationId",UUID.randomUUID())).ok());return c;
    }
    private RpcConnection user(String login) throws Exception {
        RpcConnection c=connect();c.request("REGISTER",new Registration(login,"Użytkownik "+login,password)).checked();c.request("LOGIN",new Credentials(login,password)).checked();return c;
    }
    private Auction draft(RpcConnection owner) throws Exception { return photo(owner,Json.read(owner.request("CREATE_DRAFT",new Draft(null,0,"Aukcja testowa","Zażółć gęślą jaźń","BOOKS",100,10,null,90)).checked(),Auction.class)); }
    private byte[] image() throws Exception {var image=new java.awt.image.BufferedImage(32,24,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",out);return out.toByteArray();}
    private Auction photo(RpcConnection client,Auction a) throws Exception {return PhotoTransfer.upload((op,p)->client.request(op,p).checked(),a,image());}
    private Auction published(RpcConnection owner) throws Exception { Auction d=draft(owner);return Json.read(owner.request("PUBLISH_AUCTION",new Publish(d.auctionId(),d.version())).checked(),Auction.class); }
    private Auction get(RpcConnection c,UUID id) throws Exception { return Json.read(c.request("GET_AUCTION",Map.of("auctionId",id)).checked(),Auction.class); }
    private void advance(long seconds) { clock.value.updateAndGet(t->t.plusSeconds(seconds));server.auctions().runtimes().wakeAll(); }
    private static void eventually(BooleanSupplier condition) throws Exception { long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!condition.getAsBoolean()&&System.nanoTime()<deadline)TimeUnit.MILLISECONDS.sleep(20);assertTrue(condition.getAsBoolean()); }
    @Test void twoEqualBidsRaceAndOnlyOneCommits() throws Exception {
        RpcConnection owner=user("owner"),one=user("first"),two=user("second");Auction a=published(owner);advance(50);
        CountDownLatch gate=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Wire> f1=executor.submit(()->{gate.await();return one.request("PLACE_BID",new PlaceBid(a.auctionId(),100));});
            Future<Wire> f2=executor.submit(()->{gate.await();return two.request("PLACE_BID",new PlaceBid(a.auctionId(),100));});gate.countDown();
            var replies=List.of(f1.get(10,TimeUnit.SECONDS),f2.get(10,TimeUnit.SECONDS));assertEquals(1,replies.stream().filter(Wire::ok).count());
        }
        Auction after=get(owner,a.auctionId());assertEquals(1,after.bidCount());assertEquals(100,after.currentPrice());assertEquals(clock.instant().plusSeconds(60),after.endAt());
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM bid",r->r.getLong(1))));
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM auction_event WHERE event_type='EXTENDED'",r->r.getLong(1))));
    }
    @Test void duplicateAcrossSocketsAndAfterRestartHasOneEffect() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder"),second=connect();
        second.request("LOGIN",new Credentials("bidder",password)).checked();Auction a=published(owner);advance(50);UUID id=UUID.randomUUID();PlaceBid bid=new PlaceBid(a.auctionId(),100);
        CountDownLatch gate=new CountDownLatch(1);Wire first;
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Wire> f1=executor.submit(()->{gate.await();return bidder.request(id,"PLACE_BID",bid);});Future<Wire> f2=executor.submit(()->{gate.await();return second.request(id,"PLACE_BID",bid);});gate.countDown();first=f1.get(10,TimeUnit.SECONDS);assertEquals(first,f2.get(10,TimeUnit.SECONDS));
        }
        assertTrue(first.ok());assertEquals("REQUEST_ID_CONFLICT",bidder.request(id,"PLACE_BID",new PlaceBid(a.auctionId(),110)).error().code());
        Map<String,Object> reordered=new LinkedHashMap<>();reordered.put("amount",100);reordered.put("auctionId",a.auctionId());assertEquals(first,bidder.request(id,"PLACE_BID",reordered));
        server.close();clock.value.updateAndGet(t->t.plusSeconds(70));server=new ServerApp(config,clock);RpcConnection again=connect();again.request("LOGIN",new Credentials("bidder",password)).checked();
        assertEquals(first,again.request(id,"PLACE_BID",bid));Auction after=get(again,a.auctionId());assertEquals(Status.FINISHED,after.status());assertEquals(1,after.bidCount());assertEquals(first.payload().path("endAt").asText(),after.endAt().toString());
        assertEquals("FOUND",again.request("GET_COMMAND_RESULT",Map.of("commandRequestId",id)).checked().path("status").asText());
    }
    @Test void oneAuctionLockDoesNotBlockAnother() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder");Auction a=published(owner),b=published(owner);
        try(var lease=server.auctions().runtimes().acquire(a.auctionId());ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Wire> independent=executor.submit(()->bidder.request("PLACE_BID",new PlaceBid(b.auctionId(),100)));
            assertTrue(independent.get(3,TimeUnit.SECONDS).ok());
        }
    }
    @Test void bidAndDeadlineUseFreshProtectedClock() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder");Auction a=published(owner);advance(85);
        assertTrue(bidder.request("PLACE_BID",new PlaceBid(a.auctionId(),100)).ok());advance(6);
        assertEquals(Status.ACTIVE,get(owner,a.auctionId()).status());advance(29);
        Wire late=bidder.request("PLACE_BID",new PlaceBid(a.auctionId(),110));assertFalse(late.ok());
        eventually(()->{try{return get(owner,a.auctionId()).status()==Status.FINISHED;}catch(Exception e){return false;}});
        assertEquals(1,get(owner,a.auctionId()).bidCount());
    }
    @Test void privateDraftStaysPrivateAfterCancelAndAdminChecks() throws Exception {
        RpcConnection owner=user("owner"),stranger=user("stranger");Auction d=draft(owner);
        assertEquals("NOT_FOUND",stranger.request("GET_AUCTION",Map.of("auctionId",d.auctionId())).error().code());
        owner.request("CANCEL_AUCTION",new Cancel(d.auctionId(),"Rezygnacja właściciela")).checked();
        assertEquals(0,stranger.request("LIST_AUCTIONS",Map.of("view","history")).checked().path("items").size());
        assertEquals("NOT_FOUND",stranger.request("EXPORT_REPORT_START",Map.of("auctionId",d.auctionId())).error().code());
        assertEquals("FORBIDDEN",stranger.request("ADMIN_LIST_USERS",Map.of()).error().code());
        RpcConnection publicClient=connect();Wire injected=publicClient.request("REGISTER",Map.of("login","intruder","displayName","Test","password",password,"role","ADMIN"));assertFalse(injected.ok());
        assertFalse(owner.request("REGISTER",new Registration("OWNER","Duplikat",password)).ok());
    }
    @Test void accountBlockRevokesSessionAndLastAdminIsProtected() throws Exception {
        RpcConnection user=user("bidder"),admin=connect();JsonNodeHolder info=new JsonNodeHolder(admin.request("LOGIN",new Credentials("admin_test",password)).checked());
        Long adminId=info.node.path("user").path("id").asLong();
        assertEquals("FORBIDDEN",admin.request("ADMIN_SET_USER_STATUS",new UserStatus(adminId,false,"Próba blokady")).error().code());
        var list=admin.request("ADMIN_LIST_USERS",Map.of("query","bidder")).checked();Long id=list.path("items").get(0).path("id").asLong();
        admin.request("ADMIN_SET_USER_STATUS",new UserStatus(id,false,"Blokada testowa")).checked();eventually(()->!user.isOpen());
        assertEquals("INVALID_CREDENTIALS",connect().request("LOGIN",new Credentials("bidder",password)).error().code());
    }
    private record JsonNodeHolder(com.fasterxml.jackson.databind.JsonNode node) { }
    @Test void reportAndClientLogsAreDurableAndDeduplicated() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder");Auction a=published(owner);advance(70);bidder.request("PLACE_BID",new PlaceBid(a.auctionId(),150)).checked();advance(51);
        eventually(()->{try{return get(owner,a.auctionId()).status()==Status.FINISHED;}catch(Exception e){return false;}});
        var entry=new DurableLog.Entry(UUID.randomUUID(),UUID.randomUUID(),clock.instant(),"INFO","TEST_EVENT",a.auctionId(),null,"Połączono");
        for(int i=0;i<2;i++)bidder.request("LOG_BATCH",Map.of("entries",List.of(entry))).checked();
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM diagnostic_log WHERE origin='CLIENT'",r->r.getLong(1))));
        var export=owner.request("EXPORT_REPORT_START",Map.of("auctionId",a.auctionId())).checked();UUID report=UUID.fromString(export.path("reportId").asText());
        assertEquals("REPORT_EXPIRED",bidder.request("EXPORT_REPORT_CHUNK",Map.of("reportId",report,"offset",0)).error().code());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();long offset=0;
        while(true) { var chunk=owner.request("EXPORT_REPORT_CHUNK",Map.of("reportId",report,"offset",offset)).checked();bytes.write(Base64.getDecoder().decode(chunk.path("base64").asText()));offset=chunk.path("nextOffset").asLong();if(chunk.path("eof").asBoolean())break; }
        assertEquals(export.path("bytes").asLong(),bytes.size());assertEquals(export.path("sha256").asText(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
        String text=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(text.contains("150 pkt"));assertTrue(text.contains("PRZEDŁUŻENIA"));assertFalse(text.contains(password));
    }
    @Test void sqlFailureRollsBackBidExtensionAndResultTogether() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder");Auction a=published(owner);advance(70);
        server.database().transaction(c->{Sql.update(c,"CREATE OR REPLACE FUNCTION bidroom_test_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test fault'; END $$");Sql.update(c,"CREATE TRIGGER bidroom_test_failure BEFORE INSERT ON auction_event FOR EACH ROW EXECUTE FUNCTION bidroom_test_failure()");return null;});
        UUID request=UUID.randomUUID();try { Wire reply=bidder.request(request,"PLACE_BID",new PlaceBid(a.auctionId(),100));assertFalse(reply.ok()); }catch(IOException expected) { }
        try(Database independent=new Database(config)) {
            Auction after=independent.read(c->new AuctionRepository().get(c,a.auctionId(),false));assertEquals(0,after.bidCount());assertEquals(a.endAt(),after.endAt());assertEquals(a.version(),after.version());
            assertEquals(0L,independent.<Long>read(c->Sql.one(c,"SELECT count(*) FROM command_result WHERE request_id=?",r->r.getLong(1),request)));
        }
    }
    @Test void secondServerRejectedAndMalformedSocketReleasesCapacity() throws Exception {
        assertThrows(IllegalStateException.class,()->new ServerApp(config,clock));
        try(Socket malformed=new Socket("127.0.0.1",server.port())) { malformed.getOutputStream().write(ByteBuffer.allocate(4).putInt(Integer.MAX_VALUE).array());malformed.setSoTimeout(2000);assertEquals(-1,malformed.getInputStream().read()); }
        eventually(()->server.clientCount()==0);try(RpcConnection c=connect()) { assertTrue(c.request("PING",Map.of()).ok()); }
    }
    @Test void concurrentRegistrationRespectsNormalizedLogin() throws Exception {
        RpcConnection one=connect(),two=connect();CountDownLatch gate=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Wire> first=executor.submit(()->{gate.await();return one.request("REGISTER",new Registration("Same.Login","Pierwszy",password));});
            Future<Wire> second=executor.submit(()->{gate.await();return two.request("REGISTER",new Registration("same.login","Drugi",password));});gate.countDown();
            assertEquals(1,List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)).stream().filter(Wire::ok).count());
        }
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM app_user WHERE login_normalized='same.login'",r->r.getLong(1))));
    }
    @Test void scheduledAndEmptyAuctionsFinishOnceAndReleaseCapacity() throws Exception {
        RpcConnection owner=user("owner");
        Auction d=Json.read(owner.request("CREATE_DRAFT",new Draft(null,0,"Zaplanowana aukcja","","OTHER",100,10,clock.instant().plusSeconds(30),60)).checked(),Auction.class);
        d=photo(owner,d);Auction a=Json.read(owner.request("PUBLISH_AUCTION",new Publish(d.auctionId(),d.version())).checked(),Auction.class);assertEquals(Status.SCHEDULED,a.status());
        advance(30);eventually(()->{try{return get(owner,a.auctionId()).status()==Status.ACTIVE;}catch(Exception e){return false;}});
        advance(60);eventually(()->{try{return get(owner,a.auctionId()).status()==Status.FINISHED;}catch(Exception e){return false;}});
        Auction ended=get(owner,a.auctionId());assertNull(ended.winnerId());assertNull(ended.salePrice());eventually(()->server.auctions().liveCount()==0);
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM auction_event WHERE event_type='FINISHED'",r->r.getLong(1))));
    }
    @Test void concurrentAdminBlocksCannotRemoveLastAdmin() throws Exception {
        RpcConnection admin1=connect();User first=Json.read(admin1.request("LOGIN",new Credentials("admin_test",password)).checked().path("user"),User.class);
        User second=Json.read(admin1.request("ADMIN_CREATE_USER",new NewUser("admin_two","Drugi administrator",password,"ADMIN")).checked(),User.class);
        RpcConnection admin2=connect();admin2.request("LOGIN",new Credentials("admin_two",password)).checked();CountDownLatch gate=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Void> one=()->{gate.await();try{admin1.request("ADMIN_SET_USER_STATUS",new UserStatus(second.id(),false,"Próba równoczesna"));}catch(IOException ignored){}return null;};
            Callable<Void> two=()->{gate.await();try{admin2.request("ADMIN_SET_USER_STATUS",new UserStatus(first.id(),false,"Próba równoczesna"));}catch(IOException ignored){}return null;};
            var a=executor.submit(one);var b=executor.submit(two);gate.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM app_user WHERE role='ADMIN' AND enabled",r->r.getLong(1))));
    }
    @Test void cancellationAndBidShareOneSerializationOrder() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder"),admin=connect();admin.request("LOGIN",new Credentials("admin_test",password)).checked();Auction a=published(owner);advance(70);
        CountDownLatch gate=new CountDownLatch(1);Wire bid;
        try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Wire> first=executor.submit(()->{gate.await();return bidder.request("PLACE_BID",new PlaceBid(a.auctionId(),100));});Future<Wire> second=executor.submit(()->{gate.await();return admin.request("CANCEL_AUCTION",new Cancel(a.auctionId(),"Test porządku operacji"));});gate.countDown();bid=first.get(10,TimeUnit.SECONDS);assertTrue(second.get(10,TimeUnit.SECONDS).ok());
        }
        Auction after=get(owner,a.auctionId());assertEquals(Status.CANCELLED,after.status());assertNull(after.winnerId());assertEquals(Boolean.TRUE.equals(bid.ok())?1:0,after.bidCount());assertFalse(bidder.request("PLACE_BID",new PlaceBid(a.auctionId(),110)).ok());
    }
    @Test void capacityIsReleasedOnlyWhenLifecycleEnds() throws Exception {
        RpcConnection owner=user("owner"),admin=connect();admin.request("LOGIN",new Credentials("admin_test",password)).checked();List<Auction> all=new ArrayList<>();for(int i=0;i<8;i++)all.add(published(owner));
        Auction extra=draft(owner);assertEquals("CAPACITY_REACHED",owner.request("PUBLISH_AUCTION",new Publish(extra.auctionId(),extra.version())).error().code());assertEquals(Status.DRAFT,get(owner,extra.auctionId()).status());
        admin.request("CANCEL_AUCTION",new Cancel(all.getFirst().auctionId(),"Zwolnienie miejsca")).checked();eventually(()->server.auctions().liveCount()==7);
        assertTrue(owner.request("PUBLISH_AUCTION",new Publish(extra.auctionId(),extra.version())).ok());
    }
    @Test void lossOfInstanceLockStopsServiceAndRestartReconciles() throws Exception {
        RpcConnection owner=user("owner");Auction a=published(owner);
        Boolean terminated=server.database().read(c->Sql.one(c,"SELECT pg_terminate_backend(pid) FROM pg_locks WHERE locktype='advisory' AND objid=710000 AND granted",r->r.getBoolean(1)));assertTrue(terminated);
        eventually(()->!server.isHealthy());assertThrows(IOException.class,()->owner.request("CREATE_DRAFT",new Draft(null,0,"Niedozwolona","","OTHER",1,1,null,60)));
        server.close();advance(100);server=new ServerApp(config,clock);RpcConnection again=connect();again.request("LOGIN",new Credentials("owner",password)).checked();assertEquals(Status.FINISHED,get(again,a.auctionId()).status());
    }
    @Test void lostResponseAfterCommitIsRecoveredWithoutSecondBid() throws Exception {
        RpcConnection owner=user("owner"),bidder=user("bidder");Auction a=published(owner);advance(70);UUID request=UUID.randomUUID();
        try(Socket raw=new Socket("127.0.0.1",server.port())) {
            raw.getOutputStream().write(FrameCodec.encode(Wire.request(UUID.randomUUID(),"HELLO",Map.of())));FrameCodec.read(raw,5000).checked();
            raw.getOutputStream().write(FrameCodec.encode(Wire.request(UUID.randomUUID(),"LOGIN",new Credentials("bidder",password))));FrameCodec.read(raw,5000).checked();
            raw.getOutputStream().write(FrameCodec.encode(Wire.request(request,"PLACE_BID",new PlaceBid(a.auctionId(),100))));raw.getOutputStream().flush();
            eventually(()->{try{return server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM command_result WHERE request_id=?",r->r.getLong(1),request))==1;}catch(Exception e){return false;}});
            // Intentionally close without consuming the committed response.
        }
        Wire replay=bidder.request(request,"PLACE_BID",new PlaceBid(a.auctionId(),100));assertTrue(replay.ok());Auction current=get(owner,a.auctionId());assertEquals(1,current.bidCount());assertEquals(clock.instant().plusSeconds(50),current.endAt());
    }
    @Test void lostJdbcCommitAcknowledgementStopsServerAndReplayRecoversOneBid() throws Exception {
        server.close();
        try (PgCommitFaultProxy proxy = new PgCommitFaultProxy(config.dbUrl())) {
            ServerConfig throughProxy = new ServerConfig(config.profile(),config.host(),config.port(),proxy.jdbcUrl(),
                config.dbUser(),config.dbPassword(),config.poolSize(),config.maxClients(),config.maxAuctions(),
                config.tls(),config.keyStore(),config.keyPassword(),config.hmacSecret(),config.dataDir(),
                config.adminLogin(),config.adminPassword());
            server = new ServerApp(throughProxy,clock);
            RpcConnection owner=user("owner"),bidder=user("bidder");Auction auction=published(owner);advance(70);
            UUID request=UUID.randomUUID();PlaceBid input=new PlaceBid(auction.auctionId(),100);
            proxy.arm();
            try {
                Wire uncertain=bidder.request(request,"PLACE_BID",input);
                assertFalse(uncertain.ok(),"JDBC has not confirmed the commit");
                assertEquals("RESULT_UNKNOWN",uncertain.error().code());
            } catch (IOException expected) { /* fail-stop can close the socket before the response */ }
            assertTrue(proxy.awaitDropped(),"The actual PostgreSQL COMMIT acknowledgement must be intercepted");
            eventually(()->!server.isHealthy());
            Wire committed;
            try (Database independent=new Database(config)) {
                Auction persisted=independent.read(c->new AuctionRepository().get(c,auction.auctionId(),false));
                assertEquals(1,persisted.bidCount());assertEquals(auction.version()+1,persisted.version());
                assertEquals(clock.instant().plusSeconds(50),persisted.endAt());
                assertEquals(1L,independent.<Long>read(c->Sql.one(c,"SELECT count(*) FROM bid WHERE auction_id=?",r->r.getLong(1),auction.auctionId())));
                assertEquals(1L,independent.<Long>read(c->Sql.one(c,"SELECT count(*) FROM auction_event WHERE auction_id=? AND event_type='EXTENDED'",r->r.getLong(1),auction.auctionId())));
                String saved=independent.read(c->Sql.one(c,"SELECT result::text FROM command_result WHERE request_id=?",r->r.getString(1),request));
                assertNotNull(saved);committed=Json.MAPPER.readValue(saved,Wire.class);assertTrue(committed.ok());
            }
            server.close();advance(51);server=new ServerApp(config,clock);
            RpcConnection reconnected=connect();reconnected.request("LOGIN",new Credentials("bidder",password)).checked();
            assertEquals(committed,reconnected.request(request,"PLACE_BID",input));
            assertEquals(Json.node(committed),reconnected.request("GET_COMMAND_RESULT",Map.of("commandRequestId",request)).checked().path("result"));
            Auction recovered=get(reconnected,auction.auctionId());assertEquals(Status.FINISHED,recovered.status());
            assertEquals(1,recovered.bidCount());assertEquals(committed.payload().path("endAt").asText(),recovered.endAt().toString());
            assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM auction_event WHERE auction_id=? AND event_type='EXTENDED'",r->r.getLong(1),auction.auctionId())));
        }
    }
    @Test void unsupportedProtocolReturnsReasonAndClosesOnlyThatSocket() throws Exception {
        try(Socket raw=new Socket("127.0.0.1",server.port())) {
            Wire incompatible=new Wire(99,"REQUEST",UUID.randomUUID(),"HELLO",null,null,Json.node(Map.of()),null,null);
            raw.getOutputStream().write(FrameCodec.encode(incompatible));assertEquals("PROTOCOL_UNSUPPORTED",FrameCodec.read(raw,5000).error().code());
            raw.setSoTimeout(2000);assertEquals(-1,raw.getInputStream().read());
        }
        eventually(()->server.clientCount()==0);assertTrue(connect().request("PING",Map.of()).ok());
    }
    @Test void largeReportUsesBoundedChunksAndExpiredHandleIsRejected() throws Exception {
        RpcConnection owner=user("owner"),bidder=connect();String displayName="Łucja Żółć - pracownia projektowa Politechniki";
        bidder.request("REGISTER",new Registration("bidder",displayName,password)).checked();bidder.request("LOGIN",new Credentials("bidder",password)).checked();Auction a=published(owner);
        for(int i=0;i<1050;i++)bidder.request("PLACE_BID",new PlaceBid(a.auctionId(),100+i*10L)).checked();
        advance(120);eventually(()->{try{return get(owner,a.auctionId()).status()==Status.FINISHED;}catch(Exception e){return false;}});
        var export=owner.request("EXPORT_REPORT_START",Map.of("auctionId",a.auctionId())).checked();assertTrue(export.path("bytes").asLong()>65536);
        UUID id=UUID.fromString(export.path("reportId").asText());ByteArrayOutputStream output=new ByteArrayOutputStream();long offset=0;int chunks=0;
        while(true) {
            Wire response=owner.request("EXPORT_REPORT_CHUNK",Map.of("reportId",id,"offset",offset));assertTrue(FrameCodec.encode(response).length<=65540);
            var chunk=response.checked();byte[] bytes=Base64.getDecoder().decode(chunk.path("base64").asText());assertTrue(bytes.length<=16384);output.write(bytes);offset=chunk.path("nextOffset").asLong();chunks++;if(chunk.path("eof").asBoolean())break;
        }
        assertTrue(chunks>4);assertEquals(export.path("bytes").asLong(),output.size());assertEquals(export.path("sha256").asText(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(output.toByteArray())));
        String report=output.toString(java.nio.charset.StandardCharsets.UTF_8);assertTrue(report.lines().anyMatch(line->line.startsWith("1050 | "+displayName+" (#")&&line.contains(" | 10590 pkt | ")));
        var expiring=owner.request("EXPORT_REPORT_START",Map.of("auctionId",a.auctionId())).checked();advance(301);
        assertEquals("REPORT_EXPIRED",owner.request("EXPORT_REPORT_CHUNK",Map.of("reportId",expiring.path("reportId").asText(),"offset",0)).error().code());
    }
    @Test void photosArePrivateValidatedAndImmutableAfterPublication() throws Exception {
        RpcConnection owner=user("owner"),stranger=user("stranger");
        Auction empty=Json.read(owner.request("CREATE_DRAFT",new Draft(null,0,"Zdjęcie testowe","","OTHER",100,10,null,90)).checked(),Auction.class);
        assertEquals("PHOTO_REQUIRED",owner.request("PUBLISH_AUCTION",new Publish(empty.auctionId(),empty.version())).error().code());
        byte[] image=image();String sha=pl.edu.bidroom.server.service.PhotoService.sha(image);
        var begin=Map.of("auctionId",empty.auctionId(),"expectedVersion",empty.version(),"bytes",image.length,"sha256",sha);
        assertEquals("FORBIDDEN",stranger.request("PHOTO_BEGIN",begin).error().code());
        assertEquals("NOT_FOUND",stranger.request("GET_PHOTO",Map.of("auctionId",empty.auctionId())).error().code());
        Auction pictured=photo(owner,empty);assertEquals(1,pictured.photoVersion());assertEquals(empty.version()+1,pictured.version());
        byte[] downloaded=PhotoTransfer.download((op,p)->owner.request(op,p).checked(),pictured);
        assertEquals(32,javax.imageio.ImageIO.read(new ByteArrayInputStream(downloaded)).getWidth());
        Auction active=Json.read(owner.request("PUBLISH_AUCTION",new Publish(pictured.auctionId(),pictured.version())).checked(),Auction.class);
        assertArrayEquals(downloaded,PhotoTransfer.download((op,p)->stranger.request(op,p).checked(),active));
        assertEquals("FORBIDDEN",owner.request("PHOTO_BEGIN",Map.of("auctionId",active.auctionId(),"expectedVersion",active.version(),"bytes",image.length,"sha256",sha)).error().code());
        Auction other=draft(owner);byte[] invalid="This is not a photograph".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var opened=owner.request("PHOTO_BEGIN",Map.of("auctionId",other.auctionId(),"expectedVersion",other.version(),"bytes",invalid.length,"sha256",pl.edu.bidroom.server.service.PhotoService.sha(invalid))).checked();
        String upload=opened.path("uploadId").asText();var chunk=Map.of("uploadId",upload,"offset",0,"base64",Base64.getEncoder().encodeToString(invalid));
        assertEquals("PHOTO_EXPIRED",stranger.request("PHOTO_CHUNK",chunk).error().code());owner.request("PHOTO_CHUNK",chunk).checked();
        assertEquals("BAD_REQUEST",owner.request("PHOTO_CHUNK",chunk).error().code());
        assertEquals("VALIDATION_ERROR",owner.request("PHOTO_FINISH",Map.of("uploadId",upload)).error().code());assertEquals(other.photoVersion(),get(owner,other.auctionId()).photoVersion());
    }
    @Test void photoCommitReplayHasOneEffect() throws Exception {
        RpcConnection owner=user("owner");Auction a=draft(owner);byte[] image=image();
        var opened=owner.request("PHOTO_BEGIN",Map.of("auctionId",a.auctionId(),"expectedVersion",a.version(),"bytes",image.length,"sha256",pl.edu.bidroom.server.service.PhotoService.sha(image))).checked();String upload=opened.path("uploadId").asText();
        owner.request("PHOTO_CHUNK",Map.of("uploadId",upload,"offset",0,"base64",Base64.getEncoder().encodeToString(image))).checked();UUID request=UUID.randomUUID();
        Wire first=owner.request(request,"PHOTO_FINISH",Map.of("uploadId",upload));first.checked();assertEquals(first,owner.request(request,"PHOTO_FINISH",Map.of("uploadId",upload)));
        assertEquals(a.photoVersion()+1,get(owner,a.auctionId()).photoVersion());assertEquals(a.version()+1,get(owner,a.auctionId()).version());
    }
    @Test void reportsAppearWithoutDownloadAndRecoverMissingFiles() throws Exception {
        RpcConnection owner=user("owner");Auction a=published(owner);advance(100);
        eventually(()->{try{return server.database().read(c->Sql.one(c,"SELECT count(*) FROM auction_report WHERE auction_id=?",r->r.getInt(1),a.auctionId()))==1;}catch(Exception e){return false;}});
        String filename=server.database().read(c->Sql.one(c,"SELECT filename FROM auction_report WHERE auction_id=?",r->r.getString(1),a.auctionId()));
        assertTrue(filename.startsWith("aukcja-"+a.auctionNumber()+"-"));Path file=config.dataDir().resolve("reports").resolve(filename);assertTrue(Files.readString(file).contains("Brak ofert i zwycięzcy"));
        eventually(()->{try{return Files.readString(file.getParent().resolve("raport-ogolny.txt")).contains(filename);}catch(Exception e){return false;}});
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM diagnostic_log WHERE auction_id=? AND event_type='REPORT_GENERATED'",r->r.getLong(1),a.auctionId())));
        server.close();Files.delete(file);server=new ServerApp(config,clock);eventually(()->Files.isRegularFile(file));
        RpcConnection again=connect();again.request("LOGIN",new Credentials("owner",password)).checked();assertTrue(again.request("GET_REPORT_STATUS",Map.of("auctionId",a.auctionId())).checked().path("ready").asBoolean());
        assertEquals(1L,server.database().<Long>read(c->Sql.one(c,"SELECT count(*) FROM auction_report WHERE auction_id=?",r->r.getLong(1),a.auctionId())));
    }
    @Test void administratorSeesActualTaskThreadsAndUsersCannot() throws Exception {
        RpcConnection owner=user("owner"),admin=connect();admin.request("LOGIN",new Credentials("admin_test",password)).checked();Auction a=published(owner);
        assertEquals("FORBIDDEN",owner.request("ADMIN_SERVER_STATS",Map.of()).error().code());
        eventually(()->{try{return admin.request("ADMIN_SERVER_STATS",Map.of()).checked().path("tasks").path("roles").path("AUCTION").asInt()==1;}catch(Exception e){return false;}});
        var metrics=admin.request("ADMIN_SERVER_STATS",Map.of()).checked();assertEquals(2,metrics.path("clients").asInt());assertTrue(metrics.path("heapUsedBytes").asLong()>0);
        for(var item:metrics.path("tasks").path("items")){assertTrue(item.path("threadId").asLong()>0);assertTrue(item.path("virtual").asBoolean());}
        advance(100);eventually(()->{try{return admin.request("ADMIN_SERVER_STATS",Map.of()).checked().path("tasks").path("roles").path("AUCTION").asInt()==0;}catch(Exception e){return false;}});
    }
    @Test void logoutAndIdleExpiryInvalidateTokens() throws Exception {
        RpcConnection first=user("bidder"),other=connect();String token=first.request("LOGIN",new Credentials("bidder",password)).checked().path("token").asText();
        other.request("SESSION_RESUME",Map.of("token",token)).checked();first.request("LOGOUT",Map.of()).checked();assertEquals("SESSION_EXPIRED",other.request("LIST_AUCTIONS",Map.of()).error().code());
        token=first.request("LOGIN",new Credentials("bidder",password)).checked().path("token").asText();advance(1801);
        assertEquals("SESSION_EXPIRED",other.request("SESSION_RESUME",Map.of("token",token)).error().code());
    }
}

