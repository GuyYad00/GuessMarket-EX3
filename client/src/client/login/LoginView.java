package client.login;

import client.http.ApiException;
import client.http.Constants;
import client.http.ServerApi;
import gm.dto.LoginResultDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * The login screen: a single user name, no password, no sign-up. The request is
 * asynchronous (the JavaFX thread never waits on the network); a name that is
 * already connected comes back as an error the user can read and then simply
 * try another name.
 */
public class LoginView extends StackPane {

    private final TextField nameField = new TextField();
    private final Button loginButton = new Button("Login");
    private final Label errorLabel = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Consumer<String> onLoggedIn;

    public LoginView(Consumer<String> onLoggedIn, String initialMessage) {
        this.onLoggedIn = onLoggedIn;
        getStyleClass().add("login-view");

        Label title = new Label("Guess Market");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("Prediction markets, client-server edition");
        subtitle.getStyleClass().add("login-subtitle");

        nameField.setPromptText("Your user name");
        nameField.setPrefWidth(260);
        nameField.setOnAction(e -> attemptLogin());
        nameField.textProperty().addListener((obs, o, n) -> errorLabel.setText(""));

        nameField.setId("login-name");
        loginButton.setId("login-button");
        loginButton.getStyleClass().add("primary-button");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> attemptLogin());

        progress.setPrefSize(22, 22);
        progress.setVisible(false);

        errorLabel.getStyleClass().add("login-error");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(380);
        if (initialMessage != null) {
            errorLabel.setText(initialMessage);
        }

        Label server = new Label("Server: " + Constants.BASE_URL);
        server.getStyleClass().add("hint");

        HBox row = new HBox(10, nameField, loginButton, progress);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, title, subtitle, new Label("Enter a user name to join:"), row, errorLabel, server);
        card.getStyleClass().addAll("card", "login-card");
        card.setPadding(new Insets(28));
        card.setMaxWidth(460);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setAlignment(Pos.CENTER_LEFT);

        getChildren().add(card);
        setAlignment(Pos.CENTER);
        setPadding(new Insets(40));
    }

    public void focusName() {
        nameField.requestFocus();
    }

    private void attemptLogin() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            errorLabel.setText("Please enter a user name.");
            return;
        }
        setBusy(true);
        ServerApi.login(name, this::onSuccess, this::onFailure);
    }

    private void onSuccess(LoginResultDTO result) {
        setBusy(false);
        onLoggedIn.accept(result.getUserName());
    }

    private void onFailure(ApiException error) {
        setBusy(false);
        errorLabel.setText(error.getMessage());
        nameField.selectAll();
        nameField.requestFocus();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        loginButton.setDisable(busy);
        nameField.setDisable(busy);
    }
}
