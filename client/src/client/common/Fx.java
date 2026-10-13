package client.common;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Function;

/** Tiny JavaFX building blocks shared by the screens (moved out of the exercise-2 panes). */
public final class Fx {

    private Fx() {
    }

    public static <T> TableColumn<T, String> col(String title, double width, Function<T, String> extractor) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(extractor.apply(data.getValue())));
        if (width > 0) {
            column.setPrefWidth(width);
        }
        return column;
    }

    public static void fixHeight(TableView<?> table, int rows) {
        table.setPrefHeight(40 + Math.max(rows, 1) * 32.0);
        table.setMinHeight(Region.USE_PREF_SIZE);
    }

    public static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static Label subTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("sub-title");
        return label;
    }

    public static Region placeholder(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("placeholder");
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    public static void addInfo(GridPane grid, int row, String label, String value) {
        Label key = new Label(label + ":");
        key.getStyleClass().add("info-key");
        Label val = new Label(value);
        val.getStyleClass().add("info-value");
        val.setWrapText(true);
        grid.add(key, 0, row);
        grid.add(val, 1, row);
    }

    public static VBox card(javafx.scene.Node... children) {
        VBox card = new VBox(10, children);
        card.getStyleClass().add("card");
        return card;
    }

    /**
     * Wraps content in a ScrollPane that behaves well on resize: the content
     * stretches to fill the viewport when there is room, and when the window
     * becomes narrower (or shorter) than the content's minimum size, scrollbars
     * appear instead of clipping details away.
     */
    public static ScrollPane resizableScroll(Region content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.viewportBoundsProperty().addListener((obs, oldBounds, bounds) -> {
            content.setPrefWidth(Math.max(bounds.getWidth(), content.minWidth(-1)));
            content.setPrefHeight(Math.max(bounds.getHeight(), content.minHeight(-1)));
        });
        return scroll;
    }
}
