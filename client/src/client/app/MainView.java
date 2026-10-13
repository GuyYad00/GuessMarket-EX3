package client.app;

import client.account.AccountView;
import client.chat.ChatView;
import client.events.EventsView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * The screen shown after login, laid out as in the assignment's sketch: a
 * "Guess Market" header with the tabs Events | Account (plus the bonus Chat
 * tab), the logged-in user, a connection indicator, and the small bonus
 * controls (theme, animations) kept from exercise 2.
 */
public class MainView extends BorderPane {

    private final Circle connectionDot = new Circle(6);
    private final Label connectionLabel = new Label("Connecting...");

    public MainView(ClientContext context, GuessMarketClientApp app) {
        getStyleClass().add("main-view");
        setTop(buildHeader(context, app));

        EventsView eventsView = new EventsView(context);
        AccountView accountView = new AccountView(context);
        ChatView chatView = new ChatView(context);

        Tab eventsTab = new Tab("Events", wrapInScroll(eventsView));
        eventsTab.setClosable(false);
        Tab accountTab = new Tab("Account", wrapInScroll(accountView));
        accountTab.setClosable(false);
        Tab chatTab = new Tab("Chat (bonus)", chatView);
        chatTab.setClosable(false);

        TabPane tabs = new TabPane(eventsTab, accountTab, chatTab);
        tabs.getStyleClass().add("main-tabs");
        tabs.setId("main-tabs");
        setCenter(tabs);
    }

    private Region buildHeader(ClientContext context, GuessMarketClientApp app) {
        Label title = new Label("Guess Market");
        title.getStyleClass().add("app-title");

        Label user = new Label();
        user.getStyleClass().add("user-badge");
        user.textProperty().bind(context.currentUserProperty().map(name -> "Logged in as: " + name));

        connectionDot.getStyleClass().add("connection-dot");
        connectionLabel.getStyleClass().add("status-label");
        HBox connection = new HBox(6, connectionDot, connectionLabel);
        connection.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ComboBox<String> themeCombo = new ComboBox<>();
        themeCombo.getItems().addAll("Light", "Dark", "Ocean");
        themeCombo.setValue(app.getThemeName());
        themeCombo.setOnAction(e -> app.applyTheme(themeCombo.getValue()));

        CheckBox animations = new CheckBox("Animations");
        animations.selectedProperty().bindBidirectional(context.animationsEnabledProperty());

        Button logout = new Button("Logout");
        logout.getStyleClass().add("danger-button");
        logout.setId("logout-button");
        logout.setOnAction(e -> app.logout());

        HBox row = new HBox(14, title, new Separator(javafx.geometry.Orientation.VERTICAL), user, connection,
                spacer, new Label("Theme:"), themeCombo, animations, logout);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(row);
        header.getStyleClass().add("top-bar");
        header.setPadding(new Insets(12, 16, 12, 16));
        return header;
    }

    private Region wrapInScroll(Region content) {
        return client.common.Fx.resizableScroll(content);
    }

    public void setConnectionStatus(boolean connected, String message) {
        connectionDot.getStyleClass().removeAll("connected", "disconnected");
        connectionDot.getStyleClass().add(connected ? "connected" : "disconnected");
        connectionLabel.setText(connected ? "Connected" : "Disconnected: " + message);
    }
}
