package pl.edu.bidroom.client;

import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import java.util.List;

final class LayoutChecks {
    private LayoutChecks() { }
    static void verify(Scene scene) {
        scene.getRoot().applyCss();scene.getRoot().layout();
        for (Node node : scene.getRoot().lookupAll(".brand-artwork-image")) {
            ImageView image=(ImageView)node;Region parent=(Region)image.getParent();
            var bounds=image.getBoundsInParent();
            require(image.getViewport()==null,"Grafika PK ma ustawiony obszar wycinania");
            require(bounds.getMinX()>=-1&&bounds.getMinY()>=-1&&bounds.getMaxX()<=parent.getWidth()+1&&bounds.getMaxY()<=parent.getHeight()+1,"Grafika PK wykracza poza panel");
            require(Math.abs(bounds.getWidth()/bounds.getHeight()-image.getImage().getWidth()/image.getImage().getHeight())<0.01,"Zmieniono proporcje grafiki PK");
        }
        ScrollPane content=(ScrollPane)scene.lookup("#scroll");
        require(((Region)content.getContent()).getWidth()<=content.getViewportBounds().getWidth()+1,"Zawartość szersza niż widoczny obszar");
        List<Node> nav=scene.getRoot().lookupAll(".nav").stream().filter(Node::isManaged).toList();
        if(scene.lookup("#sidebarPane").isManaged()&&!nav.isEmpty()) {
            double width=nav.getFirst().getLayoutBounds().getWidth();
            for(Node node:nav)require(Math.abs(node.getLayoutBounds().getWidth()-width)<1,"Różne szerokości przycisków menu");
        }
        Node retry=scene.lookup("#retryButton"),status=scene.lookup("#connectionLabel");
        var buttonBounds=retry.localToScene(retry.getBoundsInLocal());var statusBounds=status.localToScene(status.getBoundsInLocal());
        require(Math.abs(buttonBounds.getCenterY()-statusBounds.getCenterY())<1,"Komunikat połączenia i Ponów nie są wyrównane");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
