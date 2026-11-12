package pl.edu.bidroom.loadtest;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit local fault scenario. Its script stops only the private BidRoom PostgreSQL cluster. */
public final class RecoveryCheck {
    private RecoveryCheck() { }
    private static RpcConnection connect() throws Exception {
        RpcConnection connection=new RpcConnection("127.0.0.1",5001,false,event->{});
        try { connection.request("HELLO",Map.of()).checked();return connection; }
        catch(Exception e) { connection.close();throw e; }
    }
    public static void main(String[] args) throws Exception {
        Path signals=Path.of(args[0]),output=Path.of(args[1]);Files.createDirectories(signals);
        String name="recovery_"+UUID.randomUUID().toString().substring(0,8),password=UUID.randomUUID().toString();
        UUID request=UUID.randomUUID();Auction auction;Wire original;
        try(RpcConnection owner=connect();RpcConnection bidder=connect()) {
            owner.request("REGISTER",new Registration(name,"Próba odzyskiwania",password)).checked();owner.request("LOGIN",new Credentials(name,password)).checked();
            bidder.request("REGISTER",new Registration(name+"_bid","Licytujący po awarii",password)).checked();bidder.request("LOGIN",new Credentials(name+"_bid",password)).checked();
            Auction draft=Json.read(owner.request("CREATE_DRAFT",new Draft(null,0,"Odzyskanie po utracie PostgreSQL","Próba lokalna","OTHER",100,10,null,60)).checked(),Auction.class);
            draft=PhotoTransfer.upload((op,payload)->owner.request(op,payload).checked(),draft,Files.readAllBytes(Path.of("docs/reference/design/pk-pattern.png")));
            auction=Json.read(owner.request("PUBLISH_AUCTION",new Publish(draft.auctionId(),draft.version())).checked(),Auction.class);
            original=bidder.request(request,"PLACE_BID",new PlaceBid(auction.auctionId(),100));original.checked();
            Files.writeString(signals.resolve("ready.signal"),"ready");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35);boolean disconnected=false;
            while(System.nanoTime()<deadline) {
                try { bidder.request("GET_AUCTION",Map.of("auctionId",auction.auctionId())).checked(); }
                catch(Exception expected) { disconnected=true;break; }
                TimeUnit.MILLISECONDS.sleep(200);
            }
            if(!disconnected)throw new AssertionError("Nie zaobserwowano niedostępności bazy");
            Files.writeString(signals.resolve("disconnected.signal"),"disconnected");
        }
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);Exception last=null;
        while(System.nanoTime()<deadline) {
            try(RpcConnection resumed=connect()) {
                resumed.request("LOGIN",new Credentials(name+"_bid",password)).checked();
                Wire replay=resumed.request(request,"PLACE_BID",new PlaceBid(auction.auctionId(),100));
                if(!original.equals(replay))throw new AssertionError("Zapisany wynik nie został odtworzony");
                Auction after=Json.read(resumed.request("GET_AUCTION",Map.of("auctionId",auction.auctionId())).checked(),Auction.class);
                if(after.bidCount()!=1||!after.endAt().toString().equals(original.payload().path("endAt").asText()))throw new AssertionError("Duplikat oferty lub terminu");
                Files.write(output,Json.bytes(Map.of("timestamp",Instant.now(),"databaseDisconnectObserved",true,"supervisorRecovered",true,"replayedIdenticalResult",true,"bidCount",after.bidCount(),"endAt",after.endAt())));
                System.out.println("RECOVERY_CHECK_PASSED");return;
            }catch(AssertionError e){throw e;}catch(Exception e){last=e;TimeUnit.MILLISECONDS.sleep(500);}
        }
        throw new IllegalStateException("Brak powrotu serwera po awarii bazy",last);
    }
}
