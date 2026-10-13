package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * The events list together with the server state version it was taken from.
 * Clients that poll compare the version to the one they already rendered and
 * redraw only when something actually changed (no flicker).
 */
public class EventsListDTO {

    private final long version;
    private final List<EventDTO> events;

    public EventsListDTO(long version, List<EventDTO> events) {
        this.version = version;
        this.events = new ArrayList<>(events);
    }

    public long getVersion() {
        return version;
    }

    public List<EventDTO> getEvents() {
        return events;
    }
}
