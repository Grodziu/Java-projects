package pl.edu.bidroom.server.service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Actual running application tasks; JVM MXBean counts platform threads only. */
public final class TaskRegistry {
    public record Task(long threadId,String role,String name,boolean virtual,Instant startedAt) { }
    private final ConcurrentHashMap<Long,Task> running=new ConcurrentHashMap<>();
    private volatile BiConsumer<String,String> log=(type,message)->{};
    public void onEvent(BiConsumer<String,String> listener) { log=listener; }
    public <T> Callable<T> track(String role,String name,Callable<T> work) {
        return ()->{
            Thread thread=Thread.currentThread();Task task=new Task(thread.threadId(),role,name,thread.isVirtual(),Instant.now());
            running.put(task.threadId(),task);thread.setName("bidroom-"+role+"-"+name);
            log.accept("TASK_STARTED","Wątek "+task.threadId()+": "+role+" / "+name);
            try { return work.call(); }
            finally { running.remove(task.threadId());log.accept("TASK_FINISHED","Wątek "+task.threadId()+": "+role+" / "+name); }
        };
    }
    public Map<String,Object> snapshot() {
        var tasks=running.values().stream().sorted(Comparator.comparingLong(Task::threadId)).toList();
        Map<String,Long> roles=new TreeMap<>();tasks.forEach(task->roles.merge(task.role(),1L,Long::sum));
        return Map.of("total",tasks.size(),"virtual",tasks.stream().filter(Task::virtual).count(),"roles",roles,"items",tasks.stream().limit(100).toList());
    }
}
