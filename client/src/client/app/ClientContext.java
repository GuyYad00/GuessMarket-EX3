package client.app;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Shared state for the client screens: who is logged in, the animation toggle
 * and the poller every screen registers its refresh tasks with.
 * <p>
 * Unlike exercise 2 there is no engine here at all: the screens talk to the
 * server through {@link client.http.ServerApi} and the only "truth" is what the
 * server last answered.
 */
public class ClientContext {

    private final StringProperty currentUser = new SimpleStringProperty(null);
    private final BooleanProperty animationsEnabled = new SimpleBooleanProperty(false);
    private final Poller poller;

    public ClientContext(Poller poller) {
        this.poller = poller;
    }

    public Poller getPoller() {
        return poller;
    }

    public StringProperty currentUserProperty() {
        return currentUser;
    }

    public String getCurrentUser() {
        return currentUser.get();
    }

    public void setCurrentUser(String userName) {
        currentUser.set(userName);
    }

    public BooleanProperty animationsEnabledProperty() {
        return animationsEnabled;
    }

    public boolean isAnimationsEnabled() {
        return animationsEnabled.get();
    }

    /** Asks the poller for an immediate round, so the screen reflects an action without waiting a tick. */
    public void refreshSoon() {
        poller.pokeNow();
    }
}
