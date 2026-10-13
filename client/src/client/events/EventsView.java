package client.events;

import client.app.ClientContext;
import client.app.Poller;
import client.common.Animations;
import client.http.ServerApi;
import gm.dto.EventDTO;
import gm.dto.EventsListDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The Events tab (same layout as exercise 2): the filter line on top, the list
 * of events on the left and the selected event's details and trading area on
 * the right. The data now arrives from the server through the poller; the list
 * is only rebuilt when the server's state version changed, and the selection is
 * kept by event id across rebuilds.
 */
public class EventsView extends BorderPane {

    private final ClientContext context;
    private final ListView<EventDTO> eventList = new ListView<>();
    private final EventDetailsPane detailsPane;

    private final ToggleGroup methodGroup = new ToggleGroup();
    private final ToggleGroup statusGroup = new ToggleGroup();
    private final ToggleGroup commissionGroup = new ToggleGroup();

    private List<EventDTO> allEvents = new ArrayList<>();
    private long renderedVersion = -1;

    public EventsView(ClientContext context) {
        this.context = context;
        this.detailsPane = new EventDetailsPane(context);
        getStyleClass().add("events-view");

        setTop(buildFilterBar());
        SplitPane split = new SplitPane(buildListPane(), detailsPane);
        split.setDividerPositions(0.28);
        SplitPane.setResizableWithParent(detailsPane, true);
        setCenter(split);

        eventList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                detailsPane.clear();
            } else if (oldValue == null || oldValue.getId() != newValue.getId()) {
                detailsPane.showEvent(newValue.getId());
                Animations.fadeIn(detailsPane, context.isAnimationsEnabled());
            }
        });

        context.getPoller().register(new Poller.Task<EventsListDTO>() {
            @Override
            public EventsListDTO fetch() {
                return ServerApi.getEventsSync();
            }

            @Override
            public void apply(EventsListDTO value) {
                onEventsFetched(value);
            }
        });
        context.getPoller().register(detailsPane.pollTask());
    }

    private VBox buildListPane() {
        Label title = new Label("Events");
        title.getStyleClass().add("pane-title");

        eventList.setMinWidth(220);
        eventList.setId("event-list");
        eventList.setPlaceholder(new Label("No events yet.\nUpload a file from the Account tab."));
        eventList.setCellFactory(view -> new EventCell());
        VBox.setVgrow(eventList, Priority.ALWAYS);

        VBox box = new VBox(8, title, eventList);
        box.setPadding(new Insets(12));
        box.setMinWidth(240);
        box.getStyleClass().add("list-pane");
        return box;
    }

    private VBox buildFilterBar() {
        FlowPane method = filterGroup("By method", methodGroup,
                new String[]{"All", "LMSR", "Order Book"});
        FlowPane status = filterGroup("By status", statusGroup,
                new String[]{"All", "Not started", "Active", "Closed"});
        FlowPane commission = filterGroup("By commission", commissionGroup,
                new String[]{"All", "on-purchase", "on-close"});

        VBox bar = new VBox(6, method, status, commission);
        bar.setPadding(new Insets(12, 12, 6, 12));
        bar.getStyleClass().add("filter-bar");
        return bar;
    }

    private FlowPane filterGroup(String title, ToggleGroup group, String[] options) {
        FlowPane pane = new FlowPane(6, 6);
        pane.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(title + ":");
        label.getStyleClass().add("filter-label");
        label.setMinWidth(110);
        pane.getChildren().add(label);

        for (String option : options) {
            ToggleButton button = new ToggleButton(option);
            button.setToggleGroup(group);
            button.setUserData(option);
            button.getStyleClass().add("filter-toggle");
            if (option.equals("All")) {
                button.setSelected(true);
            }
            button.setOnAction(e -> {
                if (!button.isSelected()) {
                    button.setSelected(true); // keep at least one selected
                }
                applyFilters();
            });
            pane.getChildren().add(button);
        }
        return pane;
    }

    private String selected(ToggleGroup group) {
        return group.getSelectedToggle() == null ? "All" : (String) group.getSelectedToggle().getUserData();
    }

    private void onEventsFetched(EventsListDTO list) {
        if (list.getVersion() == renderedVersion) {
            return; // nothing changed on the server since the last redraw
        }
        renderedVersion = list.getVersion();
        allEvents = list.getEvents();
        applyFilters();
    }

    private void applyFilters() {
        String method = selected(methodGroup);
        String status = selected(statusGroup);
        String commission = selected(commissionGroup);

        ObservableList<EventDTO> filtered = FXCollections.observableArrayList();
        for (EventDTO event : allEvents) {
            if (!"All".equals(method) && !event.getMethod().equals(method)) {
                continue;
            }
            if (!"All".equals(status) && !event.getStatus().equals(status)) {
                continue;
            }
            if (!"All".equals(commission) && !event.getCommissionType().equals(commission)) {
                continue;
            }
            filtered.add(event);
        }

        EventDTO previous = eventList.getSelectionModel().getSelectedItem();
        int previousId = previous == null ? -1 : previous.getId();
        eventList.setItems(filtered);
        if (previousId >= 0) {
            for (EventDTO event : filtered) {
                if (event.getId() == previousId) {
                    eventList.getSelectionModel().select(event);
                    return;
                }
            }
        }
        if (eventList.getSelectionModel().getSelectedItem() == null) {
            detailsPane.clear();
        }
    }

    /** Compact list cell: number, name and a status/method line. */
    private static final class EventCell extends ListCell<EventDTO> {
        @Override
        protected void updateItem(EventDTO event, boolean empty) {
            super.updateItem(event, empty);
            if (empty || event == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label name = new Label("#" + event.getId() + "  " + event.getName());
            name.getStyleClass().add("event-cell-name");
            Label meta = new Label(event.getMethod() + "  |  " + event.getStatus() + "  |  MM: " + event.getMarketMakerName());
            meta.getStyleClass().add("event-cell-meta");
            VBox box = new VBox(2, name, meta);
            HBox row = new HBox(box);
            setGraphic(row);
            setText(null);
        }
    }
}
