package client.common;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;

/**
 * Optional, tasteful animations (bonus). Every method is a no-op unless the user
 * enables animations, so the base experience is completely static by default and
 * never exceeds a couple of seconds when enabled.
 */
public final class Animations {

    private Animations() {
    }

    /** Gently fades a node in - used when switching detail panels. */
    public static void fadeIn(Node node, boolean enabled) {
        if (!enabled || node == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(280), node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    /** A short "pop" used to acknowledge a successful action on a node. */
    public static void pop(Node node, boolean enabled) {
        if (!enabled || node == null) {
            return;
        }
        ScaleTransition scale = new ScaleTransition(Duration.millis(160), node);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.08);
        scale.setToY(1.08);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    /** A brief green glow, e.g. after a trade updates a balance. */
    public static void highlight(Node node, boolean enabled) {
        if (!enabled || node == null) {
            return;
        }
        DropShadow glow = new DropShadow(18, Color.web("#2e9e5b"));
        node.setEffect(glow);
        FadeTransition fade = new FadeTransition(Duration.millis(700), node);
        fade.setFromValue(1.0);
        fade.setToValue(1.0);
        fade.setOnFinished(e -> node.setEffect(null));
        fade.play();
    }
}
