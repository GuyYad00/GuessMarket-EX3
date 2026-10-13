package gm.dto;

/**
 * The body of every non-2xx response from the server: a single human readable
 * message the client shows as-is.
 */
public class ErrorDTO {

    private final String error;

    public ErrorDTO(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
