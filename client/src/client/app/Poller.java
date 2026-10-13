package client.app;

import client.http.ApiException;
import client.http.Constants;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The client's "pull" heartbeat. Every {@link Constants#POLL_INTERVAL_MS} a
 * single daemon thread runs each registered {@link Task}: it fetches fresh data
 * from the server synchronously (allowed, because this is not the JavaFX
 * thread) and then hands the result to the JavaFX Application Thread, where the
 * task decides whether anything on screen must change.
 * <p>
 * Tasks compare the server's state version with the one they last rendered, so
 * an unchanged server costs nothing on screen: no flicker, no lost selection.
 */
public class Poller {

    /** One thing to keep fresh. {@code fetch} runs in the background, {@code apply} on the JavaFX thread. */
    public interface Task<T> {
        T fetch();

        void apply(T value);

        /** Return {@code false} to skip this tick (e.g. a details pane with nothing selected). */
        default boolean isActive() {
            return true;
        }
    }

    private final List<Task<?>> tasks = new CopyOnWriteArrayList<>();
    private final Consumer<ApiException> connectionListener;
    private ScheduledExecutorService executor;
    private volatile boolean running;

    /** @param connectionListener told (on the JavaFX thread) when a poll fails, or with {@code null} when it recovers. */
    public Poller(Consumer<ApiException> connectionListener) {
        this.connectionListener = connectionListener;
    }

    public void register(Task<?> task) {
        tasks.add(task);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "server-poller");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tick, 0, Constants.POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Runs one round immediately (in the background), e.g. right after the user performed an action. */
    public void pokeNow() {
        if (executor != null && running) {
            executor.execute(this::tick);
        }
    }

    private void tick() {
        if (!running) {
            return;
        }
        boolean anyFailure = false;
        for (Task<?> task : tasks) {
            if (!running) {
                return;
            }
            if (!task.isActive()) {
                continue;
            }
            try {
                runTask(task);
            } catch (ApiException e) {
                anyFailure = true;
                Platform.runLater(() -> connectionListener.accept(e));
                if (e.isUnauthorized()) {
                    return; // the session is gone; the listener takes the user back to the login screen
                }
            } catch (RuntimeException e) {
                anyFailure = true;
                Platform.runLater(() -> connectionListener.accept(new ApiException(0, e.getMessage())));
            }
        }
        if (!anyFailure) {
            Platform.runLater(() -> connectionListener.accept(null));
        }
    }

    private <T> void runTask(Task<T> task) {
        T value = task.fetch();
        Platform.runLater(() -> {
            if (running) {
                task.apply(value);
            }
        });
    }
}
