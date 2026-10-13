package gm.server.chat;

import gm.dto.ChatDTO;
import gm.dto.ChatEntryDTO;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Bonus: the single server-side chat room. Everyone sees everything.
 * <p>
 * Clients poll with the number of lines they already have ("version") and get
 * back only the lines added since (delta fetching), together with the new
 * version. All methods are synchronised on this instance, so adding a line and
 * reading a consistent snapshot are both atomic.
 */
public class ChatManager {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final List<ChatEntryDTO> lines = new ArrayList<>();

    public synchronized void addLine(String userName, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("The chat message cannot be empty.");
        }
        if (clean.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("The chat message is too long (maximum "
                    + MAX_MESSAGE_LENGTH + " characters).");
        }
        lines.add(new ChatEntryDTO(userName, clean, LocalTime.now().format(TIME)));
    }

    /** @return the lines added after {@code fromVersion} (the count the client already has). */
    public synchronized ChatDTO getSince(int fromVersion) {
        int from = (fromVersion < 0 || fromVersion > lines.size()) ? 0 : fromVersion;
        return new ChatDTO(lines.size(), new ArrayList<>(lines.subList(from, lines.size())));
    }
}
