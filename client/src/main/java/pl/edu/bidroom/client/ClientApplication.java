package pl.edu.bidroom.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Screen;
import pl.edu.bidroom.client.ui.MainController;

public final class ClientApplication extends Application {
    private ClientGateway gateway;private MainController controller;
    @Override public void init() throws Exception { gateway=new ClientGateway(ClientConfig.load()); }
    @Override public void start(Stage stage) throws Exception {
        var bounds=Screen.getPrimary().getVisualBounds();
        FXMLLoader loader=new FXMLLoader(getClass().getResource("/ui/main.fxml"));Scene scene=new Scene(loader.load(),Math.min(1180,bounds.getWidth()-40),Math.min(740,bounds.getHeight()-70));
        scene.getStylesheets().add(getClass().getResource("/ui/app.css").toExternalForm());controller=loader.getController();controller.initializeGateway(gateway,stage);
        stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/ui/assets/pk-symbol.png")));
        stage.setTitle("BidRoom • Projekt studencki PK");stage.setMinWidth(Math.min(760,bounds.getWidth()-20));stage.setMinHeight(Math.min(520,bounds.getHeight()-20));stage.setScene(scene);stage.show();
    }
    @Override public void stop() { if(controller!=null)controller.stop();if(gateway!=null)gateway.close(); }
}
