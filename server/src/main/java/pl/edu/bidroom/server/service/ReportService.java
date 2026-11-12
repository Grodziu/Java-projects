package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import pl.edu.bidroom.server.persistence.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static pl.edu.bidroom.common.BusinessException.require;

/** Terminal auctions are the durable work queue. Downloads never delete original reports. */
public final class ReportService implements AutoCloseable {
    private record Handle(Long user,Path file,Instant expires,long size,String sha) { }
    private record Report(String filename,long bytes,String sha256,Instant generatedAt) { }
    private final Database db;private final AuctionRepository repository;private final Path directory;private final Clock clock;
    private final ConcurrentHashMap<UUID,Handle> handles=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long,Integer> counts=new ConcurrentHashMap<>();private final Semaphore handleSlots=new Semaphore(64);
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final ReentrantLock generation=new ReentrantLock();private volatile boolean closed;private boolean indexDirty=true;private final LogService logs;
    private final UUID instance=UUID.randomUUID();
    public ReportService(Database db,AuctionRepository repository,Path root,Clock clock,LogService logs,TaskRegistry tasks) throws IOException {
        this.db=db;this.repository=repository;this.clock=clock;this.logs=logs;directory=root.resolve("reports");Files.createDirectories(directory);
        try(var files=Files.newDirectoryStream(directory,"*.part")) { for(Path p:files)Files.deleteIfExists(p); }
        executor.submit(tasks.track("REPORTS","automatyczne raporty",this::generatePending));
    }
    private Void generatePending() {
        boolean checked=false,warned=false;
        while(!closed) {
            try {
                if(!checked) {
                    var known=db.read(c->Sql.list(c,"SELECT auction_id,filename,sha256 FROM auction_report",r->new String[]{r.getString(1),r.getString(2),r.getString(3)}));
                    for(var row:known) {
                        if(closed)return null;Path file=directory.resolve(row[1]);
                        if(!Files.isRegularFile(file)||!checksum(file).equals(row[2]))ensure(UUID.fromString(row[0]),true);
                    }
                    checked=true;
                }
                var pending=db.read(c->Sql.list(c,"SELECT a.id FROM auction a LEFT JOIN auction_report r ON r.auction_id=a.id WHERE a.status IN ('FINISHED','CANCELLED') AND r.auction_id IS NULL ORDER BY a.auction_number LIMIT 16",r->r.getObject(1,UUID.class)));
                for(UUID id:pending) { if(closed)break;ensure(id,false); }
                generation.lockInterruptibly();try { if(indexDirty){updateIndex();indexDirty=false;} }finally {generation.unlock();}
                warned=false;TimeUnit.SECONDS.sleep(1);
            } catch(InterruptedException e) { Thread.currentThread().interrupt();break; }
            catch(Exception e) {
                if(!warned)logs.record("REPORT_RETRY","Nie zapisano raportu; automat ponowi próbę. Sprawdź miejsce na dysku i bazę.");warned=true;
                try { TimeUnit.SECONDS.sleep(3); }catch(InterruptedException interrupted) { Thread.currentThread().interrupt();break; }
            }
        }
        return null;
    }
    private Report stored(Connection c,UUID id) throws SQLException {
        return Sql.one(c,"SELECT * FROM auction_report WHERE auction_id=?",r->new Report(r.getString("filename"),r.getLong("bytes"),r.getString("sha256"),Sql.time(r,"generated_at")),id);
    }
    private Report ensure(UUID id,boolean repair) throws Exception {
        generation.lockInterruptibly();
        try {
            Report previous=db.read(c->stored(c,id));
            if(!repair&&previous!=null&&Files.isRegularFile(directory.resolve(previous.filename())))return previous;
            Auction auction=db.read(c->repository.get(c,id,false));require(auction.status().terminal(),"FORBIDDEN","Raport powstaje po zakończeniu aukcji.");
            String slug=Normalizer.normalize(auction.title().replace('ł','l').replace('Ł','L'),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$","");
            if(slug.length()>60)slug=slug.substring(0,60);if(slug.isBlank())slug="wynik";
            String filename="aukcja-"+auction.auctionNumber()+"-"+slug+".txt";
            Path target=directory.resolve(filename),temporary=directory.resolve(filename+".part");Instant generated=clock.instant();
            try {
                db.transaction(c->{
                    c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);
                    try(var settings=Sql.prepare(c,"SET LOCAL statement_timeout='15s'")) { settings.execute(); }
                    writeReport(c,repository.get(c,id,false),temporary,generated);return null;
                });
                try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE)) { channel.force(true); }
                Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                Report report=new Report(filename,Files.size(target),checksum(target),generated);
                db.transaction(c->{
                    Sql.update(c,"INSERT INTO auction_report VALUES(?,?,?,?,?) ON CONFLICT(auction_id) DO UPDATE SET filename=excluded.filename,sha256=excluded.sha256,bytes=excluded.bytes,generated_at=excluded.generated_at",id,filename,report.sha256(),report.bytes(),generated);
                    Sql.update(c,"INSERT INTO diagnostic_log(id,origin,origin_instance_id,event_id,received_at,level,event_type,auction_id,message) VALUES(?,'SERVER',?,?,?,'INFO','REPORT_GENERATED',?,?)",UUID.randomUUID(),instance,UUID.randomUUID(),generated,id,"Automatycznie zapisano raport aukcji #"+auction.auctionNumber()+": "+filename);
                    return null;
                });
                indexDirty=true;return report;
            } finally { Files.deleteIfExists(temporary); }
        } finally { generation.unlock(); }
    }
    private void writeReport(Connection c,Auction a,Path file,Instant generated) throws Exception {
        try(BufferedWriter w=Files.newBufferedWriter(file)) {
            w.write("BIDROOM — WYNIK AUKCJI #"+a.auctionNumber()+"\nTytuł: "+a.title()+"\nIdentyfikator techniczny: "+a.auctionId()+"\nWystawiający: "+a.ownerName()+" (#"+a.ownerId()+")\nStan: "+a.status()+"\nPoczątek (UTC): "+a.startAt()+"\nPierwotny koniec: "+a.initialEndAt()+"\nKońcowy termin: "+a.endAt()+"\nWygenerowano automatycznie (UTC): "+generated+"\n\nOFERTY\n");
            try(PreparedStatement s=Sql.prepare(c,"SELECT b.*,u.display_name FROM bid b JOIN app_user u ON u.id=b.bidder_id WHERE auction_id=? ORDER BY sequence_no",a.auctionId())) {
                s.setFetchSize(100);try(ResultSet r=s.executeQuery()) { while(r.next())w.write(r.getLong("sequence_no")+" | "+r.getString("display_name")+" (#"+r.getLong("bidder_id")+") | "+r.getLong("amount")+" pkt | "+Sql.time(r,"accepted_at")+"\n"); }
            }
            w.write("\nPRZEDŁUŻENIA\n");
            try(PreparedStatement s=Sql.prepare(c,"SELECT occurred_at,details::text FROM auction_event WHERE auction_id=? AND event_type='EXTENDED' ORDER BY auction_version",a.auctionId())) {
                s.setFetchSize(100);try(ResultSet r=s.executeQuery()) { while(r.next())w.write(r.getTimestamp(1).toInstant()+" | "+r.getString(2)+"\n"); }
            }
            w.write("\nWYNIK\n");
            if(a.status()==Status.CANCELLED)w.write("Anulowano: "+a.cancelReason()+"\nBrak zwycięzcy.\n");
            else if(a.leaderId()==null)w.write("Brak ofert i zwycięzcy.\n");
            else w.write("Zwycięzca: "+a.leaderName()+" (#"+a.leaderId()+")\nCena: "+a.currentPrice()+" pkt\n");
        }
    }
    private void updateIndex() throws Exception {
        Path temporary=directory.resolve("raport-ogolny.txt.part");
        db.transaction(c->{
            try(BufferedWriter w=Files.newBufferedWriter(temporary);PreparedStatement s=Sql.prepare(c,"SELECT a.auction_number,a.title,r.filename,r.generated_at FROM auction_report r JOIN auction a ON a.id=r.auction_id ORDER BY a.auction_number")) {
                s.setFetchSize(100);w.write("BIDROOM — REJESTR AUTOMATYCZNYCH RAPORTÓW\nCzasy w UTC. Każda aukcja ma osobny plik z ofertami i wynikiem.\n\n");
                try(ResultSet r=s.executeQuery()) { while(r.next())w.write("#"+r.getLong(1)+" | "+r.getString(2)+" | "+r.getString(3)+" | "+r.getTimestamp(4).toInstant()+"\n"); }
            }return null;
        });
        Files.move(temporary,directory.resolve("raport-ogolny.txt"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    public Object status(User user,UUID id) throws Exception {
        return db.read(c->{Auction a=repository.get(c,id,false);AuctionRepository.visible(a,user);Report report=stored(c,id);return report==null?Map.of("ready",false):Map.of("ready",true,"filename",report.filename(),"generatedAt",report.generatedAt());});
    }
    public Object start(User user,UUID auction) throws Exception {
        db.read(c->{Auction a=repository.get(c,auction,false);AuctionRepository.visible(a,user);require(a.status().terminal(),"FORBIDDEN","Raport jest dostępny po zakończeniu aukcji.");return null;});
        Report report=ensure(auction,false);sweep();require(handleSlots.tryAcquire(),"CAPACITY_REACHED","Limit otwartych pobrań.");
        try { counts.compute(user.id(),(key,count)-> { require(count==null||count<2,"CAPACITY_REACHED","Zakończ poprzednie pobrania.");return count==null?1:count+1; }); }
        catch(RuntimeException e) { handleSlots.release();throw e; }
        UUID id=UUID.randomUUID();handles.put(id,new Handle(user.id(),directory.resolve(report.filename()),clock.instant().plusSeconds(300),report.bytes(),report.sha256()));
        return Map.of("reportId",id,"bytes",report.bytes(),"sha256",report.sha256(),"filename",report.filename());
    }
    public Object chunk(User user,UUID id,long offset) throws Exception {
        Handle h=handles.get(id);require(h!=null&&h.user().equals(user.id())&&clock.instant().isBefore(h.expires()),"REPORT_EXPIRED","Pobranie wygasło lub jest niedostępne.");
        require(offset>=0&&offset<=h.size(),"BAD_REQUEST","Nieprawidłowa pozycja pobrania.");byte[] bytes=new byte[(int)Math.min(16384,h.size()-offset)];
        try(RandomAccessFile f=new RandomAccessFile(h.file().toFile(),"r")) { f.seek(offset);f.readFully(bytes); }
        long next=offset+bytes.length;boolean end=next==h.size();if(end)remove(id,h);
        return Map.of("base64",Base64.getEncoder().encodeToString(bytes),"nextOffset",next,"eof",end);
    }
    public void clear(Long user) { handles.forEach((id,h)-> { if(h.user().equals(user))remove(id,h); }); }
    public void sweep() { handles.forEach((id,h)-> { if(!clock.instant().isBefore(h.expires()))remove(id,h); }); }
    private void remove(UUID id,Handle h) { if(handles.remove(id,h)) { counts.computeIfPresent(h.user(),(key,count)->count==1?null:count-1);handleSlots.release(); } }
    public static String checksum(Path file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)) { byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)digest.update(bytes,0,n); }return HexFormat.of().formatHex(digest.digest());
    }
    @Override public void close() {
        closed=true;executor.shutdown();try { if(!executor.awaitTermination(5,TimeUnit.SECONDS))executor.shutdownNow(); }catch(InterruptedException e) { executor.shutdownNow();Thread.currentThread().interrupt(); }handles.forEach(this::remove);
    }
}
