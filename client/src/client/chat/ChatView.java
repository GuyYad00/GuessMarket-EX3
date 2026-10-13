package client.chat;

import client.app.ClientContext;
import client.app.Poller;
import client.common.Dialogs;
import client.http.ServerApi;
import gm.dto.ChatDTO;
import gm.dto.ChatEntryDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Bonus: a chat room shared by everyone, running entirely through the server.
 * The client keeps the number of lines it already has ("version") and asks the
 * server only for the lines added since (delta fetching), exactly like the
 * course's summarising example.
 */
public class ChatView extends BorderPane {

    private final ClientContext context;
    private final ListView<ChatEntryDTO> lines = new ListView<>();
    private final TextField input = new TextField();
    private int version = 0;

    public ChatView(ClientContext context) {
        this.context = context;
        getStyleClass().add("chat-view");

        Label title = new Label("Chat room (everyone sees everything)");
        title.getStyleClass().add("pane-title");

        lines.setCellFactory(view -> new ChatCell());
        lines.setPlaceholder(new Label("No messages yet. Say hello!"));
        VBox.setVgrow(lines, Priority.ALWAYS);

        input.setPromptText("Type a message and press Enter");
        input.setOnAction(e -> send());
        HBox.setHgrow(input, Priority.ALWAYS);
        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("primary-button");
        sendButton.setId("chat-send");
        input.setId("chat-input");
        lines.setId("chat-lines");
        sendButton.setOnAction(e -> send());
        HBox inputRow = new HBox(8, input, sendButton);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, title, lines, inputRow);
        box.setPadding(new Insets(12));
        setCenter(box);

        context.getPoller().register(new Poller.Task<ChatDTO>() {
            @Override
            public ChatDTO fetch() {
                return ServerApi.getChatSync(version);
            }

            @Override
            public void apply(ChatDTO value) {
                append(value);
            }
        });
    }

    private void append(ChatDTO delta) {
        if (delta.getVersion() < version) {
            // the server restarted (fewer lines than we have): start over
            lines.getItems().clear();
            version = 0;
            return;
        }
        if (delta.getEntries().isEmpty()) {
            version = delta.getVersion();
            return;
        }
        lines.getItems().addAll(delta.getEntries());
        version = delta.getVersion();
        lines.scrollTo(lines.getItems().size() - 1);
    }

    private void send() {
        String text = input.getText() == null ? "" : input.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setDisable(true);
        ServerApi.sendChat(text, result -> {
            input.clear();
            input.setDisable(false);
            input.requestFocus();
            context.refreshSoon();
        }, error -> {
            input.setDisable(false);
            Dialogs.error("Could not send message", error.getMessage());
        });
    }

    private final class ChatCell extends ListCell<ChatEntryDTO> {
        @Override
        protected void updateItem(ChatEntryDTO entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean mine = entry.getUserName().equals(context.getCurrentUser());
            Label who = new Label("[" + entry.getTime() + "] " + entry.getUserName() + (mine ? " (you)" : ""));
            who.getStyleClass().add(mine ? "chat-author-me" : "chat-author");
            Label text = new Label(entry.getText());
            text.setWrapText(true);
            text.getStyleClass().add("chat-text");
            VBox box = new VBox(2, who, text);
            setGraphic(box);
            setText(null);
        }
    }
}
