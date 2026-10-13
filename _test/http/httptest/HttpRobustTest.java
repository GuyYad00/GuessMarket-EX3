package httptest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Third HTTP acceptance suite: everything the first two do not touch.
 * <ul>
 *   <li>the exercise-4 Node host itself: static files, MIME types, 404, path traversal,
 *       malformed URLs, the proxy (cookies, errors, multipart, unicode) and the 502 answer
 *       when Tomcat is down (a second host is started against a closed port);</li>
 *   <li>HTTP method and parameter abuse on the servlets (wrong verbs, missing / empty /
 *       overflowing / non-numeric parameters, odd user names, unknown paths, content types);</li>
 *   <li>schema-level upload errors, one rule at a time, each leaving the system untouched;</li>
 *   <li>ledger consistency after a full LMSR life cycle (every balanceAfter adds up, payout
 *       and commission math, participation drill-down).</li>
 * </ul>
 * Creates its own users and events (unique names), so it runs against any server state.
 * Usage: {@code HttpRobustTest [tomcatBaseUrl] [webClientDir]}; needs {@code node} on the PATH.
 */
public class HttpRobustTest {

    private static String base = "http://localhost:8080/guessmarket";
    private static String webDir = "web-client";
    private static final int WEB_PORT = 3105;
    private static final int DEAD_WEB_PORT = 3106;
    private static int passed = 0;
    private static int failed = 0;
    private static final String RUN = Long.toString(System.currentTimeMillis() % 100000);

    // ------------------------------------------------------------------ tiny HTTP client

    static final class Client {
        final String root;
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        Client(String root) {
            this.root = root;
        }

        Res get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(root + path)).GET().build());
        }

        Res post(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(root + path))
                    .POST(HttpRequest.BodyPublishers.noBody()).build());
        }

        Res method(String verb, String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(root + path))
                    .method(verb, HttpRequest.BodyPublishers.noBody()).build());
        }

        Res upload(byte[] content) throws Exception {
            return upload("/upload", content);
        }

        Res upload(String path, byte[] content) throws Exception {
            String boundary = "----GuessMarketRobust" + System.nanoTime();
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"events.xml\"\r\n"
                    + "Content-Type: text/xml\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            byte[] body = concat(head.getBytes(StandardCharsets.UTF_8), content, tail.getBytes(StandardCharsets.UTF_8));
            return send(HttpRequest.newBuilder(URI.create(root + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
        }

        Res login(String name) throws Exception {
            return post("/login?username=" + enc(name));
        }

        private Res send(HttpRequest request) throws Exception {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Res(response.statusCode(), response.body(),
                    response.headers().firstValue("content-type").orElse(""));
        }
    }

    record Res(int status, String body, String contentType) {
        boolean has(String text) {
            return body.contains(text);
        }

        boolean isJsonError() {
            return body.trim().startsWith("{") && body.contains("\"error\"");
        }

        boolean badRequest(String reason) {
            return status == 400 && isJsonError() && body.contains(reason);
        }

        double num(String field) {
            Matcher m = Pattern.compile("\"" + field + "\":(-?[0-9.E]+)").matcher(body);
            return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
        }

        long version() {
            return (long) num("version");
        }
    }

    record Tx(int number, String type, String description, double amount, double balanceAfter) { }

    // ------------------------------------------------------------------ event file builder

    private static String event(String name, String body) {
        return "    <GM-event name=\"" + name + "\">\n" + body + "    </GM-event>\n";
    }

    private static String standardBody(String commissionType, int commission, String options, String method) {
        return "      <description>robust test event</description>\n"
                + "      <commission type=\"" + commissionType + "\">" + commission + "</commission>\n"
                + "      <GM-options>" + options + "</GM-options>\n"
                + "      <GM-method>" + method + "</GM-method>\n";
    }

    private static final String YES_NO = "<GM-option>Yes</GM-option><GM-option>No</GM-option>";
    private static final String LMSR_100 = "<GM-LMSR><b>100</b></GM-LMSR>";
    private static final String BOOK_OK = "<GM-order-book allow-mint=\"true\" initial=\"5\" d=\"1\"/>";

    private static byte[] file(String... events) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Guess-Market>\n  <GM-events>\n");
        for (String e : events) {
            sb.append(e);
        }
        sb.append("  </GM-events>\n</Guess-Market>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] badEvent(String name, String body) {
        return file(event(name, body));
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            base = args[0];
        }
        if (args.length > 1) {
            webDir = args[1];
        }
        Process web = null;
        Process deadWeb = null;
        try {
            web = startNode(WEB_PORT, null);
            deadWeb = startNode(DEAD_WEB_PORT, "http://localhost:9");
            waitForPort(WEB_PORT);
            waitForPort(DEAD_WEB_PORT);

            webHostTests();
            methodAndParameterTests();
            schemaTests();
            ledgerTests();
        } finally {
            if (web != null) {
                web.destroyForcibly();
            }
            if (deadWeb != null) {
                deadWeb.destroyForcibly();
            }
        }

        System.out.println();
        System.out.println("HTTP robust tests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------ A. web host

    private static void webHostTests() throws Exception {
        section("Exercise-4 host: static files");
        String webRoot = "http://localhost:" + WEB_PORT;
        Client browser = new Client(webRoot);

        Res home = browser.get("/");
        check("GET / serves the login page as text/html",
                home.status() == 200 && home.contentType().startsWith("text/html") && home.has("Login"), home);
        Res chatPage = browser.get("/chat.html");
        check("chat.html is served and wires js/chat.js",
                chatPage.status() == 200 && chatPage.has("Chat (bonus)") && chatPage.has("js/chat.js"), chatPage);
        Res js = browser.get("/js/chat.js");
        check("JS is served with a JavaScript content type",
                js.status() == 200 && js.contentType().startsWith("application/javascript") && js.has("Api.chatSince"), js);
        Res css = browser.get("/css/style.css");
        check("CSS is served as text/css and contains the chat styles",
                css.status() == 200 && css.contentType().startsWith("text/css") && css.has(".chat-line"), css);
        Res events = browser.get("/events.html");
        Res account = browser.get("/account.html");
        check("events.html and account.html both link to the chat tab",
                events.has("chat.html") && account.has("chat.html"), events, account);
        Res missing = browser.get("/no-such-page.html");
        check("unknown static path answers 404 (not a crash, not the index)",
                missing.status() == 404, missing);
        Res cache = browser.get("/js/api.js");
        check("static files are served with Cache-Control: no-cache (edits show up on refresh)",
                cache.status() == 200, cache);

        section("Exercise-4 host: hostile URLs");
        String traversal = raw(WEB_PORT, "GET /../server.js HTTP/1.1");
        check("path traversal (/../server.js) is refused", !traversal.startsWith("HTTP/1.1 200")
                && !traversal.contains("require('http')"), new Res(0, traversal, ""));
        String encodedTraversal = raw(WEB_PORT, "GET /%2e%2e/%2e%2e/server.js HTTP/1.1");
        check("encoded traversal (%2e%2e) is refused", !encodedTraversal.startsWith("HTTP/1.1 200")
                && !encodedTraversal.contains("require('http')"), new Res(0, encodedTraversal, ""));
        String malformed = raw(WEB_PORT, "GET /%zz HTTP/1.1");
        check("malformed percent-encoding gets an HTTP error answer (no exception leaks)",
                malformed.startsWith("HTTP/1.1 4"), new Res(0, malformed, ""));
        Res stillAlive = browser.get("/");
        check("the host is still alive after the malformed URL", stillAlive.status() == 200, stillAlive);

        section("Exercise-4 host: proxy to Tomcat");
        Res anon = browser.get("/guessmarket/events");
        check("unauthenticated API call through the proxy is a JSON 401, exactly as direct",
                anon.status() == 401 && anon.isJsonError(), anon);
        String name = "Web" + RUN;
        Res login = browser.post("/guessmarket/login?username=" + enc(name));
        check("login through the proxy succeeds", login.status() == 200 && login.has(name), login);
        Res me = browser.get("/guessmarket/me");
        check("the session cookie survives the proxy round trip (GET /me knows us)",
                me.status() == 200 && me.has("\"" + name + "\""), me);
        Res badBuy = browser.post("/guessmarket/events/buy?id=99999999&option=1&shares=1");
        check("server-side 400 and its JSON reason pass through the proxy unchanged",
                badBuy.status() == 400 && badBuy.isJsonError(), badBuy);
        String viaProxy = "Robust Via Proxy " + RUN;
        Res upload = browser.upload("/guessmarket/upload", file(event(viaProxy, standardBody("on-close", 5, YES_NO, LMSR_100))));
        check("multipart upload through the proxy (binary body forwarded intact)",
                upload.status() == 200 && upload.num("eventsAdded") == 1, upload);
        Res unicodeChat = browser.post("/guessmarket/chat?text=" + enc("proxy says שלום"));
        check("unicode chat text round-trips through the proxy",
                unicodeChat.status() == 200 && unicodeChat.has("proxy says שלום"), unicodeChat);
        Res chatDelta = browser.get("/guessmarket/chat?version=0");
        check("chat delta fetch works through the proxy", chatDelta.status() == 200 && chatDelta.has("\"version\""), chatDelta);
        Res logout = browser.post("/guessmarket/logout");
        Res after = browser.get("/guessmarket/me");
        check("logout through the proxy ends the session", logout.status() == 200 && after.status() == 401, logout, after);

        section("Exercise-4 host: Tomcat down");
        Client dead = new Client("http://localhost:" + DEAD_WEB_PORT);
        Res deadHome = dead.get("/");
        check("static pages are still served when Tomcat is unreachable", deadHome.status() == 200, deadHome);
        Res deadApi = dead.post("/guessmarket/login?username=x");
        check("API calls answer 502 with a JSON explanation when Tomcat is unreachable",
                deadApi.status() == 502 && deadApi.isJsonError() && deadApi.has("not reachable"), deadApi);
        Res deadAgain = dead.get("/guessmarket/events");
        check("the host survives repeated upstream failures", deadAgain.status() == 502, deadAgain);
    }

    // ------------------------------------------------------------------ B. methods and parameters

    private static void methodAndParameterTests() throws Exception {
        section("Wrong HTTP verbs");
        Client c = new Client(base);
        String name = "Robust" + RUN;
        check("login works", c.login(name).status() == 200);
        Res put = c.method("PUT", "/events");
        check("PUT on a read endpoint is 405", put.status() == 405, put);
        Res del = c.method("DELETE", "/login");
        check("DELETE on /login is 405", del.status() == 405, del);
        Res postList = c.post("/events");
        check("POST on the GET-only /events is 405", postList.status() == 405, postList);
        Res unknown = c.get("/no-such-endpoint");
        check("unknown API path is 404", unknown.status() == 404, unknown);

        section("Parameter abuse");
        Res noId = c.get("/events/details");
        check("details without id is a JSON 400", noId.badRequest(""), noId);
        Res emptyId = c.get("/events/details?id=");
        check("details with an empty id is a JSON 400", emptyId.badRequest(""), emptyId);
        Res negId = c.get("/events/details?id=-1");
        check("details with a negative id is a JSON 400", negId.badRequest(""), negId);
        Res hugeId = c.get("/events/details?id=99999999999999999999");
        check("an id that overflows int is a JSON 400, not a 500", hugeId.badRequest(""), hugeId);

        // one real event to abuse
        String target = "Robust Target " + RUN;
        Res up = c.upload(file(event(target, standardBody("on-close", 10, YES_NO, BOOK_OK))));
        check("target event uploaded", up.status() == 200, up);
        int id = idOf(c.get("/events").body(), target);
        check("target event has an id", id > 0);
        Res sci = c.post("/events/buy?id=" + id + "&option=1&shares=1e3");
        check("shares in scientific notation is rejected as a bad number", sci.badRequest(""), sci);
        Res zeroOpt = c.post("/events/buy?id=" + id + "&option=0&shares=1");
        check("option 0 does not exist", zeroOpt.badRequest(""), zeroOpt);
        Res side = c.post("/events/order?id=" + id + "&option=1&side=Hold&price=0.5&quantity=1");
        check("order with an unknown side is rejected", side.badRequest(""), side);
        Res nan = c.post("/events/order?id=" + id + "&option=1&side=Buy&price=NaN&quantity=1");
        check("order with price=NaN is rejected", nan.badRequest(""), nan);
        Res inf = c.post("/events/order?id=" + id + "&option=1&side=Buy&price=1e400&quantity=1");
        check("order with an infinite price is rejected", inf.badRequest(""), inf);
        Res fracQty = c.post("/events/order?id=" + id + "&option=1&side=Buy&price=0.5&quantity=1.5");
        check("order with a fractional quantity is rejected", fracQty.badRequest(""), fracQty);
        Res winner = c.post("/events/close?id=" + id + "&winner=abc");
        check("close with a non-numeric winner is rejected", winner.badRequest(""), winner);
        Res fundsText = c.post("/users/funds?amount=abc");
        check("funds with a non-numeric amount is rejected", fundsText.badRequest(""), fundsText);
        Res fundsTiny = c.post("/users/funds?amount=0.001");
        check("funds below one cent is rejected", fundsTiny.badRequest("0.01"), fundsTiny);
        Res fundsNegZero = c.post("/users/funds?amount=-0");
        check("funds of -0 is rejected", fundsNegZero.badRequest(""), fundsNegZero);
        Res fundsInf = c.post("/users/funds?amount=Infinity");
        check("funds of Infinity is rejected", fundsInf.badRequest(""), fundsInf);
        Res fundsOk = c.post("/users/funds?amount=0.01");
        check("funds of exactly one cent is accepted", fundsOk.status() == 200 && near(fundsOk.num("amountLoaded"), 0.01, 1e-9), fundsOk);

        section("Content types and odd user names");
        Res list = c.get("/events");
        check("successful answers are application/json", list.contentType().startsWith("application/json"), list);
        Res err = new Client(base).get("/events");
        check("error answers are application/json too", err.status() == 401 && err.contentType().startsWith("application/json"), err);
        Client spaced = new Client(base);
        String spacedName = "Robust Two Words " + RUN;
        Res spacedLogin = spaced.login(spacedName);
        Res spacedMe = spaced.get("/me");
        check("a name with inner spaces is accepted and echoed exactly",
                spacedLogin.status() == 200 && spacedMe.has("\"" + spacedName + "\""), spacedLogin, spacedMe);
        Client hebrew = new Client(base);
        String hebrewName = "רובוסט" + RUN;
        Res hebrewLogin = hebrew.login(hebrewName);
        Res hebrewUsers = hebrew.get("/users");
        check("a Hebrew user name is accepted and listed",
                hebrewLogin.status() == 200 && hebrewUsers.has(hebrewName), hebrewLogin, hebrewUsers);
        Client html = new Client(base);
        Res htmlLogin = html.login("<b>Robust</b>" + RUN);
        Res htmlMe = html.get("/me");
        check("a name with HTML characters is accepted and returned as JSON (no 500)",
                htmlLogin.status() == 200 && htmlMe.status() == 200 && htmlMe.has("Robust"), htmlLogin, htmlMe);
        Res quoteChat = c.post("/chat?text=" + enc("it's \"quoted\" \\ back\\slash"));
        check("quotes and backslashes in chat text survive JSON encoding",
                quoteChat.status() == 200 && quoteChat.has("quoted") && quoteChat.has("back"), quoteChat);
        spaced.post("/logout");
        hebrew.post("/logout");
        html.post("/logout");
        c.post("/logout");
    }

    // ------------------------------------------------------------------ C. schema errors

    private static void schemaTests() throws Exception {
        section("Schema-level upload errors (each leaves the system untouched)");
        Client c = new Client(base);
        check("login works", c.login("RobustSchema" + RUN).status() == 200);
        Res before = c.get("/events");
        long versionBefore = before.version();
        int countBefore = count(before.body(), "\"id\":");
        String n = "Robust Schema " + RUN;

        expectBad(c, "missing description",
                badEvent(n, "      <commission type=\"on-close\">5</commission>\n      <GM-options>" + YES_NO
                        + "</GM-options>\n      <GM-method>" + LMSR_100 + "</GM-method>\n"), "has no description");
        expectBad(c, "commission 101", badEvent(n, standardBody("on-close", 101, YES_NO, LMSR_100)), "illegal commission");
        expectBad(c, "commission -1", badEvent(n, standardBody("on-close", -1, YES_NO, LMSR_100)), "illegal commission");
        expectBad(c, "unknown commission type", badEvent(n, standardBody("monthly", 5, YES_NO, LMSR_100)), "unknown commission type");
        expectBad(c, "a single option", badEvent(n, standardBody("on-close", 5, "<GM-option>Yes</GM-option>", LMSR_100)), "1 was found");
        expectBad(c, "three options", badEvent(n, standardBody("on-close", 5,
                YES_NO + "<GM-option>Maybe</GM-option>", LMSR_100)), "3 were found");
        expectBad(c, "two options equal ignoring case", badEvent(n, standardBody("on-close", 5,
                "<GM-option>Yes</GM-option><GM-option>yes</GM-option>", LMSR_100)), "twice");
        expectBad(c, "an empty option", badEvent(n, standardBody("on-close", 5,
                "<GM-option>Yes</GM-option><GM-option>   </GM-option>", LMSR_100)), "empty option");
        expectBad(c, "b = 0", badEvent(n, standardBody("on-close", 5, YES_NO, "<GM-LMSR><b>0</b></GM-LMSR>")), "illegal liquidity");
        expectBad(c, "b negative", badEvent(n, standardBody("on-close", 5, YES_NO, "<GM-LMSR><b>-7</b></GM-LMSR>")), "illegal liquidity");
        expectBad(c, "b not a whole number", badEvent(n, standardBody("on-close", 5, YES_NO, "<GM-LMSR><b>12.5</b></GM-LMSR>")), "");
        expectBad(c, "order book d = 0", badEvent(n, standardBody("on-close", 5, YES_NO,
                "<GM-order-book allow-mint=\"true\" initial=\"5\" d=\"0\"/>")), "illegal base value");
        expectBad(c, "order book negative initial", badEvent(n, standardBody("on-close", 5, YES_NO,
                "<GM-order-book allow-mint=\"true\" initial=\"-1\" d=\"1\"/>")), "negative initial");
        expectBad(c, "allow-mint = maybe", badEvent(n, standardBody("on-close", 5, YES_NO,
                "<GM-order-book allow-mint=\"maybe\" initial=\"5\" d=\"1\"/>")), "allow-mint");
        expectBad(c, "both LMSR and order book", badEvent(n, standardBody("on-close", 5, YES_NO, LMSR_100 + BOOK_OK)), "exactly one trading method");
        expectBad(c, "empty GM-method", badEvent(n, standardBody("on-close", 5, YES_NO, "")), "exactly one trading method");
        expectBad(c, "no GM-method at all", badEvent(n, "      <description>d</description>\n"
                + "      <commission type=\"on-close\">5</commission>\n      <GM-options>" + YES_NO + "</GM-options>\n"), "no trading method");
        expectBad(c, "event with an empty name", badEvent("   ", standardBody("on-close", 5, YES_NO, LMSR_100)), "no name");
        expectBad(c, "wrong root element", "<?xml version=\"1.0\"?><Market><GM-events></GM-events></Market>".getBytes(StandardCharsets.UTF_8), "");
        expectBad(c, "a JSON file instead of XML", "{\"events\":[]}".getBytes(StandardCharsets.UTF_8), "");
        expectBad(c, "GM-events present but empty", "<?xml version=\"1.0\"?><Guess-Market><GM-events></GM-events></Guess-Market>"
                .getBytes(StandardCharsets.UTF_8), "no events");
        expectBad(c, "a valid event plus a broken one (atomic)", file(
                event(n + " ok", standardBody("on-close", 5, YES_NO, LMSR_100)),
                event(n + " broken", standardBody("on-close", 5, YES_NO, "<GM-LMSR><b>0</b></GM-LMSR>"))), "illegal liquidity");

        Res after = c.get("/events");
        check("after all the bad files the event count is unchanged", count(after.body(), "\"id\":") == countBefore, after);
        check("and the version is unchanged (nothing was mutated)", after.version() == versionBefore, after);

        expectBad(c, "commission 91 (the spec allows 0 to 90)", badEvent(n, standardBody("on-close", 91, YES_NO, LMSR_100)), "illegal commission");
        Res bounds = c.upload(file(
                event(n + " zero", standardBody("on-purchase", 0, YES_NO, LMSR_100)),
                event(n + " ninety", standardBody("on-close", 90, YES_NO, BOOK_OK))));
        check("commission 0 and 90 (the spec's boundaries) are accepted", bounds.status() == 200 && bounds.num("eventsAdded") == 2, bounds);
        Res okName = c.upload(file(event(n + " ok", standardBody("on-close", 5, YES_NO, LMSR_100))));
        check("the good event from the atomic test was really not added earlier (adding it now succeeds)",
                okName.status() == 200 && okName.num("eventsAdded") == 1, okName);
        Res clash = c.upload(file(event("  " + (n + " OK").toUpperCase() + "  ", standardBody("on-close", 5, YES_NO, LMSR_100))));
        check("a name equal ignoring case and outer spaces clashes with it", clash.badRequest("already exists"), clash);
        c.post("/logout");
    }

    private static void expectBad(Client c, String what, byte[] content, String reason) throws Exception {
        Res r = c.upload(content);
        check("upload with " + what + " is a JSON 400" + (reason.isEmpty() ? "" : " mentioning '" + reason + "'"),
                r.badRequest(reason), r);
    }

    // ------------------------------------------------------------------ D. ledger

    private static void ledgerTests() throws Exception {
        section("Ledger consistency over a full LMSR life cycle");
        Client mm = new Client(base);
        Client tr = new Client(base);
        String mmName = "RobustMM" + RUN, trName = "RobustTr" + RUN;
        check("both users log in", mm.login(mmName).status() == 200 && tr.login(trName).status() == 200);
        String evName = "Robust Ledger " + RUN;
        check("MM loads 1000", mm.post("/users/funds?amount=1000").status() == 200);
        check("MM uploads a 10% on-close LMSR event", mm.upload(file(event(evName, standardBody("on-close", 10, YES_NO, LMSR_100)))).status() == 200);
        int id = idOf(mm.get("/events").body(), evName);
        Res open = mm.post("/events/open?id=" + id);
        check("MM opens it (subsidy 100*ln2 = 69.31)", open.status() == 200, open);
        check("trader loads 100", tr.post("/users/funds?amount=100").status() == 200);
        Res buy = tr.post("/events/buy?id=" + id + "&option=1&shares=10");
        double cost = buy.num("cost");
        check("trader buys 10 Yes", buy.status() == 200 && cost > 0, buy);
        Res close = mm.post("/events/close?id=" + id + "&winner=1");
        double paid = close.num("totalPaidToWinners");
        double commission = close.num("commissionCollected");
        check("MM closes with Yes as the winner", close.status() == 200, close);
        check("payout + commission = 10 shares x $1", near(paid + commission, 10.0, 1e-6), close);
        check("commission is 10% of the gross payout", near(commission, 1.0, 1e-6), close);

        Res trDetails = tr.get("/users/details");
        List<Tx> trLedger = ledger(trDetails.body());
        check("trader ledger has load, purchase and payout lines", trLedger.size() >= 3, trDetails);
        check("trader ledger numbers are 1..n without gaps", numbered(trLedger), trDetails);
        check("every trader balanceAfter equals the previous one plus the amount", addsUp(trLedger), trDetails);
        check("the last trader balanceAfter equals the current balance",
                !trLedger.isEmpty() && near(trLedger.get(trLedger.size() - 1).balanceAfter(), trDetails.num("balance"), 1e-6), trDetails);
        check("trader balance = 100 - cost + payout", near(trDetails.num("balance"), 100 - cost + paid, 1e-6), trDetails);
        check("trader has a ledger line of exactly the payout amount", hasAmount(trLedger, paid), trDetails);
        check("trader has a ledger line of exactly minus the purchase cost", hasAmount(trLedger, -cost), trDetails);
        check("trader totalLoaded is 100", near(trDetails.num("totalLoaded"), 100, 1e-9), trDetails);
        String participation = participationOf(trDetails.body(), id);
        check("trader participation shows the event Closed with winner Yes",
                participation.contains("\"status\":\"Closed\"") && participation.contains("\"winnerName\":\"Yes\""), trDetails);
        check("trader participation lists the LMSR purchase (10 shares at the exact cost)",
                participation.contains("\"shares\":10") && near(numIn(participation, "cost"), cost, 1e-6), trDetails);
        check("the payout ledger line explains itself (winning shares, on-close commission)",
                trLedger.stream().anyMatch(tx -> tx.description().contains("10 winning shares") && tx.description().contains("commission")), trDetails);

        double subsidy = 100 * Math.log(2);
        double leftover = close.num("leftoverToMarketMaker");
        check("leftover to the MM = subsidy + trader's money - gross payout", near(leftover, subsidy + cost - 10.0, 1e-6), close);
        Res mmDetails = mm.get("/users/details");
        List<Tx> mmLedger = ledger(mmDetails.body());
        check("MM ledger adds up too", addsUp(mmLedger) && numbered(mmLedger), mmDetails);
        check("MM has the subsidy line (-69.31)", hasAmount(mmLedger, -subsidy), mmDetails);
        check("MM has one close line worth commission + leftover", hasAmount(mmLedger, commission + leftover), mmDetails);
        check("MM balance = 1000 - subsidy + commission + leftover",
                near(mmDetails.num("balance"), 1000 - subsidy + commission + leftover, 1e-6), mmDetails);
        check("money is conserved: MM balance + trader balance = 1100",
                near(mmDetails.num("balance") + trDetails.num("balance"), 1100.0, 1e-6), mmDetails, trDetails);
        Res list = mm.get("/events");
        check("the event is listed as Closed", list.has("\"name\":\"" + evName + "\"") && statusOf(list.body(), evName).equals("Closed"), list);
        mm.post("/logout");
        tr.post("/logout");
    }

    // ------------------------------------------------------------------ helpers

    private static Process startNode(int port, String tomcat) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("node", "server.js");
        pb.directory(new File(webDir));
        pb.environment().put("PORT", Integer.toString(port));
        if (tomcat != null) {
            pb.environment().put("TOMCAT", tomcat);
        }
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    private static void waitForPort(int port) throws Exception {
        for (int i = 0; i < 50; i++) {
            try (Socket s = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("node host on port " + port + " did not start (is node on the PATH?)");
    }

    /** One raw HTTP/1.1 request on a socket; returns the status line plus the body text. */
    private static String raw(int port, String requestLine) throws IOException {
        try (Socket s = new Socket("localhost", port)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            out.write((requestLine + "\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            return "NO RESPONSE (" + e.getMessage() + ")";
        }
    }

    private static List<Tx> ledger(String json) {
        List<Tx> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\"number\":(\\d+),\"type\":\"([^\"]*)\",\"description\":\"((?:[^\"\\\\]|\\\\.)*)\",\"amount\":(-?[0-9.E]+),\"balanceAfter\":(-?[0-9.E]+)\\}")
                .matcher(json);
        while (m.find()) {
            out.add(new Tx(Integer.parseInt(m.group(1)), m.group(2), m.group(3),
                    Double.parseDouble(m.group(4)), Double.parseDouble(m.group(5))));
        }
        out.sort((a, b) -> Integer.compare(a.number(), b.number()));
        return out;
    }

    private static boolean numbered(List<Tx> ledger) {
        for (int i = 0; i < ledger.size(); i++) {
            if (ledger.get(i).number() != i + 1) {
                return false;
            }
        }
        return !ledger.isEmpty();
    }

    private static boolean addsUp(List<Tx> ledger) {
        double running = 0;
        for (Tx tx : ledger) {
            running += tx.amount();
            if (!near(running, tx.balanceAfter(), 1e-6)) {
                return false;
            }
        }
        return !ledger.isEmpty();
    }

    private static boolean hasAmount(List<Tx> ledger, double amount) {
        return ledger.stream().anyMatch(tx -> near(tx.amount(), amount, 1e-6));
    }

    private static String participationOf(String json, int eventId) {
        Matcher m = Pattern.compile("\\{\"eventId\":" + eventId + ",.*?\"winnerName\":(?:\"[^\"]*\"|null)\\}").matcher(json);
        return m.find() ? m.group() : "";
    }

    private static double numIn(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":(-?[0-9.E]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    private static String statusOf(String eventsJson, String name) {
        Matcher m = Pattern.compile("\"name\":\"" + Pattern.quote(name) + "\".*?\"status\":\"([^\"]*)\"").matcher(eventsJson);
        return m.find() ? m.group(1) : "";
    }

    private static int idOf(String eventsJson, String name) {
        Matcher m = Pattern.compile("\"id\":(\\d+),\"name\":\"" + Pattern.quote(name) + "\"").matcher(eventsJson);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
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
                String body = r.body().length() > 400 ? r.body().substring(0, 400) + "..." : r.body();
                System.out.println("       -> " + r.status() + " " + body);
            }
        }
    }
}
