package pl.edu.bidroom.server.transport;

import com.fasterxml.jackson.databind.JsonNode;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.*;
import pl.edu.bidroom.server.persistence.*;
import pl.edu.bidroom.server.service.*;
import java.time.*;
import java.util.*;
import static pl.edu.bidroom.common.BusinessException.require;

public final class Dispatcher {
    private final Database db;private final AuthService auth;private final AuctionService auctions;private final CommandService commands;private final LogService logs;private final ReportService reports;private final Clock clock;private final ServerConfig config;
    private final PhotoService photos;private final Metrics metrics;
    @FunctionalInterface public interface Metrics {Object get() throws Exception;}
    private final RateLimiter limiter=new RateLimiter();
    public Dispatcher(Database db,AuthService auth,AuctionService auctions,CommandService commands,LogService logs,ReportService reports,PhotoService photos,Clock clock,ServerConfig config,Metrics metrics) {
        this.db=db;this.auth=auth;this.auctions=auctions;this.commands=commands;this.logs=logs;this.reports=reports;this.clock=clock;this.config=config;this.photos=photos;this.metrics=metrics;
    }
    public Wire dispatch(ClientSessionHandler session,Wire request) throws Exception {
        UUID id=request.requestId();require(id!=null&&"REQUEST".equals(request.kind())&&request.operation()!=null,"BAD_REQUEST","Niepoprawna koperta.");
        require(request.protocolVersion()==2,"PROTOCOL_UNSUPPORTED","Nieobsługiwana wersja protokołu.");
        require(session.hello||"HELLO".equals(request.operation()),"BAD_REQUEST","Najpierw wyślij HELLO.");
        JsonNode p=request.payload();require(p!=null&&p.isObject(),"BAD_REQUEST","Nieprawidłowe dane polecenia.");String op=request.operation();
        if(op.equals("HELLO")) { session.hello=true;return ok(id,Map.of("serverVersion","1.1.1","maxClients",config.maxClients(),"maxLiveAuctions",config.maxAuctions(),"available",true)); }
        if(op.equals("PING"))return ok(id,Map.of("available",true));
        if(op.equals("REGISTER")) { limiter.check(session.address());return ok(id,auth.register(Json.read(p,Registration.class))); }
        if(op.equals("LOGIN")) {
            limiter.check(session.address());AuthService.Login login=auth.login(Json.read(p,Credentials.class));
            if(session.userId!=null)reports.clear(session.userId);photos.clear(session.userId);auth.logout(session.token);session.subscriptions.clear();session.token=login.token();session.userId=login.user().id();
            logs.record("LOGIN","Uwierzytelniono użytkownika.");return ok(id,login);
        }
        if(op.equals("SESSION_RESUME")) { String token=string(p,"token",256);User user=auth.authenticate(token);session.token=token;session.userId=user.id();session.subscriptions.clear();return ok(id,Map.of("user",user)); }
        User user=auth.authenticate(session.token);session.userId=user.id();
        if(op.startsWith("ADMIN_"))require(user.admin(),"FORBIDDEN","Wymagane uprawnienia administratora.");
        switch(op) {
            case "LOGOUT" -> { auth.logout(session.token);reports.clear(user.id());photos.clear(user.id());session.token=null;session.userId=null;session.subscriptions.clear();return ok(id,Map.of()); }
            case "CREATE_DRAFT","UPDATE_DRAFT" -> { return auctions.mutate(user,id,op,Json.read(p,Draft.class)); }
            case "PUBLISH_AUCTION" -> { return auctions.mutate(user,id,op,Json.read(p,Publish.class)); }
            case "PLACE_BID" -> { return auctions.mutate(user,id,op,Json.read(p,PlaceBid.class)); }
            case "CANCEL_AUCTION" -> { return auctions.mutate(user,id,op,Json.read(p,Cancel.class)); }
            case "GET_COMMAND_RESULT" -> { return ok(id,db.read(c->commands.result(c,user.id(),uuid(p,"commandRequestId")))); }
            case "LIST_AUCTIONS" -> {
                int size=pageSize(p,20),offset=offset(p);String view=optional(p,"view","active",20);
                if(p.path("mine").asBoolean(false))view="mine";String selectedView=view;
                List<Auction> items=db.read(c->auctions.repository().catalog(c,user,selectedView,optional(p,"query","",120),optional(p,"category","",30),offset,size+1));
                return page(id,items.stream().map(a->{var n=Json.node(a).deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)n).remove("description");return n;}).toList(),offset,size);
            }
            case "GET_AUCTION","SUBSCRIBE_AUCTION" -> {
                UUID auction=uuid(p,"auctionId");boolean added=false;
                if(op.equals("SUBSCRIBE_AUCTION")) { require(session.subscriptions.contains(auction)||session.subscriptions.size()<4,"CAPACITY_REACHED","Możesz obserwować do 4 aukcji.");added=session.subscriptions.add(auction); }
                try { Auction a=db.read(c->auctions.repository().get(c,auction,false));AuctionRepository.visible(a,user);return ok(id,a); }
                catch(Exception e) { if(added)session.subscriptions.remove(auction);throw e; }
            }
            case "UNSUBSCRIBE_AUCTION" -> { session.subscriptions.remove(uuid(p,"auctionId"));return ok(id,Map.of()); }
            case "GET_BIDS" -> {
                UUID auction=uuid(p,"auctionId");long after=number(p,"afterSequence",0,Long.MAX_VALUE,0);int limit=pageSize(p,50);
                return ok(id,db.read(c->{Auction a=auctions.repository().get(c,auction,false);AuctionRepository.visible(a,user);var bids=auctions.repository().bids(c,auction,after,limit+1);boolean more=bids.size()>limit;var items=bids.subList(0,Math.min(limit,bids.size()));return Map.of("items",items,"hasMore",more,"nextSequence",items.isEmpty()?after:items.getLast().sequence());}));
            }
            case "ADMIN_CREATE_USER","ADMIN_SET_USER_STATUS" -> {
                Object input=op.equals("ADMIN_CREATE_USER")?Json.read(p,NewUser.class):Json.read(p,UserStatus.class);
                // Hashing is bounded and performed without holding a pooled connection.
                PasswordHasher.Hash hash=input instanceof NewUser n?auth.hash(n.password()):null;
                var outcome=db.transaction(c->{Sql.advisory(c,AuthService.ADMIN_LOCK);User active=auth.active(c,user.id(),true);return commands.execute(c,active,id,op,input,()->input instanceof NewUser n?auth.adminCreate(c,active,n,hash):auth.setStatus(c,active,(UserStatus)input));});
                if(input instanceof UserStatus s&&!s.enabled()&&Boolean.TRUE.equals(outcome.response().ok()))auth.revoke(s.userId());
                return outcome.response();
            }
            case "ADMIN_LIST_USERS" -> { int size=pageSize(p,20),offset=offset(p);return page(id,db.read(c->auth.users(c,optional(p,"query","",32),offset,size+1)),offset,size); }
            case "ADMIN_GET_LOGS" -> {
                int size=pageSize(p,20),offset=offset(p);
                @SuppressWarnings("unchecked") List<Object> rows=(List<Object>)logs.query(optional(p,"origin","",10),optional(p,"userId","",36),optional(p,"auctionId","",36),optional(p,"level","",10),instant(p,"from",Instant.parse("2000-01-01T00:00:00Z")),instant(p,"to",clock.instant().plusSeconds(1)),offset,size+1);
                return page(id,rows,offset,size);
            }
            case "LOG_BATCH" -> {
                JsonNode list=p.path("entries");require(list.isArray()&&list.size()<=50,"BAD_REQUEST","Nieprawidłowa paczka logów.");
                List<DurableLog.Entry> entries=new ArrayList<>();for(JsonNode entry:list)entries.add(Json.read(entry,DurableLog.Entry.class));
                return ok(id,Map.of("eventIds",logs.clientBatch(user.id(),entries)));
            }
            case "ADMIN_SERVER_STATS" -> { return ok(id,metrics.get()); }
            case "PHOTO_BEGIN" -> { return ok(id,photos.begin(user,Json.read(p,PhotoService.Begin.class))); }
            case "PHOTO_CHUNK" -> { return ok(id,photos.append(user,uuid(p,"uploadId"),number(p,"offset",0,PhotoService.MAX_BYTES,0),string(p,"base64",22000))); }
            case "PHOTO_FINISH" -> { return photos.finish(user,id,uuid(p,"uploadId")); }
            case "PHOTO_ABORT" -> { photos.abort(user,uuid(p,"uploadId"));return ok(id,Map.of()); }
            case "GET_PHOTO" -> { return ok(id,photos.info(user,uuid(p,"auctionId"))); }
            case "GET_PHOTO_CHUNK" -> { return ok(id,photos.chunk(user,uuid(p,"auctionId"),number(p,"version",1,Long.MAX_VALUE,1),number(p,"offset",0,PhotoService.MAX_BYTES,0))); }
            case "GET_REPORT_STATUS" -> { return ok(id,reports.status(user,uuid(p,"auctionId"))); }
            case "EXPORT_REPORT_START" -> { return ok(id,reports.start(user,uuid(p,"auctionId"))); }
            case "EXPORT_REPORT_CHUNK" -> { return ok(id,reports.chunk(user,uuid(p,"reportId"),number(p,"offset",0,Long.MAX_VALUE,0))); }
            default -> throw new BusinessException("BAD_REQUEST","Nieznane polecenie.");
        }
    }
    private Wire page(UUID id,List<?> rows,int offset,int size) {
        int count=Math.min(size,rows.size());Wire result;
        do { result=ok(id,Map.of("items",rows.subList(0,count),"nextOffset",offset+count,"hasMore",count<rows.size()));if(Json.bytes(result).length<=60000)return result;count--; }while(count>0);
        throw new BusinessException("INTERNAL_ERROR","Nie można przygotować strony wyników.");
    }
    private Wire ok(UUID id,Object body) { return Wire.success(id,clock.instant(),body); }
    public static UUID uuid(JsonNode p,String name) { try { return UUID.fromString(string(p,name,36)); }catch(IllegalArgumentException e) { throw new BusinessException("BAD_REQUEST","Nieprawidłowy identyfikator."); } }
    private static String string(JsonNode p,String name,int max) { JsonNode n=p.get(name);require(n!=null&&n.isTextual()&&n.textValue().length()<=max,"BAD_REQUEST","Nieprawidłowe pole: "+name);return n.textValue(); }
    private static String optional(JsonNode p,String name,String fallback,int max) { return p.hasNonNull(name)?string(p,name,max):fallback; }
    private static long number(JsonNode p,String name,long min,long max,long fallback) { if(!p.has(name))return fallback;JsonNode n=p.get(name);require(n.isIntegralNumber()&&n.canConvertToLong()&&n.longValue()>=min&&n.longValue()<=max,"BAD_REQUEST","Nieprawidłowe pole: "+name);return n.longValue(); }
    private static int pageSize(JsonNode p,int fallback) { return (int)number(p,"pageSize",1,100,number(p,"limit",1,100,fallback)); }
    private static int offset(JsonNode p) { return (int)number(p,"offset",0,1_000_000,0); }
    private static Instant instant(JsonNode p,String name,Instant fallback) { try { return p.hasNonNull(name)?Instant.parse(string(p,name,40)):fallback; }catch(Exception e) { throw new BusinessException("BAD_REQUEST","Nieprawidłowa data."); } }
}
