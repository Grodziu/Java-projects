package pl.edu.bidroom.loadtest;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.*;
import java.nio.file.*;

/** Explicit small workload for a server started with bidroom_test. Never invoked by the Maven lifecycle. */
public final class LoadTest {
    private LoadTest() { }
    public static void main(String[] args) throws Exception {
        if(args.length<2||!args[0].equals("--confirm-local-test"))throw new IllegalArgumentException("Uruchom przez scripts/load-test.ps1 na własnym serwerze testowym.");
        int port=Integer.parseInt(args[1]);int clients=args.length>2?Integer.parseInt(args[2]):10;int seconds=args.length>3?Integer.parseInt(args[3]):30;
        if(clients<1||clients>10||seconds<1||seconds>30)throw new IllegalArgumentException("Mały profil: do 10 klientów i 30 s.");
        String prefix="lt_"+UUID.randomUUID().toString().substring(0,8),password=UUID.randomUUID().toString();
        List<RpcConnection> connections=new ArrayList<>();List<UUID> auctions=new ArrayList<>();
        try {
            RpcConnection owner=account(port,prefix+"_owner",password);connections.add(owner);
            for(int i=0;i<3;i++) {
                Auction draft=Json.read(owner.request("CREATE_DRAFT",new Draft(null,0,"Pomiar "+prefix+" #"+(i+1),"Syntetyczna aukcja pomiarowa.","OTHER",1,1,null,60)).checked(),Auction.class);
            draft=PhotoTransfer.upload((op,payload)->owner.request(op,payload).checked(),draft,Files.readAllBytes(Path.of("docs/reference/design/pk-pattern.png")));
                owner.request("PUBLISH_AUCTION",new Publish(draft.auctionId(),draft.version())).checked();auctions.add(draft.auctionId());
            }
            for(int i=0;i<clients;i++)connections.add(account(port,prefix+"_"+i,password));
            Queue<Long> timings=new ConcurrentLinkedQueue<>();AtomicInteger accepted=new AtomicInteger(),rejected=new AtomicInteger(),errors=new AtomicInteger();CountDownLatch ready=new CountDownLatch(clients),start=new CountDownLatch(1);
            var os=(com.sun.management.OperatingSystemMXBean)java.lang.management.ManagementFactory.getOperatingSystemMXBean();long cpuBefore=os.getProcessCpuTime(),wallBefore=System.nanoTime();
            try(ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Void>> futures=new ArrayList<>();
                for(int i=0;i<clients;i++) { RpcConnection client=connections.get(i+1);final int index=i;futures.add(executor.submit(()->{
                    ready.countDown();start.await();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
                    while(System.nanoTime()<deadline) {
                        long begin=System.nanoTime();UUID auction=auctions.get(index%3);
                        try {
                            Auction current=Json.read(client.request("GET_AUCTION",Map.of("auctionId",auction)).checked(),Auction.class);long amount=current.currentPrice()==null?current.startingPrice():current.currentPrice()+current.minIncrement();
                            Wire result=client.request("PLACE_BID",new PlaceBid(auction,amount));timings.add(System.nanoTime()-begin);
                            if(Boolean.TRUE.equals(result.ok()))accepted.incrementAndGet();else if(Set.of("BID_TOO_LOW","AUCTION_ENDED","AUCTION_NOT_ACTIVE").contains(result.error().code()))rejected.incrementAndGet();else errors.incrementAndGet();
                        }catch(Exception e) { errors.incrementAndGet(); }
                        long remaining=TimeUnit.SECONDS.toNanos(1)-(System.nanoTime()-begin);if(remaining>0)TimeUnit.NANOSECONDS.sleep(remaining);
                    }
                    return null;
                })); }
                if(!ready.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Nie uruchomiono klientów");start.countDown();for(var f:futures)f.get(seconds+15,TimeUnit.SECONDS);
            }
            List<Long> sorted=timings.stream().sorted().toList();double elapsed=(System.nanoTime()-wallBefore)/1e9;double cpu=(os.getProcessCpuTime()-cpuBefore)/(elapsed*1e9)*100;
            var result=Json.object();result.put("timestamp",Instant.now().toString());result.put("clients",clients);result.put("auctions",3);result.put("seconds",seconds);result.put("accepted",accepted.get());result.put("businessRejected",rejected.get());result.put("infrastructureErrors",errors.get());result.put("medianMs",percentile(sorted,.5));result.put("p95Ms",percentile(sorted,.95));result.put("loadDriverCpuPercentOneCore",cpu);result.put("loadDriverHeapUsedBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            Path output=Path.of(args.length>4?args[4]:"loadtest-result.json");Files.write(output,Json.bytes(result));System.out.println(result.toPrettyString());
            if(errors.get()>0)throw new IllegalStateException("Błędy infrastruktury podczas pomiaru");
        }finally { connections.forEach(RpcConnection::close); }
    }
    private static double percentile(List<Long> sorted,double q) { return sorted.isEmpty()?0:sorted.get(Math.min(sorted.size()-1,(int)Math.ceil(sorted.size()*q)-1))/1e6; }
    private static RpcConnection account(int port,String login,String password) throws Exception {
        RpcConnection c=new RpcConnection("127.0.0.1",port,false,e->{});
        try { c.request("HELLO",Map.of("installationId",UUID.randomUUID())).checked();c.request("REGISTER",new Registration(login,login,password)).checked();c.request("LOGIN",new Credentials(login,password)).checked();return c; }
        catch(Exception e) { c.close();throw e; }
    }
}
