package uitest;

import client.account.AccountView;
import client.app.GuessMarketClientApp;
import gm.dto.EventDTO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Drives the real JavaFX client against a running server without touching the
 * mouse or keyboard: the window is placed off-screen, controls are found by id,
 * and scene snapshots are written as PNG files for visual inspection.
 * <p>
 * Usage: {@code UiDriver <userName> <scenario: mm|trader> <outDir> <samplesDir>}
 */
public class UiDriver {

    private static Stage stage;
    private static Scene scene;
    private static File outDir;
    private static File samplesDir;
    private static int shotCounter = 0;
    private static final List<String> dialogs = new ArrayList<>();
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        String user = args[0];
        String scenario = args[1];
        outDir = new File(args[2]);
        samplesDir = new File(args[3]);
        outDir.mkdirs();

        CountDownLatch started = new CountDownLatch(1);
        Platform.setImplicitExit(false);
        Platform.startup(() -> {
            try {
                stage = new Stage();
                new GuessMarketClientApp().start(stage);
                scene = stage.getScene();
                stage.setX(-4000); // keep the test window off the visible desktop
                stage.setY(0);
            } finally {
                started.countDown();
            }
        });
        started.await(20, TimeUnit.SECONDS);
        Thread.sleep(800);

        try {
            if ("mm".equals(scenario)) {
                marketMakerScenario(user);
            } else {
                traderScenario(user);
            }
        } catch (Throwable t) {
            failures++;
            System.out.println("FAIL: " + t);
            t.printStackTrace(System.out);
            snapshot("failure");
        }

        System.out.println("Dialogs seen: " + dialogs.size());
        for (String d : dialogs) {
            System.out.println("  [dialog] " + d.replace("\n", " | "));
        }
        System.out.println(failures == 0 ? "UI DRIVER OK" : "UI DRIVER FAILED (" + failures + ")");
        // Same path as the user clicking the window's X: the app logs out and exits the platform.
        Platform.runLater(() -> stage.getOnCloseRequest()
                .handle(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        Thread.sleep(1500);
        System.exit(failures == 0 ? 0 : 1);
    }

    // ================= scenarios =================

    private static void marketMakerScenario(String user) throws Exception {
        snapshot("login-screen");
        login(user);
        waitFor(() -> lookup("#main-tabs") != null, 10_000, "main screen");
        Thread.sleep(1500);
        snapshot("events-empty");

        // Account tab: upload a file, load funds.
        selectTab(1);
        Thread.sleep(300);
        snapshot("account-before-upload");
        fx(() -> accountView().upload(new File(samplesDir, "multiple.xml")));
        waitDialogAndClose(10_000);
        waitFor(() -> text("#upload-path").contains("multiple.xml"), 5_000, "upload path shown");
        setText("#funds-amount", "500");
        fire("#funds-button");
        waitDialogAndClose(10_000);
        waitFor(() -> tableRows("#ledger-table") >= 1, 5_000, "ledger has the load-funds line");
        Thread.sleep(1200);
        snapshot("account-after-upload-and-funds");

        // Events tab: open the LMSR event, buy shares.
        selectTab(0);
        waitFor(() -> listSize("#event-list") >= 3, 10_000, "events listed");
        Thread.sleep(500);
        selectEventByName("Will it rain tomorrow ?");
        waitFor(() -> lookup("#open-button") != null, 5_000, "open button for LMSR event");
        snapshot("event-lmsr-not-started");
        fire("#open-button");
        waitDialogAndClose(10_000);
        waitFor(() -> lookup("#buy-button") != null, 8_000, "LMSR event active");
        Thread.sleep(600);
        selectCombo("#buy-option", 0);
        setText("#buy-shares", "10");
        fire("#buy-button");
        waitDialogAndClose(10_000);
        Thread.sleep(1500);
        snapshot("event-lmsr-after-buy");

        // Order book event: open it and place a sell order.
        selectEventByName("World Cap Winner");
        waitFor(() -> lookup("#open-button") != null, 5_000, "open button for order book event");
        fire("#open-button");
        waitDialogAndClose(10_000);
        waitFor(() -> lookup("#order-button") != null, 8_000, "order book event active");
        Thread.sleep(600);
        selectCombo("#order-option", 0);
        selectCombo("#order-side", 1); // Sell (allow-mint events let the MM sell short)
        setText("#order-price", "0.60");
        setText("#order-quantity", "10");
        fire("#order-button");
        waitDialogAndClose(10_000);
        Thread.sleep(1500);
        snapshot("event-orderbook-after-sell-order");

        // Chat.
        selectTab(2);
        setText("#chat-input", "Hello from " + user + ", events are open!");
        fire("#chat-send");
        waitFor(() -> listSize("#chat-lines") >= 1, 5_000, "chat line appears");
        Thread.sleep(800);
        snapshot("chat");

        // Give the other client time to trade, then look at the results.
        System.out.println("Waiting for the second client to trade...");
        waitFor(() -> listSize("#chat-lines") >= 2, 90_000, "second user's chat message");
        Thread.sleep(2500);
        snapshot("chat-both");
        selectTab(0);
        selectEventByName("World Cap Winner");
        Thread.sleep(2500);
        snapshot("event-orderbook-after-fill");
        selectTab(1);
        Thread.sleep(2000);
        snapshot("account-after-trades");

        // Resize check: smaller window, every tab.
        fx(() -> { stage.setWidth(900); stage.setHeight(600); });
        Thread.sleep(1200);
        snapshot("small-account");
        selectTab(0);
        Thread.sleep(800);
        snapshot("small-events");
        fx(() -> { stage.setWidth(1240); stage.setHeight(800); });
        Thread.sleep(800);

        // Close the order-book event with option 1 as the winner.
        selectEventByName("World Cap Winner");
        waitFor(() -> lookup("#close-button") != null, 5_000, "close button");
        selectCombo("#winner-combo", 0);
        fire("#close-button");
        waitDialogAndClose(10_000); // confirmation
        waitDialogAndClose(10_000); // result
        Thread.sleep(2000);
        snapshot("event-orderbook-closed");

        // Logout returns to the login screen.
        fire("#logout-button");
        waitFor(() -> lookup("#login-name") != null, 8_000, "back to login");
        Thread.sleep(500);
        snapshot("after-logout");
    }

    private static void traderScenario(String user) throws Exception {
        login(user);
        waitFor(() -> lookup("#main-tabs") != null, 10_000, "main screen");
        waitFor(() -> listSize("#event-list") >= 3, 90_000, "events uploaded by the other client");

        selectTab(1);
        setText("#funds-amount", "300");
        fire("#funds-button");
        waitDialogAndClose(10_000);
        Thread.sleep(1000);
        snapshot("trader-account");

        selectTab(0);
        selectEventByName("Will it rain tomorrow ?");
        waitFor(() -> lookup("#buy-button") != null, 90_000, "LMSR event opened by market maker");
        Thread.sleep(500);
        selectCombo("#buy-option", 1);
        setText("#buy-shares", "5");
        fire("#buy-button");
        waitDialogAndClose(10_000);
        Thread.sleep(1200);
        snapshot("trader-lmsr-after-buy");

        selectEventByName("World Cap Winner");
        waitFor(() -> lookup("#order-button") != null, 90_000, "order book event opened by market maker");
        Thread.sleep(500);
        selectCombo("#order-option", 0);
        selectCombo("#order-side", 0); // Buy
        setText("#order-price", "0.60");
        setText("#order-quantity", "4");
        fire("#order-button");
        waitDialogAndClose(10_000);
        Thread.sleep(1500);
        snapshot("trader-orderbook-after-buy-order");

        selectTab(2);
        waitFor(() -> listSize("#chat-lines") >= 1, 30_000, "market maker's chat line");
        setText("#chat-input", "Hi, " + user + " here. I just bought some shares.");
        fire("#chat-send");
        waitFor(() -> listSize("#chat-lines") >= 2, 5_000, "own chat line");
        Thread.sleep(800);
        snapshot("trader-chat");

        selectTab(1);
        Thread.sleep(1500);
        snapshot("trader-account-final");

        // Wait for the market maker to close the event, then look at the payout in the ledger.
        waitFor(() -> tableRows("#ledger-table") >= 4, 90_000, "payout or close line in ledger");
        Thread.sleep(2000);
        snapshot("trader-account-after-close");
    }

    // ================= helpers =================

    private static void login(String user) throws Exception {
        setText("#login-name", user);
        fire("#login-button");
    }

    private static Node lookup(String selector) {
        return scene.getRoot().lookup(selector);
    }

    private static Node lookupInAnyWindow(String selector) {
        for (Window w : Window.getWindows()) {
            if (w.getScene() != null) {
                Node n = w.getScene().getRoot().lookup(selector);
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    private static String text(String selector) {
        try {
            return callFx(() -> {
                Node n = lookup(selector);
                return n instanceof TextField ? ((TextField) n).getText() : "";
            });
        } catch (Exception e) {
            return "";
        }
    }

    private static void setText(String selector, String value) throws Exception {
        fx(() -> {
            Node n = lookup(selector);
            if (n == null) {
                throw new IllegalStateException("No node " + selector);
            }
            ((TextField) n).setText(value);
        });
    }

    private static void fire(String selector) throws Exception {
        Boolean ok = callFx(() -> {
            Node n = lookup(selector);
            return n != null && !n.isDisabled();
        });
        if (!ok) {
            throw new IllegalStateException("Button missing or disabled: " + selector);
        }
        // Fire without waiting: a handler may open a modal dialog (nested event loop)
        // that this driver closes afterwards.
        Platform.runLater(() -> ((Button) lookup(selector)).fire());
        Thread.sleep(250);
    }

    @SuppressWarnings("unchecked")
    private static void selectCombo(String selector, int index) throws Exception {
        fx(() -> ((ComboBox<Object>) lookup(selector)).getSelectionModel().select(index));
    }

    private static void selectTab(int index) throws Exception {
        fx(() -> ((TabPane) lookup("#main-tabs")).getSelectionModel().select(index));
        Thread.sleep(400);
    }

    @SuppressWarnings("unchecked")
    private static int listSize(String selector) {
        try {
            return callFx(() -> {
                Node n = lookup(selector);
                return n == null ? -1 : ((ListView<Object>) n).getItems().size();
            });
        } catch (Exception e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static int tableRows(String selector) {
        try {
            return callFx(() -> {
                Node n = lookup(selector);
                return n == null ? -1 : ((TableView<Object>) n).getItems().size();
            });
        } catch (Exception e) {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private static void selectEventByName(String name) throws Exception {
        fx(() -> {
            ListView<EventDTO> list = (ListView<EventDTO>) lookup("#event-list");
            for (EventDTO e : list.getItems()) {
                if (e.getName().equals(name)) {
                    list.getSelectionModel().select(e);
                    return;
                }
            }
            throw new IllegalStateException("No event named " + name);
        });
        Thread.sleep(1200);
    }

    private static AccountView accountView() {
        Node n = lookup("#upload-path");
        Parent p = n.getParent();
        while (p != null && !(p instanceof AccountView)) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("AccountView not found");
        }
        return (AccountView) p;
    }

    /** Waits for an Alert to appear, records its text, snapshots it, and closes it. */
    private static void waitDialogAndClose(long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Boolean closed = callFx(() -> {
                for (Window w : Window.getWindows()) {
                    if (w != stage && w.isShowing() && w.getScene() != null
                            && w.getScene().getRoot() instanceof DialogPane) {
                        DialogPane pane = (DialogPane) w.getScene().getRoot();
                        String title = w instanceof Stage ? ((Stage) w).getTitle() : "";
                        dialogs.add(title + ": " + pane.getContentText());
                        Node ok = pane.lookupButton(javafx.scene.control.ButtonType.OK);
                        if (ok instanceof Button) {
                            ((Button) ok).fire(); // also answers confirmation dialogs with OK
                        } else {
                            w.hide();
                        }
                        return true;
                    }
                }
                return false;
            });
            if (closed) {
                return;
            }
            Thread.sleep(150);
        }
        failures++;
        System.out.println("FAIL: expected a dialog within " + timeoutMs + " ms");
    }

    private static void waitFor(BooleanSupplier condition, long timeoutMs, String what) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Timed out waiting for: " + what);
    }

    private static void snapshot(String name) throws Exception {
        String file = String.format("%02d-%s.png", ++shotCounter, name);
        fx(() -> {
            WritableImage image = scene.snapshot(null);
            File target = new File(outDir, file);
            try {
                ImageIO.write(toBuffered(image), "png", target);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("snapshot " + file);
    }

    private static BufferedImage toBuffered(WritableImage image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }

    private static void fx(RunnableWithException r) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX thread did not respond");
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

    private static <T> T callFx(Callable<T> c) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        fx(() -> result.set(c.call()));
        return result.get();
    }

    @FunctionalInterface
    private interface RunnableWithException {
        void run() throws Exception;
    }
}

