package client.account;

import client.app.ClientContext;
import client.app.Poller;
import client.common.Animations;
import client.common.Dialogs;
import client.common.Format;
import client.common.Fx;
import client.http.ServerApi;
import gm.dto.FundsResultDTO;
import gm.dto.LmsrTradeDTO;
import gm.dto.TransactionDTO;
import gm.dto.UserDetailsDTO;
import gm.dto.UserParticipationDTO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The private part of the Account tab: the logged-in user's own details,
 * balance with the "load funds" action, participations with a per-event
 * drill-down (reused from exercise 2), the full account ledger (one line per
 * action), and the balance-over-time chart.
 * <p>
 * Redrawn only when the server's state version changed; the selected
 * participation row and the amount being typed survive redraws.
 */
public class MyAccountPane extends ScrollPane {

    private final ClientContext context;
    private final VBox body = new VBox(14);
    private long renderedVersion = -1;
    private boolean showChart;
    private int selectedParticipationEventId = -1;

    private final TextField amountField = new TextField();

    public MyAccountPane(ClientContext context) {
        this.context = context;
        body.setPadding(new Insets(16));
        body.getStyleClass().add("details-body");
        setContent(body);
        setFitToWidth(true);
        getStyleClass().add("details-pane");
        amountField.setPromptText("amount, e.g. 1000");
        amountField.setPrefWidth(140);
        body.getChildren().setAll(Fx.placeholder("Loading your account..."));
    }

    public Poller.Task<UserDetailsDTO> pollTask() {
        return new Poller.Task<UserDetailsDTO>() {
            @Override
            public UserDetailsDTO fetch() {
                return ServerApi.getMyDetailsSync();
            }

            @Override
            public void apply(UserDetailsDTO value) {
                render(value, false);
            }
        };
    }

    private void render(UserDetailsDTO details, boolean force) {
        if (!force && details.getVersion() == renderedVersion) {
            return;
        }
        renderedVersion = details.getVersion();
        double scroll = getVvalue();

        body.getChildren().setAll(buildHeader(details));
        if (details.isBlocked()) {
            body.getChildren().add(buildBlockedBanner());
        }
        body.getChildren().add(buildParticipationsCard(details));
        body.getChildren().add(buildLedgerCard(details));
        body.getChildren().add(buildChartSection(details));
        setVvalue(scroll);
    }

    // ---- Header: details + balance + load funds ----

    private Region buildHeader(UserDetailsDTO details) {
        Label name = new Label(details.getName());
        name.getStyleClass().add("details-title");

        Label balance = new Label(Format.money(details.getBalance()));
        balance.getStyleClass().add("balance-big");

        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(6);
        int row = 0;
        Fx.addInfo(grid, row++, "Total loaded", Format.money(details.getTotalLoaded()));
        Fx.addInfo(grid, row++, "Blocked", details.isBlocked() ? "Yes" : "No");
        String mmOf = details.getMarketMakerEventIds().isEmpty() ? "-"
                : details.getMarketMakerEventIds().stream().map(id -> "#" + id).collect(Collectors.joining(", "));
        Fx.addInfo(grid, row++, "Market maker of", mmOf);
        Fx.addInfo(grid, row++, "Participations", String.valueOf(details.getParticipations().size()));

        Button load = new Button("Load Funds");
        load.setId("funds-button");
        amountField.setId("funds-amount");
        load.getStyleClass().add("primary-button");
        load.setOnAction(e -> loadFunds());
        amountField.setOnAction(e -> loadFunds());
        FlowPane fundsRow = new FlowPane(10, 6, new Label("Load money into your account:"), amountField, load);
        fundsRow.setAlignment(Pos.CENTER_LEFT);

        VBox balanceBox = new VBox(4, new Label("Account balance"), balance, fundsRow);
        return Fx.card(name, balanceBox, grid);
    }

    private void loadFunds() {
        String text = amountField.getText() == null ? "" : amountField.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            Dialogs.error("Invalid amount", "Please enter a positive number to load.");
            return;
        }
        if (amount <= 0) {
            Dialogs.error("Invalid amount", "The amount to load must be positive.");
            return;
        }
        ServerApi.loadFunds(amount, (FundsResultDTO result) -> {
            amountField.clear();
            Dialogs.info("Funds loaded", Format.money(result.getAmountLoaded()) + " were added to your account.\n"
                    + "New balance: " + Format.money(result.getNewBalance()));
            context.refreshSoon();
            Animations.pop(this, context.isAnimationsEnabled());
        }, error -> Dialogs.error("Could not load funds", error.getMessage()));
    }

    private Region buildBlockedBanner() {
        Label banner = new Label("You are BLOCKED: your balance went negative and you can no longer act.");
        banner.getStyleClass().add("blocked-banner");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setPadding(new Insets(10));
        return banner;
    }

    // ---- Participations (reused from exercise 2) ----

    private Region buildParticipationsCard(UserDetailsDTO details) {
        TableView<UserParticipationDTO> table = new TableView<>();
        table.getColumns().add(Fx.col("Event", 190, p -> "#" + p.getEventId() + "  " + p.getEventName()));
        table.getColumns().add(Fx.col("Method", 100, UserParticipationDTO::getMethod));
        table.getColumns().add(Fx.col("Status", 100, UserParticipationDTO::getStatus));
        table.getColumns().add(Fx.col("Role", 110, p -> p.isMarketMaker() ? "Market maker" : "Trader"));
        table.getColumns().add(Fx.col("Involvement", 190, this::involvementSummary));
        table.getColumns().add(Fx.col("Profit / Loss", 120,
                p -> p.getProfitLoss() == null ? "-" : Format.money(p.getProfitLoss())));
        table.getItems().setAll(details.getParticipations());
        table.setPlaceholder(new Label("You have not participated in any event yet."));
        table.setPrefHeight(200);

        VBox detailHolder = new VBox();
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                selectedParticipationEventId = -1;
                detailHolder.getChildren().clear();
            } else {
                selectedParticipationEventId = newValue.getEventId();
                detailHolder.getChildren().setAll(buildParticipationDetail(newValue));
            }
        });
        for (UserParticipationDTO p : table.getItems()) {
            if (p.getEventId() == selectedParticipationEventId) {
                table.getSelectionModel().select(p);
                break;
            }
        }

        return Fx.card(Fx.sectionTitle("Events you participate in (select a row for details)"), table, detailHolder);
    }

    private String involvementSummary(UserParticipationDTO p) {
        if ("LMSR".equals(p.getMethod())) {
            return p.getLmsrTrades().size() + " purchase(s)";
        }
        StringBuilder sb = new StringBuilder();
        List<String> names = p.getOptionNames();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names.get(i)).append("=").append(Format.shares(p.getHoldings().get(i)));
        }
        return sb.toString();
    }

    private Region buildParticipationDetail(UserParticipationDTO p) {
        if ("LMSR".equals(p.getMethod())) {
            TableView<LmsrTradeDTO> trades = new TableView<>();
            trades.getColumns().add(Fx.col("Option", 160, LmsrTradeDTO::getOptionName));
            trades.getColumns().add(Fx.col("Shares", 90, t -> Format.shares(t.getShares())));
            trades.getColumns().add(Fx.col("Cost", 110, t -> Format.money(t.getCost())));
            trades.getColumns().add(Fx.col("Commission", 110, t -> Format.money(t.getCommissionPaid())));
            trades.getColumns().add(Fx.col("Total", 110, t -> Format.money(t.getTotalPaid())));
            trades.getItems().setAll(p.getLmsrTrades());
            trades.setPlaceholder(new Label("No purchases."));
            trades.setPrefHeight(180);
            VBox box = new VBox(6, Fx.subTitle("LMSR purchases in '" + p.getEventName() + "'"),
                    trades, winnerLabel(p));
            box.setPadding(new Insets(8, 0, 0, 0));
            return box;
        }

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(6);
        grid.add(new Label("Option"), 0, 0);
        grid.add(new Label("Shares held"), 1, 0);
        grid.add(new Label("Paid"), 2, 0);
        List<String> names = p.getOptionNames();
        for (int i = 0; i < names.size(); i++) {
            grid.add(new Label(names.get(i)), 0, i + 1);
            grid.add(new Label(Format.shares(p.getHoldings().get(i))), 1, i + 1);
            grid.add(new Label(Format.money(p.getPaidPerOption().get(i))), 2, i + 1);
        }
        VBox box = new VBox(6, Fx.subTitle("Order-book holdings in '" + p.getEventName() + "'"), grid, winnerLabel(p));
        if (p.getProfitLoss() != null) {
            box.getChildren().add(new Label("Realised profit / loss: " + Format.money(p.getProfitLoss())));
        }
        box.setPadding(new Insets(8, 0, 0, 0));
        return box;
    }

    private Label winnerLabel(UserParticipationDTO p) {
        if (p.getWinnerName() == null) {
            return new Label("");
        }
        return new Label("Winning option: " + p.getWinnerName());
    }

    // ---- Ledger (new in exercise 3) ----

    private Region buildLedgerCard(UserDetailsDTO details) {
        TableView<TransactionDTO> table = new TableView<>();
        table.setId("ledger-table");
        table.getColumns().add(Fx.col("#", 50, t -> String.valueOf(t.getNumber())));
        table.getColumns().add(Fx.col("Type", 120, TransactionDTO::getType));
        table.getColumns().add(Fx.col("Description", 460, TransactionDTO::getDescription));
        table.getColumns().add(Fx.col("Amount", 110, t -> signedMoney(t.getAmount())));
        table.getColumns().add(Fx.col("Balance after", 120, t -> Format.money(t.getBalanceAfter())));
        List<TransactionDTO> newestFirst = new java.util.ArrayList<>(details.getTransactions());
        java.util.Collections.reverse(newestFirst);
        table.getItems().setAll(newestFirst);
        table.setPlaceholder(new Label("No account activity yet. Load funds to get started."));
        table.setPrefHeight(240);
        return Fx.card(Fx.sectionTitle("Account activity (newest first, one line per action)"), table);
    }

    private String signedMoney(double amount) {
        return (amount >= 0 ? "+" : "-") + Format.money(Math.abs(amount));
    }

    // ---- Chart (kept from exercise 2) ----

    private Region buildChartSection(UserDetailsDTO details) {
        CheckBox toggle = new CheckBox("Show balance-over-time chart");
        toggle.setSelected(showChart);
        VBox card = Fx.card(toggle);
        if (showChart) {
            card.getChildren().add(buildBalanceChart(details));
        }
        toggle.setOnAction(e -> {
            showChart = toggle.isSelected();
            render(details, true);
        });
        return card;
    }

    private Region buildBalanceChart(UserDetailsDTO details) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Change #");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Balance ($)");
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Balance over time");
        chart.setPrefHeight(300);
        chart.setAnimated(false);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(details.getName());
        List<Double> history = details.getBalanceHistory();
        for (int i = 0; i < history.size(); i++) {
            series.getData().add(new XYChart.Data<>(i, history.get(i)));
        }
        chart.getData().add(series);
        return chart;
    }
}
