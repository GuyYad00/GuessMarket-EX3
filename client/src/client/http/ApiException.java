package client.http;

/** A failed server call: carries the HTTP status (0 = could not reach the server) and the server's message. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isUnauthorized() {
        return status == 401;
    }

    public boolean isConnectionFailure() {
        return status == 0;
    }
}
