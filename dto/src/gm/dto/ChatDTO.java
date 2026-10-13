package gm.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat delta (bonus): the lines added since the version the client asked for,
 * plus the current chat version (= total number of lines on the server).
 */
public class ChatDTO {

    private final int version;
    private final List<ChatEntryDTO> entries;

    public ChatDTO(int version, List<ChatEntryDTO> entries) {
        this.version = version;
        this.entries = new ArrayList<>(entries);
    }

    public int getVersion() {
        return version;
    }

    public List<ChatEntryDTO> getEntries() {
        return entries;
    }
}
