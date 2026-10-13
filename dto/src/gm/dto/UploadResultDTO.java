package gm.dto;

import java.util.ArrayList;
import java.util.List;

/** Returned by a successful file upload: how many events were added and their names. */
public class UploadResultDTO {

    private final String message;
    private final int eventsAdded;
    private final List<String> eventNames;

    public UploadResultDTO(String message, int eventsAdded, List<String> eventNames) {
        this.message = message;
        this.eventsAdded = eventsAdded;
        this.eventNames = new ArrayList<>(eventNames);
    }

    public String getMessage() {
        return message;
    }

    public int getEventsAdded() {
        return eventsAdded;
    }

    public List<String> getEventNames() {
        return eventNames;
    }
}
