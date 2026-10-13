package gm.dto;

/** A single chat line (bonus): who wrote it, what, and when (HH:mm:ss). */
public class ChatEntryDTO {

    private final String userName;
    private final String text;
    private final String time;

    public ChatEntryDTO(String userName, String text, String time) {
        this.userName = userName;
        this.text = text;
        this.time = time;
    }

    public String getUserName() {
        return userName;
    }

    public String getText() {
        return text;
    }

    public String getTime() {
        return time;
    }
}
