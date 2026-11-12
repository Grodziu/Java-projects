package pl.edu.bidroom.client;

import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.*;
import pl.edu.bidroom.client.ui.MainController;
import pl.edu.bidroom.common.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public final class DemoClient {
    private DemoClient() { }
    public static void main(String[] args) { Application.launch(DemoApplication.class,args); }
    public static final class DemoApplication extends Application {
        private ClientGateway gateway;private JsonNode setup;private int index;private Path directory,output;private MainController controller;private Scene scene;
        private final ExecutorService worker=Executors.newVirtualThreadPerTaskExecutor();
        @Override public void init() throws Exception {
            directory=Path.of(System.getProperty("bidroom.demoDir"));output=Path.of(System.getProperty("bidroom.demoOutput"));index=Integer.parseInt(System.getProperty("bidroom.demoIndex"));setup=Json.MAPPER.readTree(Files.readAllBytes(directory.resolve("scenario.json")));
            System.setProperty("bidroom.dataDir",directory.resolve("client-"+index).toString());System.setProperty("bidroom.port",setup.path("port").asText());gateway=new ClientGateway(ClientConfig.load());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(!gateway.online()&&System.nanoTime()<deadline)TimeUnit.MILLISECONDS.sleep(100);
            for(int attempt=0;;attempt++) {
                try { gateway.login(setup.path("users").get(index).path("login").asText(),setup.path("users").get(index).path("password").asText());break; }
                catch(BusinessException e) { if(!e.code().equals("RATE_LIMITED")||attempt>=5)throw e;TimeUnit.MILLISECONDS.sleep(500+index*200L); }
            }
        }
        @Override public void start(Stage stage) throws Exception {
            FXMLLoader loader=new FXMLLoader(getClass().getResource("/ui/main.fxml"));scene=new Scene(loader.load(),1120,700);scene.getStylesheets().add(getClass().getResource("/ui/app.css").toExternalForm());controller=loader.getController();controller.initializeGateway(gateway,stage);
            stage.setTitle("BidRoom • pokaz • klient "+(index+1));stage.setScene(scene);stage.setX(20+index*90);stage.setY(20+index*40);stage.show();
            worker.submit((Callable<Void>)()->{
                try {
                    TimeUnit.MILLISECONDS.sleep(800);UUID auction=UUID.fromString(setup.path("auctions").get(index==0?1:0).asText());
                    fx(()->{var method=MainController.class.getDeclaredMethod("openAuction",UUID.class);method.setAccessible(true);method.invoke(controller,auction);return null;});
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!fx(()->scene.lookup("#bidSubmit")!=null)&&System.nanoTime()<deadline)TimeUnit.MILLISECONDS.sleep(100);
                    Files.writeString(directory.resolve("ready-"+index),"ready");
                    while(!Files.exists(directory.resolve("go.signal")))TimeUnit.MILLISECONDS.sleep(50);
                    if(index>0)fx(()->{((TextField)scene.lookup("#bidAmount")).setText("150");((Button)scene.lookup("#bidSubmit")).fire();return null;});
                    TimeUnit.SECONDS.sleep(2);BufferedImage image=fx(()->{WritableImage shot=scene.snapshot(null);BufferedImage out=new BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),BufferedImage.TYPE_INT_ARGB);for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++)out.setRGB(x,y,shot.getPixelReader().getArgb(x,y));return out;});
                    Files.createDirectories(output);ImageIO.write(image,"png",output.resolve("demo-client-"+(index+1)+".png").toFile());
                    Files.write(output.resolve("demo-client-"+(index+1)+".json"),Json.bytes(Map.of("pid",ProcessHandle.current().pid(),"auction",auction,"outputScale",stage.getOutputScaleX(),"user",gateway.user().displayName(),"snapshot",gateway.request("GET_AUCTION",Map.of("auctionId",auction)))));
                    System.out.println("DEMO_CAPTURED client="+(index+1));TimeUnit.SECONDS.sleep(10);
                }catch(Exception e){e.printStackTrace();}finally{Platform.runLater(Platform::exit);}return null;
            });
        }
        private <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(work);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS);}
        @Override public void stop(){controller.stop();gateway.close();worker.shutdownNow();}
    }
}
