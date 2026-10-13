package enginetest;

import engine.api.Engine;
import engine.api.EngineImpl;
import engine.exception.EngineOperationException;
import engine.exception.InvalidFileException;
import gm.dto.BuyResultDTO;
import gm.dto.CloseResultDTO;
import gm.dto.EventDetailsDTO;
import gm.dto.OpenResultDTO;
import gm.dto.OrderResultDTO;
import gm.dto.TransactionDTO;
import gm.dto.UploadResultDTO;
import gm.dto.UserDetailsDTO;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Engine-level acceptance tests for exercise 3 (no server needed).
 * Usage: {@code EngineTest <samplesDir>}
 */
public class EngineTest {

    private static int passed = 0;
    private static int failed = 0;
    private static Path samples;

    public static void main(String[] args) throws Exception {
        samples = Path.of(args.length > 0 ? args[0] : "samples");

        loginAndLogout();
        uploadAccumulatesAndSetsMarketMaker();
        uploadIsAllOrNothing();
        fundsOpenBuyAndLedger();
        orderBookTradeAndClose();
        loadFundsValidation();

        System.out.println();
        System.out.println("Engine tests: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ tests

    private static void loginAndLogout() {
        section("login / logout");
        Engine engine = new EngineImpl();
        long v0 = engine.getVersion();
        check("login returns the trimmed name", engine.login("  Guy ").getUserName().equals("Guy"));
        check("login bumps the version", engine.getVersion() > v0);
        check("the user is registered", engine.isUserRegistered("Guy"));
        check("same name while connected is rejected",
                fails(() -> engine.login("Guy"), "already connected"));
        check("another name is fine", engine.login("Tomer").getUserName().equals("Tomer"));
        check("empty name is rejected", fails(() -> engine.login("   "), "cannot be empty"));
        check("too long name is rejected", fails(() -> engine.login("x".repeat(41)), "too long"));

        engine.loadFunds("Guy", 250);
        engine.logout("Guy");
        check("logout marks the user offline", !engine.getUserDetails("Guy").isConnected());
        check("the name is free after logout", engine.login("Guy").getUserName().equals("Guy"));
        check("balance survives logout + login", engine.getUserDetails("Guy").getBalance() == 250.0);
        check("users list has 2 users", engine.getUsers().getUsers().size() == 2);
    }

    private static void uploadAccumulatesAndSetsMarketMaker() throws Exception {
        section("upload: accumulate, uploader = market maker, unique names");
        Engine engine = new EngineImpl();
        engine.login("Guy");
        engine.login("Tomer");

        long v0 = engine.getVersion();
        UploadResultDTO first = engine.uploadEvents(open("multiple.xml"), "Guy");
        check("3 events added from multiple.xml", first.getEventsAdded() == 3);
        check("version bumped by upload", engine.getVersion() > v0);
        check("ids are 1..3", engine.getEvents().getEvents().get(0).getId() == 1
                && engine.getEvents().getEvents().get(2).getId() == 3);
        check("uploader is the market maker of every event",
                engine.getEvents().getEvents().stream().allMatch(e -> e.getMarketMakerName().equals("Guy")));
        check("Guy's MM list is [1, 2, 3]", engine.getUserDetails("Guy").getMarketMakerEventIds().equals(List.of(1, 2, 3)));

        long v1 = engine.getVersion();
        check("same file again is rejected (names exist)",
                fails(() -> engine.uploadEvents(open("multiple.xml"), "Tomer"), "already exists"));
        check("rejected upload changes nothing", engine.getEvents().getEvents().size() == 3 && engine.getVersion() == v1);

        UploadResultDTO second = engine.uploadEvents(open("small.xml"), "Tomer");
        check("second file accumulates (id 4)", second.getEventsAdded() == 1
                && engine.getEvents().getEvents().get(3).getId() == 4);
        check("Tomer is MM of event 4", engine.getEvents().getEvents().get(3).getMarketMakerName().equals("Tomer"));
        check("Guy's MM list unchanged", engine.getUserDetails("Guy").getMarketMakerEventIds().equals(List.of(1, 2, 3)));

        String caseDup = xmlWithEvents(event("world CAP winner", 10, "A", "B", LMSR));
        check("event names are unique ignoring case",
                fails(() -> engine.uploadEvents(stream(caseDup), "Tomer"), "already exists"));
    }

    private static void uploadIsAllOrNothing() throws Exception {
        section("upload validation (all or nothing)");
        Engine engine = new EngineImpl();
        engine.login("Guy");

        String badCommission = xmlWithEvents(event("Good one", 10, "A", "B", LMSR), event("Bad one", 95, "A", "B", LMSR));
        check("commission 95 is rejected", fails(() -> engine.uploadEvents(stream(badCommission), "Guy"), "commission"));
        check("nothing was added", engine.getEvents().getEvents().isEmpty());

        String dupInFile = xmlWithEvents(event("Twice", 10, "A", "B", LMSR), event("twice", 10, "A", "B", LMSR));
        check("duplicate name inside the file is rejected",
                fails(() -> engine.uploadEvents(stream(dupInFile), "Guy"), "more than once"));

        String sameOptions = xmlWithEvents(event("Same options", 10, "Yes", "yes", LMSR));
        check("two identical options are rejected",
                fails(() -> engine.uploadEvents(stream(sameOptions), "Guy"), "option"));

        String badB = xmlWithEvents(event("Bad b", 10, "A", "B", "<GM-LMSR><b>0</b></GM-LMSR>"));
        check("LMSR b must be positive", fails(() -> engine.uploadEvents(stream(badB), "Guy"), "b"));

        String badD = xmlWithEvents(event("Bad d", 10, "A", "B", "<GM-order-book allow-mint=\"true\" initial=\"10\" d=\"0\"/>"));
        check("order book d must be positive", fails(() -> engine.uploadEvents(stream(badD), "Guy"), "d"));

        String notXml = "this is not xml at all";
        check("garbage is rejected as an invalid file", fails(() -> engine.uploadEvents(stream(notXml), "Guy"), null));

        check("still nothing added", engine.getEvents().getEvents().isEmpty());

        String ok = xmlWithEvents(event("Fine", 0, "A", "B", LMSR));
        check("a valid file after failures is accepted", engine.uploadEvents(stream(ok), "Guy").getEventsAdded() == 1);
    }

    private static void fundsOpenBuyAndLedger() throws Exception {
        section("load funds, open LMSR, buy, ledger");
        Engine engine = new EngineImpl();
        engine.login("Guy");
        engine.login("Tomer");
        engine.uploadEvents(open("multiple.xml"), "Guy");
        int lmsrId = 3; // "Will it rain tomorrow ?", b = 200

        check("new users start with balance 0", engine.getUserDetails("Guy").getBalance() == 0.0);
        check("cannot open without funds", fails(() -> engine.openEvent(lmsrId, "Guy"), "enough money"));
        check("non market maker cannot open", fails(() -> engine.openEvent(lmsrId, "Tomer"), "market maker"));

        check("load funds returns the new balance", engine.loadFunds("Guy", 500).getNewBalance() == 500.0);
        UserDetailsDTO guy = engine.getUserDetails("Guy");
        check("ledger has one 'Load funds' line", guy.getTransactions().size() == 1
                && guy.getTransactions().get(0).getType().equals("Load funds")
                && guy.getTransactions().get(0).getAmount() == 500.0);
        check("total loaded is tracked", guy.getTotalLoaded() == 500.0);

        OpenResultDTO opened = engine.openEvent(lmsrId, "Guy");
        check("LMSR subsidy is b*ln(2) = 138.63", near(opened.getAmountPaid(), 138.63));
        check("MM balance after open", near(opened.getMarketMakerBalance(), 361.37));
        check("event is active", engine.getEventDetails(lmsrId).getMeta().getStatus().equals("Active"));
        engine.loadFunds("Tomer", 100);
        long v0 = engine.getVersion();
        BuyResultDTO buy = engine.buyLmsrShares(lmsrId, "Tomer", 1, 10);
        check("buy returns the option name", buy.getOptionName().equals("Yes"));
        check("buy costs about 5.06", near(buy.getTotalPaid(), 5.06));
        check("version bumped by buy", engine.getVersion() > v0);
        UserDetailsDTO tomer = engine.getUserDetails("Tomer");
        check("Tomer has 1 participation", tomer.getParticipations().size() == 1);
        List<TransactionDTO> ledger = tomer.getTransactions();
        check("Tomer's ledger: load then buy", ledger.size() == 2 && ledger.get(1).getType().equals("Buy")
                && near(ledger.get(1).getAmount(), -5.06) && near(ledger.get(1).getBalanceAfter(), 94.94));
        check("balance history grows", tomer.getBalanceHistory().size() == 3);

        check("bad option number is rejected", fails(() -> engine.buyLmsrShares(lmsrId, "Tomer", 3, 1), "option"));
        check("zero shares is rejected", fails(() -> engine.buyLmsrShares(lmsrId, "Tomer", 1, 0), "shares"));
        check("unknown event is rejected", fails(() -> engine.getEventDetails(99), "99"));

        // Spec: a purchase that drives the balance negative is allowed, the user is told,
        // and from then on the user is blocked for every action (including loading funds).
        engine.login("Dana");
        BuyResultDTO overdraft = engine.buyLmsrShares(lmsrId, "Dana", 1, 5);
        check("buy with no money goes through and blocks the user",
                overdraft.isUserBlocked() && overdraft.getUserBalance() < 0 && engine.getUserDetails("Dana").isBlocked());
        check("blocked user cannot buy again", fails(() -> engine.buyLmsrShares(lmsrId, "Dana", 1, 1), "blocked"));
        check("blocked user cannot load funds", fails(() -> engine.loadFunds("Dana", 50), "blocked"));
        check("blocked user cannot upload", fails(() -> engine.uploadEvents(open("small.xml"), "Dana"), "blocked"));

    }

    private static void orderBookTradeAndClose() throws Exception {
        section("order book: open, sell (mint), buy, close, payouts");
        Engine engine = new EngineImpl();
        engine.login("Guy");
        engine.login("Tomer");
        engine.uploadEvents(open("multiple.xml"), "Guy");
        int obId = 2; // "World Cap Winner": allow-mint, initial 100, commission 15% on-close
        engine.loadFunds("Guy", 500);
        engine.loadFunds("Tomer", 300);

        check("cannot place an order before the event is open",
                fails(() -> engine.placeOrder(obId, "Tomer", 1, "Buy", 0.6, 4), "Not started"));
        OpenResultDTO opened = engine.openEvent(obId, "Guy");
        check("MM pays the initial amount", opened.getAmountPaid() == 100.0);

        OrderResultDTO sell = engine.placeOrder(obId, "Guy", 1, "Sell", 0.60, 10);
        check("sell order rests in the book", sell.getFills().isEmpty() && sell.getRestingQuantity() == 10);
        EventDetailsDTO details = engine.getEventDetails(obId);
        check("book shows the resting ask", details.getBooks().get(0).getSellOrders().size() == 1);

        OrderResultDTO buy = engine.placeOrder(obId, "Tomer", 1, "BUY", 0.60, 4);
        check("buy order fills 4 shares", buy.getFills().size() == 1 && buy.getFills().get(0).getQuantity() == 4);
        check("remaining ask is 6", engine.getEventDetails(obId).getBooks().get(0).getSellOrders().get(0).getQuantity() == 6);
        check("bad side is rejected", fails(() -> engine.placeOrder(obId, "Tomer", 1, "Hold", 0.6, 1), "side"));
        check("price outside (0,1) is rejected", fails(() -> engine.placeOrder(obId, "Tomer", 1, "Buy", 1.5, 1), "price"));

        check("non MM cannot close", fails(() -> engine.closeEvent(obId, 1, "Tomer"), "market maker"));
        CloseResultDTO closed = engine.closeEvent(obId, 1, "Guy");
        check("winner is Argentina", closed.getWinnerName().equals("Argentina"));
        check("15% commission collected", near(closed.getCommissionCollected(), 15.0));
        check("85 paid to winners", near(closed.getTotalPaidToWinners(), 85.0));
        check("event is closed", engine.getEventDetails(obId).getMeta().getStatus().equals("Closed"));
        check("closing twice is rejected", fails(() -> engine.closeEvent(obId, 1, "Guy"), "Closed"));
        check("no trading after close", fails(() -> engine.placeOrder(obId, "Tomer", 1, "Buy", 0.6, 1), "Closed"));

        UserDetailsDTO tomer = engine.getUserDetails("Tomer");
        boolean payoutLine = tomer.getTransactions().stream().anyMatch(t -> t.getType().equals("Payout"));
        check("Tomer's ledger has a payout line", payoutLine);
        UserDetailsDTO guy = engine.getUserDetails("Guy");
        boolean commissionLine = guy.getTransactions().stream().anyMatch(t -> t.getType().equals("Event closed"));
        check("Guy's ledger has the close line (commission + leftover)", commissionLine);
    }

    private static void loadFundsValidation() {
        section("load funds validation");
        Engine engine = new EngineImpl();
        engine.login("Guy");
        check("zero is rejected", fails(() -> engine.loadFunds("Guy", 0), "positive"));
        check("negative is rejected", fails(() -> engine.loadFunds("Guy", -5), "positive"));
        check("NaN is rejected", fails(() -> engine.loadFunds("Guy", Double.NaN), null));
        check("amount is rounded to cents", engine.loadFunds("Guy", 10.004).getNewBalance() == 10.0);
        check("unknown user is rejected", fails(() -> engine.loadFunds("Nobody", 10), null));
    }

    // ---------------------------------------------------------------- helpers

    private static final String LMSR = "<GM-LMSR><b>100</b></GM-LMSR>";

    private static String event(String name, int commission, String opt1, String opt2, String method) {
        return "<GM-event name=\"" + name + "\"><description>d</description>"
                + "<commission type=\"on-close\">" + commission + "</commission>"
                + "<GM-options><GM-option>" + opt1 + "</GM-option><GM-option>" + opt2 + "</GM-option></GM-options>"
                + "<GM-method>" + method + "</GM-method></GM-event>";
    }

    private static String xmlWithEvents(String... events) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Guess-Market><GM-events>"
                + String.join("", events) + "</GM-events></Guess-Market>";
    }

    private static InputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream open(String sample) {
        try {
            return new FileInputStream(samples.resolve(sample).toFile());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 0.011;
    }

    /** @return true if the action throws an engine/file exception whose message contains {@code expected} (case-insensitive). */
    private static boolean fails(Runnable action, String expected) {
        try {
            action.run();
            return false;
        } catch (EngineOperationException | InvalidFileException e) {
            if (expected == null) {
                return true;
            }
            boolean ok = e.getMessage() != null && e.getMessage().toLowerCase().contains(expected.toLowerCase());
            if (!ok) {
                System.out.println("      (message was: " + e.getMessage() + ")");
            }
            return ok;
        }
    }

    private static void section(String name) {
        System.out.println();
        System.out.println("## " + name);
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + what);
        } else {
            failed++;
            System.out.println("  FAIL " + what);
        }
    }
}

