package engine.exception;

/**
 * Thrown when an engine operation cannot be performed in the current state -
 * no file loaded, unknown event/user, event in the wrong status, a non-market
 * maker trying to open an event, an illegal price or amount, and so on.
 */
public class EngineOperationException extends RuntimeException {

    public EngineOperationException(String message) {
        super(message);
    }
}
