package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.DurableLog.Entry;
import pl.edu.bidroom.server.persistence.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class LogService implements AutoCloseable {
    private final Database db;private final DurableLog spool;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final ArrayBlockingQueue<String[]> queue=new ArrayBlockingQueue<>(256);private volatile boolean closed;
    public LogService(Database db,Path path,TaskRegistry tasks) throws Exception {
        this.db=db;spool=new DurableLog(path.resolve("diagnostics"));executor.submit(tasks.track("LOGS","zapis logów",this::drain));
    }
    public void record(String type,String message) { if(!queue.offer(new String[]{type,message}))System.err.println("Przepełnienie kolejki diagnostycznej"); }
    public List<UUID> clientBatch(Long user,List<Entry> entries) throws Exception {
        BusinessException.require(entries.size()<=50,"BAD_REQUEST","Paczka może mieć do 50 wpisów.");
        for(Entry e:entries) {
            BusinessException.require(e.eventId()!=null&&e.installationId()!=null&&e.clientOccurredAt()!=null,"BAD_REQUEST","Niepełny wpis logu.");
            AuthService.text(e.message(),0,1000,"Wiadomość");AuthService.text(e.eventType(),1,60,"Typ zdarzenia");
            BusinessException.require(Set.of("INFO","WARN","ERROR").contains(e.level()),"BAD_REQUEST","Nieprawidłowy poziom logu.");
        }
        return save("CLIENT",user,entries);
    }
    private List<UUID> save(String origin,Long user,List<Entry> batch) throws Exception {
        return db.transaction(c -> {
            for(Entry e:batch) Sql.update(c,"INSERT INTO diagnostic_log VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",UUID.randomUUID(),origin,e.installationId(),e.eventId(),user,e.clientOccurredAt(),Instant.now(),e.level(),e.eventType(),e.auctionId(),e.commandRequestId(),e.message());
            return batch.stream().map(Entry::eventId).toList();
        });
    }
    private Void drain() {
        try {
            while(!closed||!queue.isEmpty()) {
                String[] event=queue.poll(1,TimeUnit.SECONDS);
                if(event!=null)spool.append("INFO",event[0],event[1],null,null);
                flush();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        finally { String[] e;while((e=queue.poll())!=null)spool.append("INFO",e[0],e[1],null,null);flush(); }
        return null;
    }
    private void flush() { var batch=spool.batch();if(batch.isEmpty())return;try { spool.acknowledge(save("SERVER",null,batch)); }catch(Exception ignored) { /* Retained on disk until DB recovers. */ } }
    public Object query(String origin,String user,String auction,String level,Instant from,Instant to,int offset,int limit) throws Exception {
        return db.read(c -> Sql.list(c,"SELECT * FROM diagnostic_log WHERE (?='' OR origin=?) AND (?='' OR reported_by_user_id::text=?) AND (?='' OR auction_id::text=?) AND (?='' OR level=?) AND received_at>=? AND received_at<=? ORDER BY received_at DESC,id LIMIT ? OFFSET ?",r -> {
            var o=Json.object();o.put("origin",r.getString("origin"));o.put("level",r.getString("level"));o.put("eventType",r.getString("event_type"));o.put("message",r.getString("message"));
            o.put("reportedByUserId",r.getString("reported_by_user_id"));o.put("auctionId",r.getString("auction_id"));o.put("receivedAt",Sql.time(r,"received_at").toString());
            var time=Sql.time(r,"client_occurred_at");o.put("clientOccurredAt",time==null?null:time.toString());return o;
        },origin,origin,user,user,auction,auction,level,level,from,to,limit,offset));
    }
    @Override public void close() {
        closed=true;executor.shutdown();try { if(!executor.awaitTermination(4,TimeUnit.SECONDS))executor.shutdownNow(); }catch(InterruptedException e) { executor.shutdownNow();Thread.currentThread().interrupt(); }
    }
}
