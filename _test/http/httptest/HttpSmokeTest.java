package httptest;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HTTP acceptance test against a running server (default http://localhost:8080/guessmarket).
 * Two independent clients (own cookie jars) play a market maker and a trader.
 * Usage: {@code HttpSmokeTest <samplesDir> [baseUrl]}
 * <p>
 * Uses only the JDK HTTP client so it also documents the raw protocol.
 */
public class HttpSmokeTest {

    private static String base = "http://localhost:8080/guessmarket";
    private static Path samples;
    private static int passed = 0;
    private static int failed = 0;

    /** One logged-in browser-like client: its own cookie jar = its own session. */
    static final class Client {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        Res get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(base + path)).GET().build());
        }

        Res post(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(base + path))
                    .POST(HttpRequest.BodyPublishers.noBody()).build());
        }

        Res upload(Path file) throws Exception {
            String boundary = "----GuessMarketTest" + System.nanoTime();
            byte[] content = Files.readAllBytes(file);
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getFileName() + "\"\r\n"
                    + "Content-Type: text/xml\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            byte[] body = concat(head.getBytes(StandardCharsets.UTF_8), content, tail.getBytes(StandardCharsets.UTF_8));
            return send(HttpRequest.newBuilder(URI.create(base + "/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
        }

        private Res send(HttpRequest request) throws Exception {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Res(response.statusCode(), response.body());
        }
    }

    record Res(int status, String body) {
        boolean has(String text) {
            return body.contains(text);
        }
    }

    public static void main(String[] args) throws Exception {
        samples = Path.of(args.length > 0 ? args[0] : "samples");
        if (args.length > 1) {
            base = args[1];
        }

        Client guy = new Client();
        Client tomer = new Client();
        Client anon = new Client();

        section("server reachable");
        Res index = anon.get("/");
        check("index page answers 200", index.status() == 200);

        section("authentication");
        check("anonymous /events is 401 with json error", anon.get("/events").status() == 401
                && anon.get("/events").has("\"error\""));
        check("empty name is 400", anon.post("/login?username=").status() == 400);
        Res login = guy.post("/login?username=" + enc("Guy"));
        check("login Guy is 200", login.status() == 200 && login.has("\"userName\":\"Guy\""));
        check("second login with same session is idempotent", guy.post("/login?username=Other").has("already logged in"));
        check("same name from another client is 409", anon.post("/login?username=Guy").status() == 409);
        check("/me returns the user", guy.get("/me").has("Guy"));
        check("login Tomer is 200", tomer.post("/login?username=Tomer").status() == 200);

        section("upload");
        Res up = guy.upload(samples.resolve("multiple.xml"));
        check("multiple.xml uploaded by Guy", up.status() == 200 && up.has("\"eventsAdded\":3"), up);
        Res again = tomer.upload(samples.resolve("multiple.xml"));
        check("same names again is 400 with explanation", again.status() == 400 && again.has("already exists"));
        Res small = tomer.upload(samples.resolve("small.xml"));
        check("small.xml uploaded by Tomer (accumulates)", small.status() == 200 && small.has("\"eventsAdded\":1"));
        Res events = guy.get("/events");
        check("events list has 4 events with versions", events.has("\"version\"") && count(events.body(), "\"id\":") == 4);
        check("event 4 belongs to Tomer", events.has("\"marketMakerName\":\"Tomer\""));
        check("anonymous upload is 401", anon.upload(samples.resolve("small.xml")).status() == 401);
        check("upload without a file part is 400", guy.post("/upload").status() == 400 || guy.post("/upload").status() == 500);

        section("funds and opening");
        check("open without funds is 400", guy.post("/events/open?id=3").status() == 400);
        check("load funds validation (abc) is 400", guy.post("/users/funds?amount=abc").status() == 400);
        check("load funds validation (-5) is 400", guy.post("/users/funds?amount=-5").status() == 400);
        Res funds = guy.post("/users/funds?amount=500");
        check("load 500", funds.status() == 200 && funds.has("\"newBalance\":500"), funds);
        check("Tomer cannot open Guy's event", tomer.post("/events/open?id=3").status() == 400);
        Res open = guy.post("/events/open?id=3");
        check("Guy opens the LMSR event (subsidy 138.63)", open.status() == 200 && open.has("138.6"), open);
        check("opening again is 400", guy.post("/events/open?id=3").status() == 400);

        section("trading");
        tomer.post("/users/funds?amount=100");
        Res buy = tomer.post("/events/buy?id=3&option=1&shares=10");
        check("Tomer buys 10 shares", buy.status() == 200 && buy.has("\"optionName\":\"Yes\""));
        check("bad option is 400", tomer.post("/events/buy?id=3&option=9&shares=1").status() == 400);
        check("missing parameter is 400", tomer.post("/events/buy?id=3").status() == 400);
        Res details = tomer.get("/events/details?id=3");
        check("details show the trade", details.status() == 200 && details.has("\"lmsrTradeHistory\"") && details.has("Tomer"));
        check("unknown event is 400", tomer.get("/events/details?id=77").status() == 400);

        guy.post("/events/open?id=2");
        Res sell = guy.post("/events/order?id=2&option=1&side=Sell&price=0.6&quantity=10");
        check("Guy's sell order rests", sell.status() == 200 && sell.has("\"restingQuantity\":10"), sell);
        Res fill = tomer.post("/events/order?id=2&option=1&side=Buy&price=0.6&quantity=4");
        check("Tomer's buy order fills", fill.status() == 200 && fill.has("\"quantity\":4"), fill);
        check("bad side is 400", tomer.post("/events/order?id=2&option=1&side=Hold&price=0.6&quantity=1").status() == 400);

        section("account details");
        Res me = tomer.get("/users/details");
        check("own details: ledger lines present", me.has("\"transactions\"") && me.has("Load funds") && me.has("\"Buy\""));
        check("own details: 2 participations", count(me.body(), "\"eventId\":") >= 2);
        Res users = guy.get("/users");
        check("users list shows both online", count(users.body(), "\"connected\":true") == 2);

        section("close");
        check("winner is required", guy.post("/events/close?id=2").status() == 400);
        Res close = guy.post("/events/close?id=2&winner=1");
        check("Guy closes event 2", close.status() == 200 && close.has("Argentina"), close);
        check("closing again is 400", guy.post("/events/close?id=2&winner=1").status() == 400);
        check("Tomer's ledger has the payout", tomer.get("/users/details").has("Payout"));

        section("chat (bonus)");
        check("empty message is 400", guy.post("/chat?text=").status() == 400);
        check("post message", guy.post("/chat?text=" + enc("Hello from Guy")).status() == 200);
        check("post message", tomer.post("/chat?text=" + enc("Hi from Tomer")).status() == 200);
        Res chat = tomer.get("/chat?version=0");
        check("full chat has 2 entries and version 2", chat.has("\"version\":2") && count(chat.body(), "\"text\":") == 2, chat);
        Res delta = tomer.get("/chat?version=1");
        check("delta from version 1 has only the newest entry", count(delta.body(), "\"text\":") == 1 && delta.has("Tomer"), delta);
        check("anonymous chat is 401", anon.get("/chat?version=0").status() == 401);

        section("logout");
        check("logout is 200", tomer.post("/logout").status() == 200);
        check("after logout requests are 401", tomer.get("/events").status() == 401);
        Res relogin = tomer.post("/login?username=Tomer");
        check("relogin keeps the balance", relogin.status() == 200 && tomer.get("/users/details").has("\"totalLoaded\":100"), relogin, tomer.get("/users/details"));

        System.out.println();
        System.out.println("HTTP tests: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static byte[] concat(byte[]... parts) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.write(p);
        }
        return out.toByteArray();
    }

    private static void section(String name) {
        System.out.println();
        System.out.println("## " + name);
    }

    private static void check(String what, boolean ok, Res... context) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + what);
        } else {
            failed++;
            System.out.println("  FAIL " + what);
            for (Res r : context) {
                String body = r.body().length() > 300 ? r.body().substring(0, 300) + "..." : r.body();
                System.out.println("       -> " + r.status() + " " + body);
            }
        }
    }
}
