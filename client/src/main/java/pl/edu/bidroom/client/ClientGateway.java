package pl.edu.bidroom.client;

import com.fasterxml.jackson.databind.JsonNode;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

public final class ClientGateway implements AutoCloseable {
    private final ClientConfig config;private final DurableLog logs;private final PendingBids pending;
    private final java.nio.channels.FileChannel sessionChannel;private final java.nio.channels.FileLock sessionLock;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore actions=new Semaphore(12);private final ReentrantLock authLock=new ReentrantLock();
    private volatile RpcConnection connection;private volatile String token;private volatile User user;private volatile boolean closed;
    private volatile String status="Łączenie z serwerem…";private volatile UUID observed;
    private final AtomicReference<Wire> latestSnapshot=new AtomicReference<>();private final AtomicBoolean catalogDirty=new AtomicBoolean();
    private final AtomicReference<String> notice=new AtomicReference<>();
    private record ServerClock(Instant time,long nano) { }
    private volatile ServerClock clock=new ServerClock(Instant.now(),System.nanoTime());
    public ClientGateway(ClientConfig config) throws IOException {
        this.config=config;Files.createDirectories(config.dataDir());sessionChannel=java.nio.channels.FileChannel.open(config.dataDir().resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        java.nio.channels.FileLock acquired;
        try { acquired=sessionChannel.tryLock(); }catch(java.nio.channels.OverlappingFileLockException e) { acquired=null; }
        if(acquired==null) { sessionChannel.close();throw new IOException("Ta sesja BidRoom jest już uruchomiona. Do pokazu użyj osobnych sesji klienta."); }
        sessionLock=acquired;logs=new DurableLog(config.dataDir());pending=new PendingBids(config.dataDir());
        logs.append("INFO","CLIENT_STARTED","Uruchomiono aplikację.",null,null);executor.submit((Callable<Void>)this::maintain);
    }
    public <T> void submit(Callable<T> work,java.util.function.BiConsumer<T,Exception> completion) {
        if(!actions.tryAcquire()) { completion.accept(null,new IOException("Trwa zbyt wiele operacji."));return; }
        executor.submit((Callable<Void>)()-> { try { completion.accept(work.call(),null); }catch(Exception e) { completion.accept(null,e); }finally { actions.release(); }return null; });
    }
    private void onEvent(Wire frame) {
        switch(frame.eventType()) {
            case "AUCTION_SNAPSHOT" -> { if(observed!=null&&observed.toString().equals(frame.payload().path("auctionId").asText()))latestSnapshot.accumulateAndGet(frame,(old,next)->old==null||next.payload().path("version").asLong()>old.payload().path("version").asLong()?next:old); }
            case "CATALOG_INVALIDATED" -> catalogDirty.set(true);
            case "SESSION_REVOKED" -> { token=null;user=null;notice.set("Sesja została zakończona przez serwer."); }
            default -> { }
        }
        clock=new ServerClock(frame.serverTime(),System.nanoTime());
    }
    private Void maintain() {
        int backoff=1;long lastPing=0;
        while(!closed) {
            try {
                if(connection==null||!connection.isOpen()) {
                    status="Połączenie przerwane — przywracanie…";
                    RpcConnection fresh=new RpcConnection(config.host(),config.port(),config.tls(),this::onEvent);
                    try {
                        Wire hello=fresh.request("HELLO",Map.of("installationId",logs.installationId(),"clientVersion","1.1.1"));hello.checked();synchronizeClock(hello);
                        authLock.lock();try {
                            if(token!=null) {
                                Wire resume=fresh.request("SESSION_RESUME",Map.of("token",token));
                                if(Boolean.TRUE.equals(resume.ok()))user=Json.read(resume.payload().path("user"),User.class);
                                else if(Set.of("SESSION_EXPIRED","ACCOUNT_DISABLED").contains(resume.error().code())) { token=null;user=null;notice.set("Zaloguj się ponownie, aby odzyskać stan."); }
                                else throw new IOException("Serwer chwilowo niedostępny.");
                            }
                            connection=fresh;
                            if(user!=null)recoverPending();
                            if(user!=null&&observed!=null)onEvent(Wire.event("AUCTION_SNAPSHOT",estimatedNow(),request("SUBSCRIBE_AUCTION",Map.of("auctionId",observed))));
                        } finally { authLock.unlock(); }
                        status="Połączono";catalogDirty.set(true);backoff=1;logs.append("INFO","CONNECTED","Połączono z serwerem.",null,null);
                    } catch(Exception e) { fresh.close();throw e; }
                }
                if(System.nanoTime()-lastPing>TimeUnit.SECONDS.toNanos(15)) { request("PING",Map.of());lastPing=System.nanoTime(); }
                if(user!=null)flushLogs();TimeUnit.SECONDS.sleep(1);
            } catch(InterruptedException e) { Thread.currentThread().interrupt();break; }
            catch(Exception e) {
                RpcConnection c=connection;if(c!=null)c.close();status="Serwer chwilowo niedostępny — ponawianie połączenia…";
                try { TimeUnit.MILLISECONDS.sleep(backoff*1000L+ThreadLocalRandom.current().nextInt(200)); }catch(InterruptedException interrupted) { Thread.currentThread().interrupt();break; }
                backoff=Math.min(15,backoff*2);
            }
        }
        return null;
    }
    public JsonNode request(String operation,Object body) throws IOException {
        RpcConnection c=connection;if(c==null||!c.isOpen())throw new IOException("Brak połączenia. Poczekaj na ponowne połączenie.");
        Wire result=c.request(operation,body);
        if(!Set.of("CREATE_DRAFT","UPDATE_DRAFT","PUBLISH_AUCTION","CANCEL_AUCTION","PLACE_BID","ADMIN_CREATE_USER","ADMIN_SET_USER_STATUS").contains(operation))synchronizeClock(result);
        if(!Boolean.TRUE.equals(result.ok())&&Set.of("SESSION_EXPIRED","ACCOUNT_DISABLED","AUTH_REQUIRED").contains(result.error().code())) { token=null;user=null; }
        if(!Set.of("PING","LOG_BATCH","GET_BIDS","LIST_AUCTIONS","GET_AUCTION","GET_PHOTO","GET_PHOTO_CHUNK","PHOTO_CHUNK","ADMIN_SERVER_STATS","GET_REPORT_STATUS").contains(operation))logs.append("INFO","COMMAND_RESULT",operation+": "+(Boolean.TRUE.equals(result.ok())?"OK":result.error().code()),null,result.requestId());
        return result.checked();
    }
    public User login(String login,String password) throws Exception {
        authLock.lock();try {
            JsonNode result=request("LOGIN",new Credentials(login,password));token=result.path("token").asText();user=Json.read(result.path("user"),User.class);recoverPending();catalogDirty.set(true);return user;
        }finally { authLock.unlock(); }
    }
    public void register(String login,String display,String password) throws IOException { request("REGISTER",new Registration(login,display,password)); }
    public void logout() throws IOException {
        authLock.lock();try { try { request("LOGOUT",Map.of()); }finally { token=null;user=null;observed=null;latestSnapshot.set(null); } }finally { authLock.unlock(); }
    }
    public Auction subscribe(UUID id) throws IOException {
        UUID previous=observed;observed=id;latestSnapshot.set(null);
        if(previous!=null&&!previous.equals(id))request("UNSUBSCRIBE_AUCTION",Map.of("auctionId",previous));
        return Json.read(request("SUBSCRIBE_AUCTION",Map.of("auctionId",id)),Auction.class);
    }
    public BidReceipt bid(UUID auction,long amount) throws Exception {
        if(!online()||user==null)throw new IOException("Nie można składać nowych ofert bez połączenia.");
        PendingBids.Pending record=new PendingBids.Pending(user.id(),UUID.randomUUID(),auction,amount);pending.save(record);
        logs.append("INFO","BID_SENT","Wysłano ofertę.",auction,record.requestId());Wire result=connection.request(record.requestId(),"PLACE_BID",new PlaceBid(auction,amount));
        resolve(record,result);return Json.read(result.checked(),BidReceipt.class);
    }
    private void resolve(PendingBids.Pending record,Wire result) throws IOException {
        boolean definitive=Boolean.TRUE.equals(result.ok())||!Set.of("RESULT_UNKNOWN","TEMPORARILY_UNAVAILABLE","INTERNAL_ERROR").contains(result.error().code());
        if(definitive)pending.remove(record);else { RpcConnection c=connection;if(c!=null)c.close(); }
        logs.append("INFO","BID_RESULT",Boolean.TRUE.equals(result.ok())?"Oferta przyjęta.":result.error().code(),record.auctionId(),record.requestId());
    }
    private void recoverPending() throws Exception {
        for(var record:pending.forUser(user.id(),user.legacyId())) {
            JsonNode saved=request("GET_COMMAND_RESULT",Map.of("commandRequestId",record.requestId()));
            Wire result="FOUND".equals(saved.path("status").asText())?Json.read(saved.path("result"),Wire.class):connection.request(record.requestId(),"PLACE_BID",new PlaceBid(record.auctionId(),record.amount()));
            resolve(record,result);notice.set(Boolean.TRUE.equals(result.ok())?"Odzyskano potwierdzenie oferty: "+record.amount()+" pkt.":"Odzyskany wynik oferty: "+result.error().message());
        }
    }
    private void flushLogs() throws IOException {
        var batch=logs.batch();if(batch.isEmpty())return;JsonNode ack=request("LOG_BATCH",Map.of("entries",batch));List<UUID> ids=new ArrayList<>();for(JsonNode id:ack.path("eventIds"))ids.add(UUID.fromString(id.asText()));logs.acknowledge(ids);
    }
    public void export(UUID auction,Path destination) throws Exception {
        JsonNode start=request("EXPORT_REPORT_START",Map.of("auctionId",auction));UUID id=UUID.fromString(start.path("reportId").asText());
        Path temporary=destination.resolveSibling(destination.getFileName()+"."+UUID.randomUUID()+".part");MessageDigest digest=MessageDigest.getInstance("SHA-256");long offset=0;
        try {
            try(OutputStream out=Files.newOutputStream(temporary,StandardOpenOption.CREATE_NEW)) {
                while(true) {
                    JsonNode chunk=request("EXPORT_REPORT_CHUNK",Map.of("reportId",id,"offset",offset));byte[] bytes=Base64.getDecoder().decode(chunk.path("base64").asText());out.write(bytes);digest.update(bytes);offset+=bytes.length;
                    if(offset!=chunk.path("nextOffset").asLong())throw new IOException("Niespójny fragment raportu.");if(chunk.path("eof").asBoolean())break;
                }
            }
            if(offset!=start.path("bytes").asLong()||!HexFormat.of().formatHex(digest.digest()).equals(start.path("sha256").asText()))throw new IOException("Niepoprawna suma kontrolna raportu.");
            Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
        }finally { Files.deleteIfExists(temporary); }
    }
    private void synchronizeClock(Wire reply) { if(reply.serverTime()!=null)clock=new ServerClock(reply.serverTime(),System.nanoTime()); }
    public Instant estimatedNow() { ServerClock current=clock;return current.time().plusNanos(Math.max(0,System.nanoTime()-current.nano())); }
    public boolean online() { RpcConnection c=connection;return c!=null&&c.isOpen()&&status.equals("Połączono"); }
    public User user() { return user; }public String status() { return status; }
    public Wire pollSnapshot() { return latestSnapshot.getAndSet(null); }public boolean pollCatalogDirty() { return catalogDirty.getAndSet(false); }
    public String pollNotice() { return notice.getAndSet(null); }
    public void retry() { RpcConnection c=connection;if(c!=null)c.close(); }
    @Override public void close() { closed=true;RpcConnection c=connection;if(c!=null)c.close();executor.shutdownNow();try { sessionLock.release();sessionChannel.close(); }catch(IOException ignored) { } }
}
