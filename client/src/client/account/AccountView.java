package client.account;

import client.app.ClientContext;
import client.app.Poller;
import client.common.Animations;
import client.common.Dialogs;
import client.common.Format;
import client.common.Fx;
import client.http.ServerApi;
import gm.dto.UploadResultDTO;
import gm.dto.UserDTO;
import gm.dto.UsersListDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.stream.Collectors;

/**
 * The Account tab, laid out as in the assignment's sketch: the file upload line
 * on top, the table of (other) users on the left, and the private account area
 * on the right (details, participations, single-event drill-down, balance with
 * "load funds", and the full account ledger).
 */
public class AccountView extends BorderPane {

    private final ClientContext context;
    private final TableView<UserDTO> usersTable = new TableView<>();
    private final MyAccountPane accountPane;

    private final TextField filePathField = new TextField();
    private final Button uploadButton = new Button("Load File...");
    private final ProgressIndicator uploadProgress = new ProgressIndicator();
    private final Label uploadStatus = new Label();

    private long renderedUsersVersion = -1;

    public AccountView(ClientContext context) {
        this.context = context;
        this.accountPane = new MyAccountPane(context);
        getStyleClass().add("users-view");

        setTop(buildUploadBar());
        SplitPane split = new SplitPane(buildUsersPane(), accountPane);
        split.setDividerPositions(0.42);
        SplitPane.setResizableWithParent(accountPane, true);
        setCenter(split);

        context.getPoller().register(new Poller.Task<UsersListDTO>() {
            @Override
            public UsersListDTO fetch() {
                return ServerApi.getUsersSync();
            }

            @Override
            public void apply(UsersListDTO value) {
                onUsersFetched(value);
            }
        });
        context.getPoller().register(accountPane.pollTask());
    }

    // ---- Upload line ----

    private Region buildUploadBar() {
        filePathField.setPromptText("No file uploaded yet. Click \"Load File...\" to choose an exercise-3 XML file.");
        filePathField.setEditable(false);
        filePathField.setId("upload-path");
        uploadButton.setId("upload-button");
        HBox.setHgrow(filePathField, Priority.ALWAYS);

        uploadButton.getStyleClass().add("primary-button");
        uploadButton.setOnAction(e -> chooseAndUpload());

        uploadProgress.setPrefSize(22, 22);
        uploadProgress.setVisible(false);
        uploadStatus.getStyleClass().add("status-label");

        HBox row = new HBox(10, new Label("File:"), filePathField, uploadButton, uploadProgress, uploadStatus);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.getStyleClass().add("top-bar");
        return row;
    }

    private void chooseAndUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Guess Market events file (exercise-3 format)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        Window owner = getScene() == null ? null : getScene().getWindow();
        File chosen = chooser.showOpenDialog(owner);
        if (chosen != null) {
            upload(chosen);
        }
    }

    /** Uploads the given file (also used directly by the automated UI test, which cannot drive the native file dialog). */
    public void upload(File file) {
        filePathField.setText(file.getAbsolutePath());
        setUploading(true, "Uploading " + file.getName() + "...");
        ServerApi.uploadFile(file, (UploadResultDTO result) -> {
            setUploading(false, result.getEventsAdded() + " event(s) added.");
            Dialogs.info("File loaded", result.getMessage() + "\n\nEvents added:\n  * "
                    + String.join("\n  * ", result.getEventNames()));
            context.refreshSoon();
            Animations.highlight(filePathField, context.isAnimationsEnabled());
        }, error -> {
            setUploading(false, "Upload failed.");
            Dialogs.error("Could not load file", error.getMessage());
        });
    }

    private void setUploading(boolean uploading, String status) {
        uploadProgress.setVisible(uploading);
        uploadButton.setDisable(uploading);
        uploadStatus.setText(status);
    }

    // ---- Users table ----

    private VBox buildUsersPane() {
        Label title = new Label("Users in the system");
        title.getStyleClass().add("pane-title");

        usersTable.getColumns().add(Fx.col("Name", 120, u -> u.getName()
                + (u.getName().equals(context.getCurrentUser()) ? " (you)" : "")));
        usersTable.getColumns().add(Fx.col("Balance", 100, u -> Format.money(u.getBalance())));
        usersTable.getColumns().add(Fx.col("Market maker", 95, u -> u.isMarketMaker() ? "Yes" : "No"));
        usersTable.getColumns().add(Fx.col("MM of events", 100,
                u -> u.getMarketMakerEventIds().isEmpty() ? "-"
                        : u.getMarketMakerEventIds().stream().map(id -> "#" + id).collect(Collectors.joining(", "))));
        usersTable.getColumns().add(Fx.col("Online", 60, u -> u.isConnected() ? "Yes" : "No"));
        usersTable.getColumns().add(Fx.col("Blocked", 65, u -> u.isBlocked() ? "Yes" : "No"));
        usersTable.setPlaceholder(new Label("No users yet."));
        usersTable.setMinWidth(300);
        usersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        usersTable.setId("users-table");
        VBox.setVgrow(usersTable, Priority.ALWAYS);

        VBox box = new VBox(8, title, usersTable);
        box.setPadding(new Insets(12));
        box.setMinWidth(320);
        box.getStyleClass().add("list-pane");
        return box;
    }

    private void onUsersFetched(UsersListDTO list) {
        if (list.getVersion() == renderedUsersVersion) {
            return;
        }
        renderedUsersVersion = list.getVersion();
        UserDTO selected = usersTable.getSelectionModel().getSelectedItem();
        String selectedName = selected == null ? null : selected.getName();
        usersTable.getItems().setAll(list.getUsers());
        if (selectedName != null) {
            for (UserDTO user : usersTable.getItems()) {
                if (user.getName().equals(selectedName)) {
                    usersTable.getSelectionModel().select(user);
                    break;
                }
            }
        }
    }
}
