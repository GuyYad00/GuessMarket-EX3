package gm.dto;

/** Returned by a successful login: the (trimmed) user name the server registered. */
public class LoginResultDTO {

    private final String userName;
    private final String message;

    public LoginResultDTO(String userName, String message) {
        this.userName = userName;
        this.message = message;
    }

    public String getUserName() {
        return userName;
    }

    public String getMessage() {
        return message;
    }
}
