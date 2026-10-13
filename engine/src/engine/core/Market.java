package engine.core;

import engine.exception.EngineOperationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole system state on the server: every event (keyed by the running id
 * the server assigned, in upload order) and every registered user (keyed by
 * exact name). Events are also indexed by their normalised name, because in
 * exercise 3 the name is the unique business key coming from the XML files.
 */
public class Market {

    private final Map<Integer, Event> events = new LinkedHashMap<>();
    private final Map<String, Integer> eventIdByNormalisedName = new HashMap<>();
    private final Map<String, User> users = new LinkedHashMap<>();
    private int nextEventId = 1;

    /** @return the id the next added event will receive. */
    public int peekNextEventId() {
        return nextEventId;
    }

    public void addEvent(Event event) {
        events.put(event.getId(), event);
        eventIdByNormalisedName.put(normalise(event.getName()), event.getId());
        nextEventId = Math.max(nextEventId, event.getId() + 1);
    }

    public boolean containsEventNamed(String name) {
        return eventIdByNormalisedName.containsKey(normalise(name));
    }

    public static String normalise(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    public void addUser(User user) {
        users.put(user.getName(), user);
    }

    public boolean containsUser(String name) {
        return name != null && users.containsKey(name);
    }

    public List<Event> getAllEvents() {
        return new ArrayList<>(events.values());
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public Event getEventById(int id) {
        Event event = events.get(id);
        if (event == null) {
            throw new EngineOperationException("There is no event with id " + id + " in the system.");
        }
        return event;
    }

    public User getUserByName(String name) {
        User user = (name == null) ? null : users.get(name);
        if (user == null) {
            throw new EngineOperationException("There is no user named '" + name + "' in the system.");
        }
        return user;
    }
}
