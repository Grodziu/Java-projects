package pl.edu.bidroom.client;

import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.*;
import pl.edu.bidroom.client.ui.MainController;
import pl.edu.bidroom.common.*;
import pl.edu.bidroom.common.Models.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Explicit JavaFX UI verification; screenshots come from the rendered scene, never a mockup. */
public final class GuiSmoke {
    private GuiSmoke() { }
    public static void main(String[] args) { Application.launch(SmokeApplication.class,args); }
    public static final class SmokeApplication extends Application {
        private ClientGateway gateway;private MainController controller;private Scene scene;private Stage stage;
        private final ExecutorService worker=Executors.newVirtualThreadPerTaskExecutor();
        private String login,password;private UUID auction;private RpcConnection owner;private Path output;
        @Override public void init() throws Exception {
            ClientConfig config=ClientConfig.load();output=Path.of(System.getProperty("bidroom.screenshots","docs/screenshots"));Files.createDirectories(output);
            login="gui_"+UUID.randomUUID().toString().substring(0,8);password=UUID.randomUUID().toString();gateway=new ClientGateway(config);
            waitUntil(gateway::online,20);
            gateway.register(login,"Alicja • pokaz",password);
            owner=new RpcConnection(config.host(),config.port(),config.tls(),e->{});owner.request("HELLO",Map.of()).checked();owner.request("REGISTER",new Registration(login+"_owner","Pracownia projektowa",password)).checked();owner.request("LOGIN",new Credentials(login+"_owner",password)).checked();
            String[] titles={"Plakat PK — kolory inżynierii","Grafika PK — projekt na ścianę"};String[] categories={"ELECTRONICS","BOOKS"};
            for(int i=0;i<2;i++) {
                Auction draft=Json.read(owner.request("CREATE_DRAFT",new Draft(null,0,titles[i],"Aukcja demonstracyjna w wirtualnych punktach. Obserwuj oferty i czas pozostały do końca.",categories[i],100,10,null,i==0?60:180)).checked(),Auction.class);
                draft=PhotoTransfer.upload((op,p)->owner.request(op,p).checked(),draft,Files.readAllBytes(Path.of("docs/reference/design/pk-pattern.png")));
                owner.request("PUBLISH_AUCTION",new Publish(draft.auctionId(),draft.version())).checked();if(i==0)auction=draft.auctionId();
            }
        }
        @Override public void start(Stage stage) throws Exception {
            this.stage=stage;FXMLLoader loader=new FXMLLoader(getClass().getResource("/ui/main.fxml"));
            double scale=Double.parseDouble(System.getProperty("glass.win.uiScale","2.0"));boolean layoutOnly=Boolean.getBoolean("bidroom.layoutOnly");
            scene=new Scene(loader.load(),layoutOnly?1366/scale-20:1180,layoutOnly?768/scale-60:740);scene.getStylesheets().add(getClass().getResource("/ui/app.css").toExternalForm());controller=loader.getController();controller.initializeGateway(gateway,stage);stage.setTitle("BidRoom • weryfikacja GUI");stage.setScene(scene);stage.show();
            worker.submit((Callable<Void>)()->{try { scenario();System.out.println("GUI_SMOKE_PASSED"); }catch(Throwable e) { e.printStackTrace();System.setProperty("bidroom.gui.failed","true"); }finally { Platform.runLater(Platform::exit); }return null;});
        }
        private void scenario() throws Exception {
            if(!checkLabel("Piotr Grodny • Marcin Gurdziel • Oliwia Jarosz")||!checkLabel("2026/2027"))throw new AssertionError("Brak autorów lub roku na ekranie logowania");
            screenshot("01-logowanie.png");
            fx(()->{((TextField)scene.lookup("#login")).setText(login);((PasswordField)scene.lookup("#password")).setText(password);((Button)scene.lookup("#loginSubmit")).fire();return null;});
            waitUntil(()->gateway.user()!=null,15);waitUntil(()->checkLabel("Aukcje na żywo"),10);waitUntil(()->nodeExists("#photo-"+auction),10);screenshot("02-katalog.png");
            waitUntil(()->nodeExists("#auction-"+auction),10);
            fx(()->{Node card=scene.lookup("#auction-"+auction);((Button)card.lookup(".button")).fire();return null;});
            waitUntil(()->nodeExists("#bidSubmit"),10);TimeUnit.MILLISECONDS.sleep(300);screenshot("03-aukcja.png");
            if(Boolean.getBoolean("bidroom.layoutOnly")) {
                var layout=fx(()->Map.of("outputScale",stage.getOutputScaleX(),"sceneWidth",scene.getWidth(),"sceneHeight",scene.getHeight(),"physicalTarget","1366x768","bidButtonPresent",scene.lookup("#bidSubmit")!=null));
                Files.write(output.resolve("layout.json"),Json.bytes(layout));return;
            }
            fx(()->{((TextField)scene.lookup("#bidAmount")).setText("150");((Button)scene.lookup("#bidSubmit")).fire();((Button)scene.lookup("#bidSubmit")).fire();return null;});
            waitUntil(()->checkLabel("Oferta przyjęta: 150"),15);TimeUnit.MILLISECONDS.sleep(700);screenshot("04-oferta-przedluzenie.png");fx(()->{((ScrollPane)scene.lookup("#scroll")).setVvalue(1);return null;});TimeUnit.MILLISECONDS.sleep(200);screenshot("04b-historia-ofert.png");fx(()->{((ScrollPane)scene.lookup("#scroll")).setVvalue(0);return null;});
            var a=Json.read(gateway.request("GET_AUCTION",Map.of("auctionId",auction)),Auction.class);if(a.bidCount()!=1)throw new AssertionError("Podwójne kliknięcie utworzyło wiele ofert");
            gateway.retry();waitUntil(()->!gateway.online(),3);waitUntil(gateway::online,20);gateway.request("GET_AUCTION",Map.of("auctionId",auction));
            waitUntil(()->{try{return Json.read(gateway.request("GET_AUCTION",Map.of("auctionId",auction)),Auction.class).status()==Status.FINISHED;}catch(Exception e){return false;}},75);
            waitUntil(()->checkLabel("Raport zapisany automatycznie"),10);screenshot("05-wynik.png");gateway.export(auction,output.resolve("przykladowy-raport.txt"));
            Platform.runLater(()->{try {var method=MainController.class.getDeclaredMethod("draftDialog",Auction.class);method.setAccessible(true);method.invoke(controller,new Object[]{null});}catch(Exception e){throw new RuntimeException(e);}});
            waitUntil(()->dialogPresent("Nowa aukcja"),5);
            fx(()->{DialogPane pane=currentDialog();((TextField)pane.lookup("#draftTitle")).setText("Aukcja utworzona przez formularz JavaFX");((TextArea)pane.lookup("#draftDescription")).setText("Weryfikacja zapisu szkicu i publikacji przez kontrolki interfejsu.");((TextField)pane.lookup("#draftDuration")).setText("1.5");ButtonType save=pane.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();((Button)pane.lookupButton(save)).fire();return null;});
            waitUntil(()->checkLabel("Aukcja utworzona przez formularz JavaFX"),10);waitUntil(()->checkLabel("Szkic"),5);
            Auction created=Json.read(gateway.request("LIST_AUCTIONS",Map.of("view","mine","query","Aukcja utworzona przez formularz JavaFX")).path("items").get(0),Auction.class);
            Auction pictured=PhotoTransfer.upload(gateway::request,created,Files.readAllBytes(Path.of("docs/reference/design/pk-pattern.png")));
            fx(()->{var method=MainController.class.getDeclaredMethod("renderDetail",Auction.class);method.setAccessible(true);method.invoke(controller,pictured);return null;});
            Platform.runLater(()->{Button publish=(Button)scene.getRoot().lookupAll(".button").stream().filter(n->n instanceof Button b&&b.getText().equals("Opublikuj")).findFirst().orElseThrow();publish.fire();});
            waitUntil(()->dialogPresent("Opublikować aukcję"),5);fx(()->{DialogPane pane=currentDialog();((Button)pane.lookupButton(ButtonType.OK)).fire();return null;});
            waitUntil(()->checkLabel("Aukcja została opublikowana"),10);TimeUnit.MILLISECONDS.sleep(500);screenshot("08-publikacja-formularz.png");
            if(System.getenv("BIDROOM_BOOTSTRAP_ADMIN_PASSWORD")!=null) {
                gateway.logout();gateway.login(System.getenv("BIDROOM_BOOTSTRAP_ADMIN_LOGIN"),System.getenv("BIDROOM_BOOTSTRAP_ADMIN_PASSWORD"));TimeUnit.MILLISECONDS.sleep(500);
                fx(()->{var method=MainController.class.getDeclaredMethod("administration");method.setAccessible(true);method.invoke(controller);return null;});TimeUnit.MILLISECONDS.sleep(400);
                waitUntil(()->checkLabel("Połączenia klientów"),10);screenshot("09-panel-serwera.png");
                fx(()->{var method=MainController.class.getDeclaredMethod("logs",int.class);method.setAccessible(true);method.invoke(controller,0);return null;});TimeUnit.MILLISECONDS.sleep(700);screenshot("06-logi.png");
            }
            fx(()->{stage.setWidth(1060);stage.setHeight(640);return null;});
            waitUntil(()->{try{return fx(()->scene.getWidth()<1060&&scene.getHeight()<640);}catch(Exception e){return false;}},5);
            TimeUnit.MILLISECONDS.sleep(300);screenshot("07-mniejsze-okno.png");
        }
        private boolean nodeExists(String selector) { try{return fx(()->scene.lookup(selector)!=null);}catch(Exception e){return false;} }
        private DialogPane currentDialog() { return Window.getWindows().stream().filter(w->w.isShowing()&&w.getScene()!=null&&w.getScene().getRoot() instanceof DialogPane).map(w->(DialogPane)w.getScene().getRoot()).findFirst().orElseThrow(); }
        private boolean dialogPresent(String header) { try{return fx(()->{try{return currentDialog().getHeaderText().contains(header);}catch(Exception e){return false;}});}catch(Exception e){return false;} }
        private boolean checkLabel(String contains) { try{return fx(()->scene.getRoot().lookupAll(".label").stream().anyMatch(n->n instanceof Label l&&l.getText()!=null&&l.getText().contains(contains)));}catch(Exception e){return false;} }
        private void screenshot(String name) throws Exception {
            BufferedImage image=fx(()->{LayoutChecks.verify(scene);WritableImage shot=scene.snapshot(null);int w=(int)shot.getWidth(),h=(int)shot.getHeight();BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++)out.setRGB(x,y,shot.getPixelReader().getArgb(x,y));return out;});
            ImageIO.write(image,"png",output.resolve(name).toFile());
        }
        private <T> T fx(Callable<T> call) throws Exception { FutureTask<T> task=new FutureTask<>(call);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS); }
        private static void waitUntil(BooleanSupplier ready,int seconds) throws Exception { long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(!ready.getAsBoolean()&&System.nanoTime()<end)TimeUnit.MILLISECONDS.sleep(100);if(!ready.getAsBoolean())throw new AssertionError("Warunek GUI nie został osiągnięty w "+seconds+" s"); }
        @Override public void stop() { controller.stop();gateway.close();owner.close();worker.shutdownNow();if(Boolean.getBoolean("bidroom.gui.failed"))System.exit(1); }
    }
}
