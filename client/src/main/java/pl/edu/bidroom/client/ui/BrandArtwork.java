package pl.edu.bidroom.client.ui;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

/** Fits the complete university artwork inside its allotted space, without cropping. */
public final class BrandArtwork extends Region {
    private static final Image ART = new Image(BrandArtwork.class.getResourceAsStream("/ui/assets/pk-pattern.png"));
    private final ImageView image = new ImageView(ART);

    public BrandArtwork() {
        image.setPreserveRatio(true);
        image.setSmooth(true);
        image.setAccessibleText("Politechnika Krakowska — kolorowa grafika inżynierska");
        image.getStyleClass().add("brand-artwork-image");
        getChildren().add(image);
        setMinSize(0, 0);
        setPrefSize(240, 135);
        getStyleClass().add("brand-artwork");
    }

    @Override protected void layoutChildren() {
        double width = Math.max(0, getWidth()), height = Math.max(0, getHeight());
        image.setFitWidth(width);
        image.setFitHeight(height);
        image.relocate((width - image.getBoundsInLocal().getWidth()) / 2,
                (height - image.getBoundsInLocal().getHeight()) / 2);
    }
}
