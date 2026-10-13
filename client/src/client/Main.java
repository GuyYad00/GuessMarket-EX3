package client;

import client.app.GuessMarketClientApp;
import javafx.application.Application;

/**
 * Plain launcher. It intentionally does not extend {@link Application}, so the
 * client can be started from a normal (class-path) jar without hitting the
 * "JavaFX runtime components are missing" guard.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Application.launch(GuessMarketClientApp.class, args);
    }
}
