package pl.edu.bidroom.common;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded spool. Only application-selected messages are recorded, never request payloads. */
public final class DurableLog {
    public record Entry(UUID eventId,UUID installationId,Instant clientOccurredAt,String level,String eventType,UUID auctionId,UUID commandRequestId,String message) { }
    private static final int MAX_ENTRIES=5000;
    private static final long MAX_BYTES=10*1024*1024;
    private final Path file;private final UUID installation;
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
    private final ReentrantLock lock=new ReentrantLock();private long dropped;
    public DurableLog(Path directory) throws IOException {
        Files.createDirectories(directory);file=directory.resolve("diagnostics.jsonl");Path identity=directory.resolve("installation-id.txt");
        if(Files.exists(identity)) installation=UUID.fromString(Files.readString(identity).strip());
        else { installation=UUID.randomUUID();Files.writeString(identity,installation.toString(),StandardOpenOption.CREATE_NEW); }
        if(Files.exists(file)) {
            if(Files.size(file)>MAX_BYTES) { Files.move(file,directory.resolve("diagnostics-overflow.jsonl"),StandardCopyOption.REPLACE_EXISTING);dropped++; }
            else try(BufferedReader reader=Files.newBufferedReader(file)) {
                String line;while((line=reader.readLine())!=null) {
                    try { Entry entry=Json.MAPPER.readValue(line,Entry.class);entries.put(entry.eventId(),entry); }
                    catch(Exception e) { dropped++; }
                    if(entries.size()>MAX_ENTRIES) { entries.remove(entries.keySet().iterator().next());dropped++; }
                }
            }
        }
    }
    public UUID installationId() { return installation; }
    public void append(String level,String type,String message,UUID auction,UUID command) {
        lock.lock();try {
            if(entries.size()>=MAX_ENTRIES) { entries.remove(entries.keySet().iterator().next());dropped++; }
            Entry entry=new Entry(UUID.randomUUID(),installation,Instant.now(),level,type,auction,command,message);
            entries.put(entry.eventId(),entry);persist();
        } finally { lock.unlock(); }
    }
    public List<Entry> batch() {
        lock.lock();try { return entries.values().stream().limit(50).toList(); }finally { lock.unlock(); }
    }
    public void acknowledge(Collection<UUID> ids) {
        lock.lock();try { ids.forEach(entries::remove);persist(); }finally { lock.unlock(); }
    }
    public long dropped() { return dropped; }
    private void persist() {
        Path temp=file.resolveSibling("diagnostics.tmp");
        try {
            try(BufferedWriter writer=Files.newBufferedWriter(temp)) { for(Entry entry:entries.values()) { writer.write(Json.node(entry).toString());writer.newLine(); } }
            if(Files.size(temp)>MAX_BYTES) { entries.remove(entries.keySet().iterator().next());dropped++;persist();return; }
            try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException e) { dropped++;System.err.println("Nie zapisano bufora diagnostyki; liczba utraconych zapisów: "+dropped); }
    }
}
