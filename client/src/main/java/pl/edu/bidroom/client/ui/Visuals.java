package pl.edu.bidroom.client.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

final class Visuals {
    static final String[] COLORS={"#007EB5","#65A524","#EB8A18","#8A3E90","#D42B78","#009A98"};
    static Node avatar(Long id,double size) {
        String color=COLORS[Math.floorMod(id==null?0:id.intValue(),COLORS.length)];
        Circle circle=new Circle(size/2,Color.web(color));SVGPath person=new SVGPath();person.setContent("M12,3 A4,4 0 1 0 12,11 A4,4 0 1 0 12,3 M3,21 C3,11 21,11 21,21 Z");person.setFill(Color.WHITE);person.setScaleX(size/32);person.setScaleY(size/32);
        StackPane pane=new StackPane(circle,person);pane.setMinSize(size,size);pane.setPrefSize(size,size);pane.setMaxSize(size,size);pane.setAccessibleText("Awatar użytkownika "+id);return pane;
    }
    static HBox person(Long id,String name) {Label label=new Label(name);label.setWrapText(true);HBox box=new HBox(9,avatar(id,30),label);box.setAlignment(Pos.CENTER_LEFT);return box;}
    static StackPane placeholder(String category,double width,double height) {
        int i=switch(category){case "BOOKS"->1;case "ACCESSORIES"->4;case "OTHER"->2;default->0;};
        SVGPath drawing=new SVGPath();drawing.setContent(switch(category){
            case "BOOKS"->"M6 5 L21 8 L36 5 L36 30 L21 33 L6 30 Z M21 8 L21 33 M10 12 L17 14 M25 14 L32 12 M10 19 L17 21 M25 21 L32 19";
            case "ACCESSORIES"->"M9 12 L33 12 L36 34 L6 34 Z M15 12 L15 7 Q21 -1 27 7 L27 12";
            case "OTHER"->"M4 11 L21 3 L38 11 L38 30 L21 38 L4 30 Z M4 11 L21 19 L38 11 M21 19 L21 38 M12 7 L29 15";
            default->"M3 5 L39 5 L39 28 L3 28 Z M21 28 L21 35 M12 35 L30 35 M7 10 L22 10 M7 15 L16 15";});
        drawing.setFill(Color.TRANSPARENT);drawing.setStroke(Color.web(COLORS[i]));drawing.setStrokeWidth(1.8);drawing.setScaleX(1.65);drawing.setScaleY(1.65);
        StackPane symbol=new StackPane(drawing);symbol.setPrefHeight(height-45);Label label=new Label("Ilustracja kategorii • brak zdjęcia");label.getStyleClass().add("photo-note");
        VBox box=new VBox(8,symbol,label);box.setAlignment(Pos.CENTER);StackPane pane=new StackPane(box);pane.setStyle("-fx-background-color: "+COLORS[i]+"18; -fx-background-radius: 10;");pane.setMinSize(width,height);pane.setPrefSize(width,height);pane.setMaxSize(width,height);return pane;
    }
}
