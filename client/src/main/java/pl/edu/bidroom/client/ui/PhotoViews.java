package pl.edu.bidroom.client.ui;

import javafx.application.Platform;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import pl.edu.bidroom.client.ClientGateway;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.Auction;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;

final class PhotoViews implements AutoCloseable {
    private final ClientGateway gateway;
    private final ExecutorService executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(24));
    private final Map<String,byte[]> cache=Collections.synchronizedMap(new LinkedHashMap<>(24,0.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,byte[]> entry){return size()>24;}});
    PhotoViews(ClientGateway gateway){this.gateway=gateway;}
    StackPane create(Auction auction,double width,double height) {
        StackPane pane=Visuals.placeholder(auction.category(),width,height);if(auction.photoVersion()==0)return pane;
        pane.getChildren().setAll(new javafx.scene.control.Label("Pobieranie zdjęcia…"));
        try {executor.submit((Callable<Void>)()->{
            try {String key=auction.auctionId()+":"+auction.photoVersion();byte[] bytes=cache.get(key);
            if(bytes==null){bytes=PhotoTransfer.download(gateway::request,auction);if(bytes!=null)cache.put(key,bytes);}
            if(bytes!=null){Image image=new Image(new ByteArrayInputStream(bytes),width*2,height*2,true,true);Platform.runLater(()->{ImageView view=new ImageView(image);view.setFitWidth(width);view.setFitHeight(height);view.setPreserveRatio(true);view.setId("photo-"+auction.auctionId());pane.getChildren().setAll(view);});}
            }catch(Exception failure){Platform.runLater(()->pane.getChildren().setAll(new javafx.scene.control.Label("Nie pobrano zdjęcia. Otwórz aukcję ponownie.")));}
            return null;
        });}catch(RejectedExecutionException ignored) { /* The next view refresh can retry a bounded queue. */ }
        return pane;
    }
    @Override public void close(){executor.shutdownNow();cache.clear();}
}
