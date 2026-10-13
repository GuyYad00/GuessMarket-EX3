package engine.exception;

/**
 * Thrown when a system-details XML file fails validation (schema-wise or
 * application-wise). Unchecked by design - the UI layer catches it and shows
 * the message to the user without the application crashing.
 */
public class InvalidFileException extends RuntimeException {

    public InvalidFileException(String message) {
        super(message);
    }

    public InvalidFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
