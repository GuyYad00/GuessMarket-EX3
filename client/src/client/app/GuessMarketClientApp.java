package client.app;

import client.http.ApiException;
import client.http.HttpClientUtil;
import client.http.ServerApi;
import client.login.LoginView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * The JavaFX client application. Shows the login screen first and, after a
 * successful login, the main screen (Events / Account / Chat tabs). Owns the
 * single {@link Poller} and stops it (and logs out) when the window closes.
 */
public class GuessMarketClientApp extends Application {

    private Stage stage;
    private Scene scene;
    private final StackPane root = new StackPane();
    private ClientContext context;
    private MainView mainView;
    private String themeName = "Light";

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        Poller poller = new Poller(this::onPollStatus);
        this.context = new ClientContext(poller);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1240, screen.getWidth());
        double height = Math.min(800, screen.getHeight() - 40);

        scene = new Scene(root, width, height);
        applyTheme(themeName);
        showLogin(null);

        stage.setTitle("Guess Market - Client");
        stage.setScene(scene);
        stage.setMinWidth(Math.min(900, screen.getWidth()));
        stage.setMinHeight(Math.min(600, screen.getHeight() - 40));
        stage.setOnCloseRequest(e -> shutdown());
        stage.show();
        stage.centerOnScreen();
        if (stage.getX() < screen.getMinX()) {
            stage.setX(screen.getMinX());
        }
        if (stage.getY() < screen.getMinY()) {
            stage.setY(screen.getMinY());
        }
    }

    // ---- screen switching ----

    void showLogin(String message) {
        context.getPoller().stop();
        context.setCurrentUser(null);
        mainView = null;
        HttpClientUtil.clearCookies();
        LoginView login = new LoginView(this::onLoggedIn, message);
        setRoot(login);
        Platform.runLater(login::focusName);
        stage.setTitle("Guess Market - Client");
    }

    private void onLoggedIn(String userName) {
        context.setCurrentUser(userName);
        mainView = new MainView(context, this);
        setRoot(mainView);
        stage.setTitle("Guess Market - " + userName);
        context.getPoller().start();
    }

    private void setRoot(Parent content) {
        root.getChildren().setAll(content);
    }

    /** Called by the main view's Logout button. */
    void logout() {
        context.getPoller().stop();
        ServerApi.logout(r -> showLogin("You have been logged out."),
                e -> showLogin("Logged out locally (" + e.getMessage() + ")."));
    }

    // ---- poller status ----

    private void onPollStatus(ApiException error) {
        if (mainView == null) {
            return;
        }
        if (error == null) {
            mainView.setConnectionStatus(true, "Connected to " + client.http.Constants.BASE_URL);
            return;
        }
        if (error.isUnauthorized()) {
            // The server no longer knows this session (e.g. it was restarted): back to login.
            showLogin("Your session is no longer valid on the server. Please log in again.");
            return;
        }
        mainView.setConnectionStatus(false, error.getMessage());
    }

    // ---- theme (reused from exercise 2) ----

    void applyTheme(String name) {
        this.themeName = name;
        String resource = "/client/app/theme-" + name.toLowerCase() + ".css";
        scene.getStylesheets().clear();
        java.net.URL url = getClass().getResource(resource);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    String getThemeName() {
        return themeName;
    }

    // ---- shutdown ----

    private void shutdown() {
        context.getPoller().stop();
        if (context.getCurrentUser() != null) {
            // Free the user name on the server. Done on a (non-daemon) background
            // thread so the JavaFX thread never blocks on the network, even while
            // closing; the JVM simply waits for this short call before exiting.
            Thread t = new Thread(() -> {
                ServerApi.logoutSync();
                HttpClientUtil.shutdown();
            }, "logout-on-exit");
            t.setDaemon(false);
            t.start();
        } else {
            HttpClientUtil.shutdown();
        }
        Platform.exit();
    }
}
