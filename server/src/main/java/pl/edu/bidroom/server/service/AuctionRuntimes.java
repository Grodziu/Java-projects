package pl.edu.bidroom.server.service;

import pl.edu.bidroom.common.Models.Auction;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** A context stays canonical while any operation or lifecycle holds a reference. */
public final class AuctionRuntimes {
    private final ConcurrentHashMap<UUID,Context> contexts=new ConcurrentHashMap<>();
    public static final class Context {
        final ReentrantLock lock=new ReentrantLock();
        final Condition changed=lock.newCondition();
        volatile boolean running;
        Auction snapshot;
        Future<?> future;
        int references;
    }
    public final class Lease implements AutoCloseable {
        private final UUID id;public final Context context;
        private Lease(UUID id,Context context) { this.id=id;this.context=context;context.lock.lock(); }
        @Override public void close() {
            context.lock.unlock();
            contexts.compute(id,(key,current)-> { if(current!=context) throw new IllegalStateException("Niespójny kontekst");current.references--;return current.references==0&&!current.running?null:current; });
        }
    }
    public Lease acquire(UUID id) {
        Context c=contexts.compute(id,(key,current)-> { if(current==null)current=new Context();current.references++;return current; });
        return new Lease(id,c);
    }
    public int size() { return contexts.size(); }
    public void wakeAll() {
        contexts.values().forEach(c -> { c.lock.lock();try { c.changed.signalAll(); }finally { c.lock.unlock(); } });
    }
}
