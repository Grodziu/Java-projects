package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.domain.BidPolicy;
import pl.edu.bidroom.server.persistence.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static pl.edu.bidroom.common.BusinessException.require;

public final class AuctionService implements AutoCloseable {
    private final Database db;private final AuthService auth;private final CommandService commands;private final Clock clock;private final TaskRegistry tasks;
    private final AuctionRepository repository=new AuctionRepository();
    private final AuctionRuntimes runtimes=new AuctionRuntimes();
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity;private final int maxAuctions;
    private volatile boolean closed;
    private Consumer<Auction> publisher=a->{ };
    private Consumer<Exception> failure=e->{ };
    public AuctionService(Database db,AuthService auth,CommandService commands,Clock clock,int maxAuctions,TaskRegistry tasks) {
        this.db=db;this.auth=auth;this.commands=commands;this.clock=clock;this.maxAuctions=maxAuctions;this.tasks=tasks;capacity=new Semaphore(maxAuctions);
    }
    public void onSnapshot(Consumer<Auction> listener) { publisher=listener; }
    public void onFailure(Consumer<Exception> listener) { failure=listener; }
    public AuctionRepository repository() { return repository; }
    public AuctionRuntimes runtimes() { return runtimes; }
    public int liveCount() { return maxAuctions-capacity.availablePermits(); }
    public void recover() throws Exception {
        List<Auction> all=db.read(repository::live);
        if(all.size()>maxAuctions) throw new IllegalStateException("Zwiększ BIDROOM_MAX_LIVE_AUCTIONS do co najmniej "+all.size());
        for(Auction auction:all) {
            capacity.acquire();
            try(var lease=runtimes.acquire(auction.auctionId())) {
                Auction current=advance(auction.auctionId());lease.context.snapshot=current;
                if(current.status().terminal()) capacity.release(); else start(lease.context,current);
            } catch(Exception e) { capacity.release();throw e; }
        }
    }
    public Wire mutate(User actor,UUID request,String operation,Object typed) throws Exception {
        UUID id=switch(typed) {
            case Draft d -> operation.equals("CREATE_DRAFT")?UUID.randomUUID():d.auctionId();
            case Publish p -> p.auctionId();case PlaceBid b -> b.auctionId();case Cancel c -> c.auctionId();
            default -> throw new BusinessException("BAD_REQUEST","Niepoprawne polecenie.");
        };
        require(id!=null,"BAD_REQUEST","Brak identyfikatora aukcji.");
        AtomicBoolean reserved=new AtomicBoolean();boolean transferred=false;
        try(var lease=runtimes.acquire(id)) {
            require(!closed,"TEMPORARILY_UNAVAILABLE","Serwer jest chwilowo niedostępny.");
            CommandService.Outcome outcome=db.transaction(c -> {
                User current=auth.active(c,actor.id(),true);
                return commands.execute(c,current,request,operation,typed,() -> apply(c,current,request,id,operation,typed,reserved));
            });
            if(!outcome.replayed()&&Boolean.TRUE.equals(outcome.response().ok())) {
                Auction snapshot=operation.equals("PLACE_BID")
                    ?Json.read(outcome.response().payload().path("auction"),Auction.class)
                    :Json.read(outcome.response().payload(),Auction.class);
                lease.context.snapshot=snapshot;
                if(reserved.get()) {
                    try { start(lease.context,snapshot);transferred=true; }
                    catch(RuntimeException e) { failure.accept(e);throw e; }
                }
                lease.context.changed.signalAll();publisher.accept(snapshot);
            }
            return outcome.response();
        } finally { if(reserved.get()&&!transferred)capacity.release(); }
    }
    private Object apply(Connection c,User actor,UUID request,UUID id,String op,Object input,AtomicBoolean reserved) throws Exception {
        if(op.equals("CREATE_DRAFT")) {
            Draft d=(Draft)input;validate(d);Instant now=now();
            Sql.update(c,"INSERT INTO auction(id,owner_id,title,description,category,status,starting_price,min_increment,start_at,duration_seconds,created_at,updated_at) VALUES(?,?,?,?,?,'DRAFT',?,?,?,?,?,?)",
                id,actor.id(),d.title().strip(),d.description().strip(),d.category(),d.startingPrice(),d.minIncrement(),d.startAt()==null?null:BidPolicy.precise(d.startAt()),d.durationSeconds(),now,now);
            Auction result=repository.get(c,id,false);repository.event(c,result,"DRAFT_CREATED",actor.id(),now,Map.of());return result;
        }
        Auction a=repository.get(c,id,true);Instant now=now();
        if(op.equals("PLACE_BID")) {
            PlaceBid inputBid=(PlaceBid)input;Instant end=BidPolicy.accept(a,actor.id(),inputBid.amount(),now);UUID bidId=UUID.randomUUID();
            Sql.update(c,"INSERT INTO bid VALUES(?,?,?,?,?,?,?)",bidId,id,actor.id(),a.bidCount()+1,inputBid.amount(),now,request);
            Sql.update(c,"UPDATE auction SET current_price=?,leader_id=?,bid_count=bid_count+1,version=version+1,end_at=?,updated_at=? WHERE id=?",inputBid.amount(),actor.id(),end,now,id);
            Auction changed=repository.get(c,id,false);
            repository.event(c,changed,"BID_ACCEPTED",actor.id(),now,Map.of("bidId",bidId,"amount",inputBid.amount()));
            if(!end.equals(a.endAt())) repository.event(c,changed,"EXTENDED",actor.id(),now,Map.of("previousEndAt",a.endAt(),"endAt",end));
            return new BidReceipt(bidId,inputBid.amount(),now,changed.version(),end,changed);
        }
        require(actor.admin()||a.ownerId().equals(actor.id()),"FORBIDDEN","Nie możesz zmieniać tej aukcji.");
        switch(op) {
            case "UPDATE_DRAFT" -> {
                Draft d=(Draft)input;require(a.status()==Status.DRAFT,"FORBIDDEN","Warunki opublikowanej aukcji są niezmienne.");version(a,d.expectedVersion());validate(d);
                Sql.update(c,"UPDATE auction SET title=?,description=?,category=?,starting_price=?,min_increment=?,start_at=?,duration_seconds=?,version=version+1,updated_at=? WHERE id=?",
                    d.title().strip(),d.description().strip(),d.category(),d.startingPrice(),d.minIncrement(),d.startAt()==null?null:BidPolicy.precise(d.startAt()),d.durationSeconds(),now,id);
            }
            case "PUBLISH_AUCTION" -> {
                Publish p=(Publish)input;require(a.status()==Status.DRAFT,"FORBIDDEN","Można opublikować tylko szkic.");version(a,p.expectedVersion());
                require(a.photoVersion()>0,"PHOTO_REQUIRED","Dodaj zdjęcie przedmiotu przed publikacją.");
                Instant start=a.startAt()==null?now:a.startAt();require(!start.isBefore(now),"VALIDATION_ERROR","Planowany początek już minął. Zmień szkic.");
                require(capacity.tryAcquire(),"CAPACITY_REACHED","Limit trwających i zaplanowanych aukcji został osiągnięty.");reserved.set(true);
                Instant end=start.plusSeconds(a.durationSeconds());
                Sql.update(c,"UPDATE auction SET status=?,start_at=?,initial_end_at=?,end_at=?,published_at=?,version=version+1,updated_at=? WHERE id=?",start.isAfter(now)?"SCHEDULED":"ACTIVE",start,end,end,now,now,id);
            }
            case "CANCEL_AUCTION" -> {
                Cancel cancel=(Cancel)input;String reason=AuthService.text(cancel.reason(),2,500,"Powód anulowania");
                require(!a.status().terminal(),"FORBIDDEN","Aukcja już się zakończyła.");
                require(a.endAt()==null||now.isBefore(a.endAt()),"AUCTION_ENDED","Czas aukcji minął.");
                boolean beforeStart=a.status()==Status.DRAFT||(a.status()==Status.SCHEDULED&&now.isBefore(a.startAt()));
                require(actor.admin()||beforeStart,"FORBIDDEN","Aktywną aukcję może anulować administrator.");
                Sql.update(c,"UPDATE auction SET status='CANCELLED',cancel_reason=?,finished_at=?,version=version+1,updated_at=? WHERE id=?",reason,now,now,id);
            }
            default -> throw new BusinessException("BAD_REQUEST","Nieznane polecenie.");
        }
        Auction changed=repository.get(c,id,false);repository.event(c,changed,op,actor.id(),now,Map.of());return changed;
    }
    private void validate(Draft d) {
        AuthService.text(d.title(),2,120,"Tytuł");AuthService.text(d.description(),0,4000,"Opis");
        require(d.category()!=null&&Set.of("ELECTRONICS","BOOKS","ACCESSORIES","OTHER").contains(d.category()),"VALIDATION_ERROR","Nieprawidłowa kategoria.");
        require(d.startingPrice()>0&&d.startingPrice()<=BidPolicy.MAX_AMOUNT&&d.minIncrement()>0&&d.minIncrement()<=BidPolicy.MAX_AMOUNT,"VALIDATION_ERROR","Cena i przebicie: 1–1 000 000 000 000 pkt.");
        require(d.durationSeconds()>=60&&d.durationSeconds()<=604800,"VALIDATION_ERROR","Czas trwania: od minuty do 7 dni.");
    }
    private void version(Auction a,long expected) { require(a.version()==expected,"VERSION_CONFLICT","Aukcja została zmieniona. Odśwież widok."); }
    private Instant now() { return BidPolicy.precise(clock.instant()); }
    private void start(AuctionRuntimes.Context context,Auction a) {
        if(context.running)throw new IllegalStateException("Automat już działa");
        context.snapshot=a;context.running=true;
        try { context.future=executor.submit(tasks.track("AUCTION","aukcja #"+a.auctionNumber(),new AuctionLifecycleTask(a.auctionId()))); }
        catch(RuntimeException e) { context.running=false;throw e; }
    }
    private Auction advance(UUID id) throws Exception {
        return db.transaction(c -> {
            Auction a=repository.get(c,id,true);Instant now=now();String state=null;
            if((a.status()==Status.SCHEDULED||a.status()==Status.ACTIVE)&&!now.isBefore(a.endAt()))state="FINISHED";
            else if(a.status()==Status.SCHEDULED&&!now.isBefore(a.startAt()))state="ACTIVE";
            if(state==null)return a;
            Sql.update(c,"UPDATE auction SET status=?,finished_at=?,version=version+1,updated_at=? WHERE id=?",state,state.equals("FINISHED")?now:null,now,id);
            Auction changed=repository.get(c,id,false);repository.event(c,changed,state,null,now,Map.of());return changed;
        });
    }
    /** This Callable owns a virtual thread from publication until the terminal state. */
    private final class AuctionLifecycleTask implements Callable<Status> {
        private final UUID id;AuctionLifecycleTask(UUID id) { this.id=id; }
        @Override public Status call() {
            try(var lease=runtimes.acquire(id)) {
                AuctionRuntimes.Context context=lease.context;
                try {
                    while(!closed&&!context.snapshot.status().terminal()) {
                        Instant target=context.snapshot.status()==Status.SCHEDULED?context.snapshot.startAt():context.snapshot.endAt();
                        long nanos=Duration.between(now(),target).toNanos();
                        if(nanos>0)context.changed.awaitNanos(Math.min(nanos,TimeUnit.SECONDS.toNanos(1)));
                        else { context.snapshot=advance(id);publisher.accept(context.snapshot); }
                    }
                    return context.snapshot.status();
                } catch(InterruptedException e) { Thread.currentThread().interrupt();return context.snapshot.status(); }
                catch(Exception e) { failure.accept(e);return context.snapshot.status(); }
                finally { context.running=false;capacity.release(); }
            }
        }
    }
    @Override public void close() { closed=true;executor.shutdownNow(); }
}
