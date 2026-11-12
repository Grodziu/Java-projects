package pl.edu.bidroom.loadtest;

import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import java.nio.file.*;
import java.util.*;

public final class DemoSetup {
    private DemoSetup() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Podaj port lokalnego serwera i prywatny plik scenariusza.");
        int port=Integer.parseInt(args[0]);String prefix="demo_"+UUID.randomUUID().toString().substring(0,8);List<Map<String,String>> accounts=new ArrayList<>();List<UUID> auctions=new ArrayList<>();
        String[] names={"Alicja • wystawiająca","Bartek • licytujący","Celina • licytująca"};
        for(int i=0;i<3;i++) {
            String login=prefix+"_"+i,password=UUID.randomUUID().toString();
            try(RpcConnection c=new RpcConnection("127.0.0.1",port,false,e->{})) {
                c.request("HELLO",Map.of()).checked();c.request("REGISTER",new Registration(login,names[i],password)).checked();c.request("LOGIN",new Credentials(login,password)).checked();
                if(i<2) {
                    int auction=i;
                    Auction d=Json.read(c.request("CREATE_DRAFT",new Draft(null,0,auction==0?"Zestaw Arduino — pokaz współbieżności":"Biblioteka algorytmów — druga aukcja","Wirtualne punkty. Aukcja przeznaczona do lokalnego pokazu trzech klientów.",auction==0?"ELECTRONICS":"BOOKS",100,10,null,auction==0?60:180)).checked(),Auction.class);
            d=PhotoTransfer.upload((op,payload)->c.request(op,payload).checked(),d,Files.readAllBytes(Path.of("docs/reference/design/pk-pattern.png")));
                    c.request("PUBLISH_AUCTION",new Publish(d.auctionId(),d.version())).checked();auctions.add(d.auctionId());
                }
            }
            accounts.add(Map.of("login",login,"password",password,"displayName",names[i]));
        }
        Files.write(Path.of(args[1]),Json.bytes(Map.of("users",accounts,"auctions",auctions,"port",port)),StandardOpenOption.CREATE_NEW);
        System.out.println("Przygotowano trzy syntetyczne konta i dwie aukcje.");
    }
}
