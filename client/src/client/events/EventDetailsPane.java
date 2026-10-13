package client.events;

import client.app.ClientContext;
import client.app.Poller;
import client.common.Animations;
import client.common.Dialogs;
import client.common.Format;
import client.common.Fx;
import client.http.ServerApi;
import gm.dto.BuyResultDTO;
import gm.dto.CloseResultDTO;
import gm.dto.EventDTO;
import gm.dto.EventDetailsDTO;
import gm.dto.LmsrTradeDTO;
import gm.dto.OpenResultDTO;
import gm.dto.OptionStateDTO;
import gm.dto.OrderBookOptionDTO;
import gm.dto.OrderDTO;
import gm.dto.OrderFillDTO;
import gm.dto.OrderResultDTO;
import gm.dto.ParticipantDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Renders the full details of the selected event and hosts the actions the user
 * can perform on it (buy LMSR shares, submit order-book orders, open/close as
 * market maker). Reused from exercise 2 with three client-server changes:
 * <ul>
 *   <li>data comes from the server through a poller task and the pane is only
 *       redrawn when the server's state version changed;</li>
 *   <li>the input controls (shares, price, quantity, option, side) are created
 *       once and re-parented on every redraw, so a background refresh never
 *       wipes what the user is typing; the scroll position is preserved too;</li>
 *   <li>actions are asynchronous HTTP calls; the result dialog is shown when the
 *       server answers and the poller is poked for an immediate refresh.</li>
 * </ul>
 */
public class EventDetailsPane extends ScrollPane {

    private final ClientContext context;
    private final VBox body = new VBox(14);
    private int currentEventId = -1;
    private long renderedVersion = -1;
    private boolean showChart;

    // persistent input controls (survive redraws)
    private final ComboBox<String> buyOption = new ComboBox<>();
    private final TextField buyShares = new TextField();
    private final ComboBox<String> orderOption = new ComboBox<>();
    private final ComboBox<String> orderSide = new ComboBox<>();
    private final TextField orderPrice = new TextField();
    private final TextField orderQuantity = new TextField();
    private final ComboBox<String> winnerCombo = new ComboBox<>();

    public EventDetailsPane(ClientContext context) {
        this.context = context;
        body.setPadding(new Insets(16));
        body.getStyleClass().add("details-body");
        setContent(body);
        // Fill the viewport when there is room; show a horizontal scrollbar instead of
        // clipping when the window is narrower than the content's minimum width.
        setFitToWidth(false);
        viewportBoundsProperty().addListener((obs, oldBounds, bounds) ->
                body.setPrefWidth(Math.max(bounds.getWidth(), body.minWidth(-1))));
        getStyleClass().add("details-pane");

        buyOption.setId("buy-option");
        buyShares.setId("buy-shares");
        orderOption.setId("order-option");
        orderSide.setId("order-side");
        orderPrice.setId("order-price");
        orderQuantity.setId("order-quantity");
        winnerCombo.setId("winner-combo");
        buyShares.setPromptText("shares");
        buyShares.setPrefWidth(120);
        orderSide.getItems().addAll("Buy", "Sell");
        orderSide.getSelectionModel().selectFirst();
        orderPrice.setPromptText("price e.g. 0.60");
        orderPrice.setPrefWidth(120);
        orderQuantity.setPromptText("quantity");
        orderQuantity.setPrefWidth(100);
        clear();
    }

    /** The poller task that keeps this pane fresh while an event is selected. */
    public Poller.Task<EventDetailsDTO> pollTask() {
        return new Poller.Task<EventDetailsDTO>() {
            @Override
            public boolean isActive() {
                return currentEventId >= 0;
            }

            @Override
            public EventDetailsDTO fetch() {
                return ServerApi.getEventDetailsSync(currentEventId);
            }

            @Override
            public void apply(EventDetailsDTO value) {
                if (value.getMeta().getId() == currentEventId) {
                    render(value, false);
                }
            }
        };
    }

    public void clear() {
        currentEventId = -1;
        renderedVersion = -1;
        body.getChildren().setAll(Fx.placeholder("Select an event to see its details."));
    }

    /** A new event was selected: fetch it right away (async) instead of waiting for the next poll. */
    public void showEvent(int eventId) {
        this.currentEventId = eventId;
        this.renderedVersion = -1;
        body.getChildren().setAll(Fx.placeholder("Loading event #" + eventId + "..."));
        ServerApi.getEventDetails(eventId, details -> {
            if (details.getMeta().getId() == currentEventId) {
                render(details, true);
            }
        }, error -> {
            if (eventId == currentEventId) {
                body.getChildren().setAll(Fx.placeholder(error.getMessage()));
            }
        });
    }

    private void render(EventDetailsDTO details, boolean force) {
        if (!force && details.getVersion() == renderedVersion) {
            return;
        }
        renderedVersion = details.getVersion();
        EventDTO meta = details.getMeta();
        double scroll = getVvalue();

        syncOptionCombos(meta);
        body.getChildren().setAll(buildHeader(meta), buildActionBar(meta));
        if ("LMSR".equals(meta.getMethod())) {
            buildLmsrSection(details);
        } else {
            buildOrderBookSection(details);
        }
        body.getChildren().add(buildChartSection(details));
        setVvalue(scroll);
    }

    private void syncOptionCombos(EventDTO meta) {
        List<String> options = meta.getOptionNames();
        if (!buyOption.getItems().equals(options)) {
            buyOption.getItems().setAll(options);
            buyOption.getSelectionModel().selectFirst();
            orderOption.getItems().setAll(options);
            orderOption.getSelectionModel().selectFirst();
            winnerCombo.getItems().setAll(options);
            winnerCombo.getSelectionModel().selectFirst();
        }
    }

    // ---- Header ----

    private Region buildHeader(EventDTO meta) {
        Label name = new Label("#" + meta.getId() + "   " + meta.getName());
        name.getStyleClass().add("details-title");
        Label description = new Label(meta.getDescription());
        description.getStyleClass().add("details-description");
        description.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(6);
        int row = 0;
        Fx.addInfo(grid, row++, "Trading method", meta.getMethod());
        Fx.addInfo(grid, row++, "Status", meta.getStatus());
        Fx.addInfo(grid, row++, "Commission", Format.percent(meta.getCommissionPercentage())
                + "  (" + meta.getCommissionType() + ")");
        Fx.addInfo(grid, row++, "Options", String.join("  /  ", meta.getOptionNames()));
        Fx.addInfo(grid, row++, "Market maker", meta.getMarketMakerName()
                + (isMe(meta.getMarketMakerName()) ? "  (you)" : ""));
        Fx.addInfo(grid, row++, "Event account", Format.money(meta.getAccountBalance()));

        return Fx.card(name, description, grid);
    }

    private boolean isMe(String name) {
        return name != null && name.equals(context.getCurrentUser());
    }

    // ---- Action bar (open / close) ----

    private Region buildActionBar(EventDTO meta) {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        boolean isMarketMaker = isMe(meta.getMarketMakerName());

        switch (meta.getStatus()) {
            case "Not started" -> {
                Button open = new Button("Open Event");
                open.setId("open-button");
                open.getStyleClass().add("primary-button");
                open.setDisable(!isMarketMaker);
                open.setOnAction(e -> openEvent(meta));
                bar.getChildren().addAll(open, mmHint(isMarketMaker, meta));
            }
            case "Active" -> {
                Button close = new Button("Close Event");
                close.setId("close-button");
                close.getStyleClass().add("danger-button");
                close.setDisable(!isMarketMaker);
                close.setOnAction(e -> closeEvent(meta, winnerCombo.getSelectionModel().getSelectedIndex() + 1,
                        winnerCombo.getValue()));
                winnerCombo.setDisable(!isMarketMaker);
                bar.getChildren().addAll(new Label("Winning option:"), winnerCombo, close, mmHint(isMarketMaker, meta));
            }
            default -> bar.getChildren().add(new Label("This event is closed."));
        }
        return bar;
    }

    private Label mmHint(boolean isMarketMaker, EventDTO meta) {
        if (isMarketMaker) {
            return new Label("");
        }
        Label hint = new Label("Only the market maker (" + meta.getMarketMakerName() + ") can do this.");
        hint.getStyleClass().add("hint");
        return hint;
    }

    // ---- LMSR section ----

    private void buildLmsrSection(EventDetailsDTO details) {
        EventDTO meta = details.getMeta();

        TableView<OptionStateDTO> options = new TableView<>();
        options.getColumns().add(Fx.col("Option", 200, OptionStateDTO::getOptionName));
        options.getColumns().add(Fx.col("Price (probability)", 150, o -> Format.probability(o.getPrice())));
        options.getColumns().add(Fx.col("Shares bought", 150, o -> Format.shares(o.getTotalShares())));
        options.getItems().setAll(details.getLmsrOptions());
        Fx.fixHeight(options, details.getLmsrOptions().size());

        VBox card = Fx.card(Fx.sectionTitle("Options and prices"), options);
        if ("Active".equals(meta.getStatus())) {
            card.getChildren().add(buildLmsrBuyRow(meta));
        }

        TableView<LmsrTradeDTO> trades = new TableView<>();
        trades.getColumns().add(Fx.col("User", 130, LmsrTradeDTO::getUserName));
        trades.getColumns().add(Fx.col("Option", 150, LmsrTradeDTO::getOptionName));
        trades.getColumns().add(Fx.col("Shares", 90, t -> Format.shares(t.getShares())));
        trades.getColumns().add(Fx.col("Cost", 110, t -> Format.money(t.getCost())));
        trades.getColumns().add(Fx.col("Commission", 110, t -> Format.money(t.getCommissionPaid())));
        trades.getColumns().add(Fx.col("Total paid", 110, t -> Format.money(t.getTotalPaid())));
        trades.getItems().setAll(details.getLmsrTradeHistory());
        trades.setPlaceholder(new Label("No trades yet."));
        trades.setPrefHeight(220);

        body.getChildren().addAll(card, Fx.card(Fx.sectionTitle("Trade history (newest first)"), trades));
    }

    private Region buildLmsrBuyRow(EventDTO meta) {
        Button buy = new Button("Buy");
        buy.setId("buy-button");
        buy.getStyleClass().add("primary-button");
        buy.setOnAction(e -> buyLmsr(meta, buyOption.getSelectionModel().getSelectedIndex() + 1, buyShares.getText()));
        buyShares.setOnAction(buy.getOnAction());

        FlowPane row = new FlowPane(10, 6, new Label("Buy:"), buyOption, buyShares, buy);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 0, 0));
        return row;
    }

    // ---- Order-book section ----

    private void buildOrderBookSection(EventDetailsDTO details) {
        EventDTO meta = details.getMeta();
        List<OrderBookOptionDTO> books = details.getBooks();

        if ("Active".equals(meta.getStatus())) {
            body.getChildren().add(Fx.card(Fx.sectionTitle("Submit an order"), buildOrderRow(meta)));
        }

        HBox booksRow = new HBox(14);
        for (int i = 0; i < books.size(); i++) {
            booksRow.getChildren().add(buildBookColumn(i + 1, books.get(i)));
        }
        body.getChildren().add(Fx.card(Fx.sectionTitle("Order books"), booksRow));
        body.getChildren().add(buildParticipantsCard(meta, details.getParticipants()));
    }

    private Region buildBookColumn(int number, OrderBookOptionDTO book) {
        Label title = new Label("Option " + number + ": " + book.getOptionName());
        title.getStyleClass().add("book-title");

        GridPane stats = new GridPane();
        stats.setHgap(14);
        stats.setVgap(4);
        String[] labels = {"LAST", "BID", "ASK", "MID", "SPREAD"};
        Double[] values = {book.getLast(), book.getBid(), book.getAsk(), book.getMid(), book.getSpread()};
        for (int i = 0; i < labels.length; i++) {
            Label key = new Label(labels[i]);
            key.getStyleClass().add("stat-key");
            Label value = new Label(Format.optionalPrice(values[i]));
            value.getStyleClass().add("stat-value");
            stats.add(key, i, 0);
            stats.add(value, i, 1);
        }

        TableView<OrderDTO> bids = ordersTable(book.getBuyOrders(), "No buy orders.");
        TableView<OrderDTO> asks = ordersTable(book.getSellOrders(), "No sell orders.");

        VBox bidsBox = new VBox(4, Fx.subTitle("Buy orders (bids)"), bids);
        VBox asksBox = new VBox(4, Fx.subTitle("Sell orders (asks)"), asks);

        VBox column = new VBox(8, title, stats, bidsBox, asksBox);
        column.getStyleClass().add("book-column");
        HBox.setHgrow(column, Priority.ALWAYS);
        column.setFillWidth(true);
        return column;
    }

    private TableView<OrderDTO> ordersTable(List<OrderDTO> orders, String emptyText) {
        TableView<OrderDTO> table = new TableView<>();
        table.getColumns().add(Fx.col("Price", 90, o -> Format.price(o.getPrice())));
        table.getColumns().add(Fx.col("Qty", 80, o -> Format.shares(o.getQuantity())));
        table.getColumns().add(Fx.col("User", 120, OrderDTO::getUserName));
        table.getItems().setAll(orders);
        table.setPlaceholder(new Label(emptyText));
        table.setPrefHeight(160);
        return table;
    }

    private Region buildParticipantsCard(EventDTO meta, List<ParticipantDTO> participants) {
        TableView<ParticipantDTO> table = new TableView<>();
        table.getColumns().add(Fx.col("User", 140, ParticipantDTO::getUserName));
        List<String> optionNames = meta.getOptionNames();
        for (int i = 0; i < optionNames.size(); i++) {
            final int index = i;
            table.getColumns().add(Fx.col(optionNames.get(i) + ": shares", 130,
                    p -> Format.shares(p.getShares().get(index))));
            table.getColumns().add(Fx.col(optionNames.get(i) + ": value", 130,
                    p -> Format.money(p.getValues().get(index))));
        }
        table.getItems().setAll(participants);
        table.setPlaceholder(new Label("No participants yet."));
        table.setPrefHeight(220);
        return Fx.card(Fx.sectionTitle("Participants"), table);
    }

    private Region buildOrderRow(EventDTO meta) {
        Button submit = new Button("Submit Order");
        submit.setId("order-button");
        submit.getStyleClass().add("primary-button");
        submit.setOnAction(e -> placeOrder(meta, orderOption.getSelectionModel().getSelectedIndex() + 1,
                orderSide.getValue(), orderPrice.getText(), orderQuantity.getText()));
        orderQuantity.setOnAction(submit.getOnAction());

        // FlowPane: on a narrow window the controls wrap to a second line instead of being squeezed.
        FlowPane row = new FlowPane(10, 6, new Label("Option:"), orderOption, new Label("Side:"), orderSide,
                new Label("Price:"), orderPrice, new Label("Qty:"), orderQuantity, submit);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---- Chart (bonus kept from exercise 2) ----

    private Region buildChartSection(EventDetailsDTO details) {
        CheckBox toggle = new CheckBox("Show price chart");
        toggle.setSelected(showChart);
        VBox card = Fx.card(toggle);
        if (showChart) {
            card.getChildren().add(buildPriceChart(details));
        }
        toggle.setOnAction(e -> {
            showChart = toggle.isSelected();
            render(details, true);
        });
        return card;
    }

    private Region buildPriceChart(EventDetailsDTO details) {
        boolean isLmsr = "LMSR".equals(details.getMeta().getMethod());
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trade #");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(isLmsr ? "Probability" : "Price ($)");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Price over time");
        chart.setPrefHeight(320);
        chart.setAnimated(false);

        List<String> optionNames = details.getMeta().getOptionNames();
        List<List<Double>> history = details.getPriceHistory();
        for (int i = 0; i < optionNames.size(); i++) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(optionNames.get(i));
            List<Double> points = history.get(i);
            for (int t = 0; t < points.size(); t++) {
                series.getData().add(new XYChart.Data<>(t + 1, points.get(t)));
            }
            chart.getData().add(series);
        }
        return chart;
    }

    // ---- Actions (asynchronous HTTP calls) ----

    private void openEvent(EventDTO meta) {
        ServerApi.openEvent(meta.getId(), (OpenResultDTO result) -> {
            Dialogs.info("Event opened", "Event '" + meta.getName() + "' is now active.\n"
                    + "You paid " + Format.money(result.getAmountPaid())
                    + " (balance: " + Format.money(result.getMarketMakerBalance()) + ").");
            context.refreshSoon();
            Animations.pop(this, context.isAnimationsEnabled());
        }, error -> Dialogs.error("Could not open event", error.getMessage()));
    }

    private void closeEvent(EventDTO meta, int winningOption, String winnerName) {
        if (!Dialogs.confirm("Close event",
                "Close '" + meta.getName() + "' with winning option '" + winnerName + "'?")) {
            return;
        }
        ServerApi.closeEvent(meta.getId(), winningOption, (CloseResultDTO result) -> {
            Dialogs.info("Event closed", "Winner: " + result.getWinnerName()
                    + "\nPaid to winners: " + Format.money(result.getTotalPaidToWinners())
                    + "\nCommission collected: " + Format.money(result.getCommissionCollected())
                    + "\nReturned to market maker: " + Format.money(result.getLeftoverToMarketMaker()));
            context.refreshSoon();
        }, error -> Dialogs.error("Could not close event", error.getMessage()));
    }

    private void buyLmsr(EventDTO meta, int optionNumber, String sharesText) {
        long shares = parseLong(sharesText, "number of shares");
        if (shares < 0) {
            return;
        }
        ServerApi.buyShares(meta.getId(), optionNumber, shares, (BuyResultDTO result) -> {
            StringBuilder message = new StringBuilder("Bought " + Format.shares(result.getShares())
                    + " shares of '" + result.getOptionName() + "'.\n"
                    + "Cost: " + Format.money(result.getCost())
                    + "  |  Commission: " + Format.money(result.getCommissionPaid())
                    + "\nYour balance: " + Format.money(result.getUserBalance()));
            if (result.getMessage() != null) {
                message.append("\n\n").append(result.getMessage());
            }
            buyShares.clear();
            Dialogs.info("Purchase complete", message.toString());
            context.refreshSoon();
        }, error -> Dialogs.error("Could not buy shares", error.getMessage()));
    }

    private void placeOrder(EventDTO meta, int optionNumber, String side, String priceText, String qtyText) {
        double price = parseDouble(priceText, "price");
        if (Double.isNaN(price)) {
            return;
        }
        long quantity = parseLong(qtyText, "quantity");
        if (quantity < 0) {
            return;
        }
        ServerApi.placeOrder(meta.getId(), optionNumber, side, price, quantity, (OrderResultDTO result) -> {
            orderQuantity.clear();
            Dialogs.info("Order submitted", describeOrderResult(result));
            context.refreshSoon();
        }, error -> Dialogs.error("Could not submit order", error.getMessage()));
    }

    private String describeOrderResult(OrderResultDTO result) {
        StringBuilder message = new StringBuilder();
        if (result.getFills().isEmpty()) {
            message.append("No immediate match. The order is resting in the book.\n");
        } else {
            message.append("Fills:\n");
            for (OrderFillDTO fill : result.getFills()) {
                message.append("  * ")
                        .append(fill.isMint() ? "MINT " : "")
                        .append(Format.shares(fill.getQuantity()))
                        .append(" @ ").append(Format.price(fill.getPrice()))
                        .append("  ").append(fill.getOptionName());
                if (fill.getBuyerName() != null) {
                    message.append("  (buyer: ").append(fill.getBuyerName());
                    if (fill.getSellerName() != null) {
                        message.append(", seller: ").append(fill.getSellerName());
                    }
                    message.append(")");
                }
                message.append("\n");
            }
        }
        if (result.getRestingQuantity() > 0) {
            message.append("Resting quantity: ").append(Format.shares(result.getRestingQuantity())).append("\n");
        }
        message.append("Your balance: ").append(Format.money(result.getUserBalance()));
        if (result.getMessage() != null) {
            message.append("\n\n").append(result.getMessage());
        }
        return message.toString();
    }

    // ---- Small helpers ----

    private long parseLong(String text, String what) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid input", "Please enter a whole number for the " + what + ".");
            return -1;
        }
    }

    private double parseDouble(String text, String what) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid input", "Please enter a valid number for the " + what + ".");
            return Double.NaN;
        }
    }
}
