package pl.edu.bidroom.server.transport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import pl.edu.bidroom.common.Wire;
import java.net.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SlowReceiverTest {
    @Test @Timeout(10) void fullOutboundBudgetDisconnectsOnlySlowReceiverAndReleasesMemory() throws Exception {
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress());Socket peer=new Socket();ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
            peer.setReceiveBufferSize(1024);peer.connect(listener.getLocalSocketAddress());
            try(Socket accepted=listener.accept()) {
                accepted.setSendBufferSize(1024);Semaphore budget=new Semaphore(2*1024*1024);
                CountDownLatch finished=new CountDownLatch(1);
                try(ClientSessionHandler handler=new ClientSessionHandler(accepted,null,executor,budget,c->finished.countDown(),e->fail(e))) {
                    Future<Void> task=executor.submit(handler);
                    Wire large=Wire.event("TEST",Instant.now(),Map.of("text","a".repeat(15000)));
                    for(int i=0;i<300&&!accepted.isClosed();i++)handler.send(large);
                    assertTrue(finished.await(3,TimeUnit.SECONDS),"Pełna kolejka powinna zamknąć wolnego odbiorcę");task.get(2,TimeUnit.SECONDS);
                    long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                    while(budget.availablePermits()!=2*1024*1024&&System.nanoTime()<until)TimeUnit.MILLISECONDS.sleep(10);
                    assertEquals(2*1024*1024,budget.availablePermits());
                }
            }
        }
    }
}
