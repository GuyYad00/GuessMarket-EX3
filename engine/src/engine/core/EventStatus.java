package engine.core;

/**
 * The life-cycle of an event. It starts {@link #NOT_STARTED} when loaded, the
 * market maker moves it to {@link #ACTIVE}, and finally locks it as
 * {@link #CLOSED}. A closed event can never be re-opened.
 */
public enum EventStatus {
    NOT_STARTED("Not started"),
    ACTIVE("Active"),
    CLOSED("Closed");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
