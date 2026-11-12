package pl.edu.bidroom.server.transport;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import pl.edu.bidroom.common.BusinessException;

public final class RateLimiter {
    private record Window(long minute,AtomicInteger count) { }
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    public void check(String address) {
        long minute=System.nanoTime()/60_000_000_000L;
        if(windows.size()>2048)windows.entrySet().removeIf(e->e.getValue().minute()!=minute);
        if(windows.size()>2048&&!windows.containsKey(address))throw new BusinessException("RATE_LIMITED","Spróbuj za minutę.");
        Window window=windows.compute(address,(key,old)->old==null||old.minute()!=minute?new Window(minute,new AtomicInteger()):old);
        if(window.count().incrementAndGet()>60)throw new BusinessException("RATE_LIMITED","Zbyt wiele prób. Spróbuj za minutę.");
    }
}
