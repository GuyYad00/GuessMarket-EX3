package engine.api;

import gm.dto.BuyResultDTO;
import gm.dto.CloseResultDTO;
import gm.dto.EventDetailsDTO;
import gm.dto.EventsListDTO;
import gm.dto.FundsResultDTO;
import gm.dto.LoginResultDTO;
import gm.dto.OpenResultDTO;
import gm.dto.OrderResultDTO;
import gm.dto.UploadResultDTO;
import gm.dto.UserDetailsDTO;
import gm.dto.UsersListDTO;

import java.io.InputStream;

/**
 * The system engine facade. In exercise 3 a single instance lives inside the
 * Tomcat server and every servlet translates an HTTP request into exactly one
 * call on this interface.
 * <p>
 * The engine is passive and knows nothing about HTTP, sessions or JavaFX. It
 * hands out immutable DTOs (from the shared {@code dto} module) and throws
 * unchecked exceptions on invalid input: {@link engine.exception.InvalidFileException}
 * for a rejected XML file and {@link engine.exception.EngineOperationException}
 * for every other illegal operation.
 * <p>
 * The engine is <b>not</b> thread safe by itself; the server serialises access
 * to it (see {@code gm.server.utils.EngineHolder}).
 */
public interface Engine {

    // ---- users / sessions ----

    /**
     * Registers the user as connected. A brand-new name creates a fresh user with
     * an empty account; a name that already exists but is not connected is
     * simply re-connected (its balance and roles are kept for the server's
     * lifetime). A name that is currently connected is rejected.
     */
    LoginResultDTO login(String userName);

    /** Marks the user as disconnected so the name can be used again. */
    void logout(String userName);

    boolean isUserRegistered(String userName);

    // ---- files ----

    /**
     * Validates an exercise-3 XML document and, only if the whole file is valid,
     * adds all of its events to the system with the uploader as their market
     * maker. The content is read straight from the stream and never persisted.
     */
    UploadResultDTO uploadEvents(InputStream xmlContent, String uploaderName);

    // ---- queries ----

    /** A counter that grows on every state change; lets clients skip redundant redraws. */
    long getVersion();

    EventsListDTO getEvents();

    UsersListDTO getUsers();

    EventDetailsDTO getEventDetails(int eventId);

    UserDetailsDTO getUserDetails(String userName);

    // ---- actions ----

    /** Market maker opens (activates) an event, paying the LMSR subsidy / order-book initial. */
    OpenResultDTO openEvent(int eventId, String marketMakerName);

    /** Market maker closes (resolves) an event, paying out the winners. */
    CloseResultDTO closeEvent(int eventId, int winningOptionNumber, String marketMakerName);

    /** Buys shares of an option in an active LMSR event. */
    BuyResultDTO buyLmsrShares(int eventId, String userName, int optionNumber, long shares);

    /** Submits a buy/sell order to an active order-book event. {@code side} is "Buy" or "Sell". */
    OrderResultDTO placeOrder(int eventId, String userName, int optionNumber,
                              String side, double price, long quantity);

    /** Loads money into the user's account (exercise 3 replaces the file-defined initial cash). */
    FundsResultDTO loadFunds(String userName, double amount);
}
