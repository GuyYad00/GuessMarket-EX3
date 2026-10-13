package client.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import gm.dto.BuyResultDTO;
import gm.dto.ChatDTO;
import gm.dto.CloseResultDTO;
import gm.dto.ErrorDTO;
import gm.dto.EventDetailsDTO;
import gm.dto.EventsListDTO;
import gm.dto.FundsResultDTO;
import gm.dto.LoginResultDTO;
import gm.dto.OpenResultDTO;
import gm.dto.OrderResultDTO;
import gm.dto.UploadResultDTO;
import gm.dto.UserDetailsDTO;
import gm.dto.UsersListDTO;
import javafx.application.Platform;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * The client's single gateway to the server. Every engine capability from
 * exercise 2 is now one HTTP call here, and the JSON answer is turned back into
 * the very same DTO classes (shared {@code dto} module) the screens already know.
 * <p>
 * Two flavours:
 * <ul>
 *   <li><b>async</b> methods are for the JavaFX Application Thread. They never
 *       block; the success / failure callbacks are delivered back on the JavaFX
 *       thread via {@link Platform#runLater}.</li>
 *   <li><b>sync</b> methods are for the background polling thread only.</li>
 * </ul>
 * Response bodies are always consumed (and therefore closed) exactly once.
 */
public final class ServerApi {

    private static final Gson GSON = new Gson();
    private static final RequestBody EMPTY_BODY = RequestBody.create(new byte[0], null);

    private ServerApi() {
    }

    // ==================== session ====================

    public static void login(String userName, Consumer<LoginResultDTO> onSuccess, Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.LOGIN).newBuilder()
                .addQueryParameter("username", userName).build();
        async(post(url), LoginResultDTO.class, onSuccess, onError);
    }

    public static void logout(Consumer<LoginResultDTO> onSuccess, Consumer<ApiException> onError) {
        async(post(HttpUrl.parse(Constants.LOGOUT)), LoginResultDTO.class, onSuccess, onError);
    }

    /** Best-effort synchronous logout used when the window is closed. */
    public static void logoutSync() {
        try {
            sync(post(HttpUrl.parse(Constants.LOGOUT)), LoginResultDTO.class);
        } catch (ApiException ignored) {
            // the server may already be down; nothing to do
        }
    }

    // ==================== files ====================

    public static void uploadFile(File file, Consumer<UploadResultDTO> onSuccess, Consumer<ApiException> onError) {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, MediaType.parse("text/xml")))
                .build();
        Request request = new Request.Builder().url(Constants.UPLOAD).post(body).build();
        async(request, UploadResultDTO.class, onSuccess, onError);
    }

    // ==================== queries (sync, for the poller) ====================

    public static EventsListDTO getEventsSync() {
        return sync(get(HttpUrl.parse(Constants.EVENTS)), EventsListDTO.class);
    }

    public static EventDetailsDTO getEventDetailsSync(int eventId) {
        HttpUrl url = HttpUrl.parse(Constants.EVENT_DETAILS).newBuilder()
                .addQueryParameter("id", String.valueOf(eventId)).build();
        return sync(get(url), EventDetailsDTO.class);
    }

    public static UsersListDTO getUsersSync() {
        return sync(get(HttpUrl.parse(Constants.USERS)), UsersListDTO.class);
    }

    public static UserDetailsDTO getMyDetailsSync() {
        return sync(get(HttpUrl.parse(Constants.USER_DETAILS)), UserDetailsDTO.class);
    }

    public static ChatDTO getChatSync(int version) {
        HttpUrl url = HttpUrl.parse(Constants.CHAT).newBuilder()
                .addQueryParameter("version", String.valueOf(version)).build();
        return sync(get(url), ChatDTO.class);
    }

    // ==================== queries (async, for one-off refreshes from the UI) ====================

    public static void getEventDetails(int eventId, Consumer<EventDetailsDTO> onSuccess,
                                       Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.EVENT_DETAILS).newBuilder()
                .addQueryParameter("id", String.valueOf(eventId)).build();
        async(get(url), EventDetailsDTO.class, onSuccess, onError);
    }

    // ==================== actions ====================

    public static void openEvent(int eventId, Consumer<OpenResultDTO> onSuccess, Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.EVENT_OPEN).newBuilder()
                .addQueryParameter("id", String.valueOf(eventId)).build();
        async(post(url), OpenResultDTO.class, onSuccess, onError);
    }

    public static void closeEvent(int eventId, int winningOption, Consumer<CloseResultDTO> onSuccess,
                                  Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.EVENT_CLOSE).newBuilder()
                .addQueryParameter("id", String.valueOf(eventId))
                .addQueryParameter("winner", String.valueOf(winningOption)).build();
        async(post(url), CloseResultDTO.class, onSuccess, onError);
    }

    public static void buyShares(int eventId, int optionNumber, long shares, Consumer<BuyResultDTO> onSuccess,
                                 Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.EVENT_BUY).newBuilder()
                .addQueryParameter("id", String.valueOf(eventId))
                .addQueryParameter("option", String.valueOf(optionNumber))
                .addQueryParameter("shares", String.valueOf(shares)).build();
        async(post(url), BuyResultDTO.class, onSuccess, onError);
    }

    public static void placeOrder(int eventId, int optionNumber, String side, double price, long quantity,
                                  Consumer<OrderResultDTO> onSuccess, Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.EVENT_ORDER).newBuilder()
                .addQueryParameter("id", String.valueOf(eventId))
                .addQueryParameter("option", String.valueOf(optionNumber))
                .addQueryParameter("side", side)
                .addQueryParameter("price", String.valueOf(price))
                .addQueryParameter("quantity", String.valueOf(quantity)).build();
        async(post(url), OrderResultDTO.class, onSuccess, onError);
    }

    public static void loadFunds(double amount, Consumer<FundsResultDTO> onSuccess, Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.USER_FUNDS).newBuilder()
                .addQueryParameter("amount", String.valueOf(amount)).build();
        async(post(url), FundsResultDTO.class, onSuccess, onError);
    }

    public static void sendChat(String text, Consumer<ChatDTO> onSuccess, Consumer<ApiException> onError) {
        HttpUrl url = HttpUrl.parse(Constants.CHAT).newBuilder()
                .addQueryParameter("text", text).build();
        async(post(url), ChatDTO.class, onSuccess, onError);
    }

    // ==================== plumbing ====================

    private static Request get(HttpUrl url) {
        return new Request.Builder().url(url).get().build();
    }

    private static Request post(HttpUrl url) {
        return new Request.Builder().url(url).post(EMPTY_BODY).build();
    }

    private static <T> void async(Request request, Class<T> type, Consumer<T> onSuccess,
                                  Consumer<ApiException> onError) {
        HttpClientUtil.runAsync(request, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                deliver(onError, new ApiException(0, connectionMessage(e)));
            }

            @Override
            public void onResponse(Call call, Response response) {
                T value;
                try {
                    value = parse(response, type);
                } catch (ApiException e) {
                    deliver(onError, e);
                    return;
                }
                deliver(onSuccess, value);
            }
        });
    }

    private static <T> T sync(Request request, Class<T> type) {
        Response response;
        try {
            response = HttpClientUtil.runSync(request);
        } catch (IOException e) {
            throw new ApiException(0, connectionMessage(e));
        }
        return parse(response, type);
    }

    /** Reads the body exactly once (which also closes it) and maps non-2xx answers to {@link ApiException}. */
    private static <T> T parse(Response response, Class<T> type) {
        String body;
        try (Response r = response) {
            body = r.body() == null ? "" : r.body().string();
        } catch (IOException e) {
            throw new ApiException(0, "The server's answer could not be read: " + e.getMessage());
        }
        if (!response.isSuccessful()) {
            throw new ApiException(response.code(), extractError(body, response.code()));
        }
        try {
            return GSON.fromJson(body, type);
        } catch (JsonSyntaxException e) {
            throw new ApiException(response.code(), "The server returned an unexpected answer.");
        }
    }

    private static String extractError(String body, int code) {
        try {
            ErrorDTO error = GSON.fromJson(body, ErrorDTO.class);
            if (error != null && error.getError() != null && !error.getError().isEmpty()) {
                return error.getError();
            }
        } catch (JsonSyntaxException ignored) {
            // fall through to a generic message
        }
        if (code == 404) {
            return "The server endpoint was not found (HTTP 404). Is the guessmarket WAR deployed?";
        }
        return "The server returned HTTP " + code + ".";
    }

    private static String connectionMessage(IOException e) {
        return "Could not reach the server at " + Constants.BASE_URL + ". Is Tomcat running? (" + e.getMessage() + ")";
    }

    private static <T> void deliver(Consumer<T> consumer, T value) {
        if (consumer == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            consumer.accept(value);
        } else {
            Platform.runLater(() -> consumer.accept(value));
        }
    }
}
