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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second HTTP acceptance suite: edge cases, atomicity, market math, the blocked-user
 * rule on every endpoint, concurrency (no lost updates) and session behaviour.
 * It creates its own events (unique names), so it runs against any server state.
 * Usage: {@code HttpEdgeTest [baseUrl]}
 */
public class HttpEdgeTest {

    private static String base = "http://localhost:8080/guessmarket";
    private static int passed = 0;
    private static int failed = 0;
    private static final String RUN = Long.toString(System.currentTimeMillis() % 100000);

    static final class Client {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        Res get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(base + path)).GET().build());
        }

        Res post(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(base + path))
                    .POST(HttpRequest.BodyPublishers.noBody()).build());
        }

        Res upload(String fileName, byte[] content) throws Exception {
            String boundary = "----GuessMarketEdge" + System.nanoTime();
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: text/xml\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";
            byte[] body = concat(head.getBytes(StandardCharsets.UTF_8), content, tail.getBytes(StandardCharsets.UTF_8));
            return send(HttpRequest.newBuilder(URI.create(base + "/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
        }

        Res login(String name) throws Exception {
            return post("/login?username=" + enc(name));
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

        boolean isJsonError() {
            return body.trim().startsWith("{") && body.contains("\"error\"");
        }

        double num(String field) {
            Matcher m = Pattern.compile("\"" + field + "\":(-?[0-9.E]+)").matcher(body);
            if (!m.find()) {
                return Double.NaN;
            }
            return Double.parseDouble(m.group(1));
        }

        List<Double> nums(String field) {
            List<Double> out = new ArrayList<>();
            Matcher m = Pattern.compile("\"" + field + "\":(-?[0-9.E]+)").matcher(body);
            while (m.find()) {
                out.add(Double.parseDouble(m.group(1)));
            }
            return out;
        }

        long version() {
            return (long) num("version");
        }
    }

    // ------------------------------------------------------------------ event files

    private static String lmsrName = "Edge LMSR " + RUN;
    private static String mintName = "Edge Mint Book " + RUN;
    private static String noMintName = "Edge NoMint Book " + RUN;
    private static String blockName = "Edge Block LMSR " + RUN;

    private static String event(String name, String commissionType, int commission, String method) {
        return "    <GM-event name=\"" + name + "\">\n"
                + "      <description>edge test event</description>\n"
                + "      <commission type=\"" + commissionType + "\">" + commission + "</commission>\n"
                + "      <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>\n"
                + "      <GM-method>" + method + "</GM-method>\n"
                + "    </GM-event>\n";
    }

    private static byte[] file(String... events) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Guess-Market>\n  <GM-events>\n");
        for (String e : events) {
            sb.append(e);
        }
        sb.append("  </GM-events>\n</Guess-Market>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static final String LMSR_100 = "<GM-LMSR><b>100</b></GM-LMSR>";
    private static final String BOOK_MINT = "<GM-order-book allow-mint=\"true\" initial=\"50\" d=\"1\"/>";
    private static final String BOOK_NO_MINT = "<GM-order-book allow-mint=\"false\" initial=\"10\" d=\"1\"/>";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            base = args[0];
        }
        Client mm = new Client();
        Client t1 = new Client();
        Client t2 = new Client();
        Client t3 = new Client();
        Client dana = new Client();
        Client anon = new Client();
        String mmName = "EdgeMM" + RUN, t1Name = "EdgeT1" + RUN, t2Name = "EdgeT2" + RUN,
                t3Name = "EdgeT3" + RUN, danaName = "EdgeDana" + RUN;

        section("login edge cases");
        check("whitespace-only name is 400 with json error", anon.login("   ").status() == 400 && anon.login("   ").isJsonError());
        Res trimmed = mm.login("  " + mmName + "  ");
        check("surrounding spaces are trimmed", trimmed.status() == 200 && trimmed.has("\"userName\":\"" + mmName + "\""), trimmed);
        Res tooLong = anon.login("x".repeat(41));
        check("41-character name is 400", tooLong.status() == 400 && tooLong.isJsonError(), tooLong);
        Client zed = new Client();
        Res getLogin = zed.get("/login?username=Zed" + RUN);
        check("GET on /login works too (Postman friendly, like the course example)", getLogin.status() == 200 && getLogin.has("Zed" + RUN), getLogin);
        Res getFunds = anon.get("/users/funds?amount=5");
        check("GET on a POST-only endpoint is refused (401 or 405), never executed",
                (getFunds.status() == 401 || getFunds.status() == 405), getFunds);
        zed.post("/logout");
        check("second user logs in", t1.login(t1Name).status() == 200);
        check("third user logs in", t2.login(t2Name).status() == 200);
        check("fourth user logs in", t3.login(t3Name).status() == 200);
        check("fifth user logs in", dana.login(danaName).status() == 200);

        section("upload: validation is all-or-nothing");
        long eventsBefore = count(mm.get("/events").body(), "\"id\":");
        long versionBefore = mm.get("/events").version();
        Res garbage = mm.upload("bad.xml", "this is not xml".getBytes(StandardCharsets.UTF_8));
        check("garbage file is 400 with json error", garbage.status() == 400 && garbage.isJsonError(), garbage);
        Res empty = mm.upload("empty.xml", new byte[0]);
        check("empty file is 400", empty.status() == 400 && empty.isJsonError(), empty);
        Res partlyBad = mm.upload("partly.xml", file(
                event(lmsrName, "on-close", 10, LMSR_100),
                event("Edge Broken " + RUN, "on-close", 150, LMSR_100)));
        check("file with one bad event (commission 150) is 400", partlyBad.status() == 400 && partlyBad.isJsonError(), partlyBad);
        Res afterBad = mm.get("/events");
        check("nothing was added by rejected files", count(afterBad.body(), "\"id\":") == eventsBefore);
        check("version unchanged by rejected files", afterBad.version() == versionBefore);
        Res dupInside = mm.upload("dup.xml", file(
                event(lmsrName, "on-close", 10, LMSR_100),
                event(lmsrName.toUpperCase(), "on-close", 10, LMSR_100)));
        check("duplicate name inside one file (different case) is 400", dupInside.status() == 400, dupInside);

        Res good = mm.upload("good.xml", file(
                event(lmsrName, "on-close", 10, LMSR_100),
                event(mintName, "on-purchase", 10, BOOK_MINT),
                event(noMintName, "on-close", 5, BOOK_NO_MINT),
                event(blockName, "on-close", 10, LMSR_100)));
        check("valid file adds 4 events", good.status() == 200 && good.has("\"eventsAdded\":4"), good);
        Res afterGood = mm.get("/events");
        check("event count grew by exactly 4", count(afterGood.body(), "\"id\":") == eventsBefore + 4);
        check("version bumped by the upload", afterGood.version() > versionBefore);
        Res clash = t1.upload("clash.xml", file(event("  " + lmsrName.toLowerCase() + " ", "on-close", 10, LMSR_100)));
        check("same name from another user, other case + spaces, is 400", clash.status() == 400 && clash.has("already exists"), clash);
        int lmsrId = idOf(afterGood.body(), lmsrName);
        int mintId = idOf(afterGood.body(), mintName);
        int noMintId = idOf(afterGood.body(), noMintName);
        int blockId = idOf(afterGood.body(), blockName);
        check("ids were assigned", lmsrId > 0 && mintId > 0 && noMintId > 0 && blockId > 0);
        Res mmDetails = mm.get("/users/details");
        String mmIds = listOf(mmDetails.body(), "marketMakerEventIds");
        check("uploader is market maker of all four",
                hasId(mmIds, lmsrId) && hasId(mmIds, mintId) && hasId(mmIds, noMintId) && hasId(mmIds, blockId), mmDetails);
        check("another user is market maker of none of them", !hasId(listOf(t1.get("/users/details").body(), "marketMakerEventIds"), lmsrId));

        section("LMSR over HTTP");
        check("buy before open is 400", t1.post("/events/buy?id=" + lmsrId + "&option=1&shares=1").status() == 400);
        check("MM loads 1000", mm.post("/users/funds?amount=1000").status() == 200);
        Res open = mm.post("/events/open?id=" + lmsrId);
        check("open LMSR b=100 costs b*ln2 = 69.31", open.status() == 200 && near(open.num("amountPaid"), 69.31, 0.01), open);
        Res fresh = t1.get("/events/details?id=" + lmsrId);
        List<Double> p0 = fresh.nums("price");
        check("prices start at 0.5 / 0.5", p0.size() == 2 && near(p0.get(0), 0.5, 1e-6) && near(p0.get(1), 0.5, 1e-6), fresh);
        check("T1 loads 100", t1.post("/users/funds?amount=100").status() == 200);
        Res buy = t1.post("/events/buy?id=" + lmsrId + "&option=1&shares=20");
        check("T1 buys 20 Yes", buy.status() == 200 && buy.has("\"shares\":20"), buy);
        Res after = t1.get("/events/details?id=" + lmsrId);
        List<Double> p1 = after.nums("price");
        check("Yes price rose above 0.5 and prices sum to 1", p1.size() == 2 && p1.get(0) > 0.5 && near(p1.get(0) + p1.get(1), 1.0, 1e-6), after);
        check("Yes holds 20 shares", after.has("\"totalShares\":20"), after);
        check("balance = 100 - cost (on-close commission: nothing charged now)",
                near(buy.num("userBalance"), 100 - buy.num("cost"), 0.011), buy);
        Res wrongKind = t1.post("/events/order?id=" + lmsrId + "&option=1&side=Buy&price=0.5&quantity=1");
        check("order on an LMSR event is 400", wrongKind.status() == 400 && wrongKind.isJsonError(), wrongKind);
        check("non-numeric id is 400", t1.get("/events/details?id=abc").status() == 400);
        check("close with option 5 is 400", mm.post("/events/close?id=" + lmsrId + "&winner=5").status() == 400);

        section("blocked user: every action is refused");
        check("MM opens the second LMSR", mm.post("/events/open?id=" + blockId).status() == 200);
        Res overdraft = dana.post("/events/buy?id=" + blockId + "&option=2&shares=3");
        check("buy with 0 funds goes through, balance negative, user blocked",
                overdraft.status() == 200 && overdraft.num("userBalance") < 0 && overdraft.has("\"userBlocked\":true"), overdraft);
        check("users list shows Dana blocked", blockedFlag(mm.get("/users").body(), danaName));
        check("blocked: buy is 400", dana.post("/events/buy?id=" + blockId + "&option=1&shares=1").has("blocked"));
        check("blocked: load funds is 400", dana.post("/users/funds?amount=50").status() == 400);
        check("blocked: upload is 400", dana.upload("x.xml", file(event("Edge Dana " + RUN, "on-close", 1, LMSR_100))).status() == 400);
        check("blocked: order is 400", dana.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=0.5&quantity=1").status() == 400);
        check("blocked: reading is still allowed", dana.get("/events").status() == 200);
        check("logout and relogin", dana.post("/logout").status() == 200 && dana.login(danaName).status() == 200);
        check("still blocked after relogin", dana.get("/users/details").has("\"blocked\":true"));

        section("order book over HTTP");
        check("order before open is 400", t2.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=0.5&quantity=1").status() == 400);
        Res openBook = mm.post("/events/open?id=" + mintId);
        check("open book: MM pays initial 50 x d", openBook.status() == 200 && near(openBook.num("amountPaid"), 50.0, 0.001), openBook);
        check("T2 loads 100", t2.post("/users/funds?amount=100").status() == 200);
        check("T3 loads 100", t3.post("/users/funds?amount=100").status() == 200);
        Res sellNothing = t2.post("/events/order?id=" + mintId + "&option=1&side=Sell&price=0.5&quantity=1");
        check("selling shares you do not own is 400", sellNothing.status() == 400 && sellNothing.has("available"), sellNothing);
        check("quantity 0 is 400", t2.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=0.5&quantity=0").status() == 400);
        check("price 0 is 400", t2.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=0&quantity=1").status() == 400);
        check("price 1.5 is 400", t2.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=1.5&quantity=1").status() == 400);
        Res bid = t2.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=0.40&quantity=10");
        check("T2 bid 10 @0.40 rests", bid.status() == 200 && bid.has("\"restingQuantity\":10"), bid);
        Res book = t2.get("/events/details?id=" + mintId);
        check("book shows bid 0.4", book.has("\"bid\":0.4"), book);
        Res hit = mm.post("/events/order?id=" + mintId + "&option=1&side=Sell&price=0.40&quantity=10");
        check("MM sells 10 @0.40 into the bid: filled", hit.status() == 200 && hit.has("\"quantity\":10") && hit.has("\"restingQuantity\":0"), hit);
        Res bookAfter = t2.get("/events/details?id=" + mintId);
        check("LAST is 0.4 and the bid is gone", bookAfter.has("\"last\":0.4") && !bookAfter.has("\"bid\":0.4"), bookAfter);
        Res t2Details = t2.get("/users/details");
        check("T2 holds 10 Yes shares", t2Details.has("\"holdings\":[10,0]"), t2Details);
        Res mintA = t2.post("/events/order?id=" + mintId + "&option=1&side=Buy&price=0.60&quantity=5");
        Res mintB = t3.post("/events/order?id=" + mintId + "&option=2&side=Buy&price=0.50&quantity=5");
        check("crossing buys on opposite options mint a pair (0.60 + 0.50 >= d)",
                mintA.status() == 200 && mintB.status() == 200 && mintB.has("\"mint\":true") && mintB.has("\"quantity\":5"), mintA, mintB);
        check("MM opens the no-mint book", mm.post("/events/open?id=" + noMintId).status() == 200);
        Res noA = t2.post("/events/order?id=" + noMintId + "&option=1&side=Buy&price=0.60&quantity=2");
        Res noB = t3.post("/events/order?id=" + noMintId + "&option=2&side=Buy&price=0.50&quantity=2");
        check("same crossing on a no-mint book: both orders rest, nothing minted",
                noA.has("\"restingQuantity\":2") && noB.has("\"restingQuantity\":2") && !noB.has("\"mint\":true"), noA, noB);
        check("participants are listed on the event", t3.get("/events/details?id=" + mintId).has(t2Name));

        section("close and payout math");
        Res close = mm.post("/events/close?id=" + lmsrId + "&winner=1");
        check("close LMSR with Yes", close.status() == 200 && close.has("\"winnerName\":\"Yes\""), close);
        check("20 winning shares: paid + commission = 20.00", near(close.num("totalPaidToWinners") + close.num("commissionCollected"), 20.0, 0.011), close);
        check("10% on-close commission = 2.00", near(close.num("commissionCollected"), 2.0, 0.011), close);
        Res t1After = t1.get("/users/details");
        check("T1 ledger has Payout and the event is closed in participations",
                t1After.has("Payout") && t1After.has("\"status\":\"Closed\""), t1After);
        check("buy after close is 400", t1.post("/events/buy?id=" + lmsrId + "&option=1&shares=1").status() == 400);
        check("events list shows the closed status", mm.get("/events").has("\"name\":\"" + lmsrName + "\"") && mm.get("/events").has("Closed"));

        section("concurrency: no lost updates, no 500s");
        double t3Before = t3.get("/users/details").num("balance");
        List<Res> loads = parallel(10, () -> t3.post("/users/funds?amount=10"));
        check("10 parallel loads all 200", loads.stream().allMatch(r -> r.status() == 200), loads.toArray(new Res[0]));
        double t3AfterLoads = t3.get("/users/details").num("balance");
        check("balance grew by exactly 100 (no lost update)", near(t3AfterLoads, t3Before + 100, 0.001));
        Res beforeBuys = mm.get("/events/details?id=" + blockId);
        long sharesBefore = beforeBuys.nums("totalShares").get(0).longValue();
        long versionBeforeBuys = beforeBuys.version();
        List<Res> buys = parallel(20, () -> {
            Client[] traders = {t1, t2, t3, mm};
            Client c = traders[(int) (Thread.currentThread().threadId() % traders.length)];
            return c.post("/events/buy?id=" + blockId + "&option=1&shares=1");
        });
        check("20 parallel buys all 200", buys.stream().allMatch(r -> r.status() == 200), buys.toArray(new Res[0]));
        Res afterBuys = mm.get("/events/details?id=" + blockId);
        check("Yes shares grew by exactly 20", afterBuys.nums("totalShares").get(0).longValue() == sharesBefore + 20, afterBuys);
        check("version advanced at least 20 times", afterBuys.version() >= versionBeforeBuys + 20);
        List<Res> reads = parallel(30, () -> t1.get("/events"));
        check("30 parallel reads all 200", reads.stream().allMatch(r -> r.status() == 200));
        List<Double> prices = afterBuys.nums("price");
        check("prices still sum to 1 after concurrent trading", near(prices.get(0) + prices.get(1), 1.0, 1e-6));

        section("sessions and chat");
        check("T1 logs out", t1.post("/logout").status() == 200);
        check("/me after logout is 401", t1.get("/me").status() == 401);
        check("MM sees T1 offline", connectedFlag(mm.get("/users").body(), t1Name) == Boolean.FALSE);
        check("same session can log in under another name", t1.login(t1Name + "b").status() == 200);
        Res fullChat = mm.get("/chat?version=0");
        Res farVersion = mm.get("/chat?version=999999");
        check("chat version ahead of the server (client outlived a restart) resyncs with the full list",
                farVersion.status() == 200 && count(farVersion.body(), "\"text\":") == count(fullChat.body(), "\"text\":"), farVersion);
        Res exactVersion = mm.get("/chat?version=" + fullChat.version());
        check("chat at the current version returns no entries", exactVersion.status() == 200 && count(exactVersion.body(), "\"text\":") == 0, exactVersion);
        Res badVersion = mm.get("/chat?version=abc");
        check("non-numeric chat version does not 500", badVersion.status() != 500, badVersion);
        check("chat message with unicode round-trips", mm.post("/chat?text=" + enc("shalom \u05e9\u05dc\u05d5\u05dd")).status() == 200
                && mm.get("/chat?version=0").has("\u05e9\u05dc\u05d5\u05dd"));

        section("crashed client: the name is released after 20 s of silence");
        Client crashed = new Client();
        String ghost = "EdgeGhost" + RUN;
        check("ghost logs in", crashed.login(ghost).status() == 200);
        Client second = new Client();
        check("immediately: the name is taken (409)", second.login(ghost).status() == 409);
        System.out.println("       waiting 21 s ...");
        Thread.sleep(21_000);
        Res takeover = second.login(ghost);
        check("after 21 s of silence the name can be taken over", takeover.status() == 200, takeover);
        check("the ghost is connected exactly once", connectedFlag(second.get("/users").body(), ghost) == Boolean.TRUE
                && count(second.get("/users").body(), "\"name\":\"" + ghost + "\"") == 1);

        section("cleanup");
        for (Client c : new Client[]{mm, t1, t2, t3, dana, second}) {
            c.post("/logout");
        }
        check("all edge users logged out", connectedFlag(anonLoginAndUsers(), mmName) == Boolean.FALSE);

        System.out.println();
        System.out.println("HTTP edge tests: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ helpers

    private static String anonLoginAndUsers() throws Exception {
        Client viewer = new Client();
        viewer.login("EdgeViewer" + RUN);
        String users = viewer.get("/users").body();
        viewer.post("/logout");
        return users;
    }

    private static List<Res> parallel(int n, Callable<Res> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<Res>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(task));
            }
            List<Res> out = new ArrayList<>();
            for (Future<Res> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdown();
        }
    }

    private static String listOf(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\":\\[([^\\]]*)\\]").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static boolean hasId(String list, int id) {
        for (String part : list.split(",")) {
            if (part.trim().equals(Integer.toString(id))) {
                return true;
            }
        }
        return false;
    }

    private static int idOf(String eventsJson, String name) {
        Matcher m = Pattern.compile("\"id\":(\\d+),\"name\":\"" + Pattern.quote(name) + "\"").matcher(eventsJson);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static boolean blockedFlag(String usersJson, String name) {
        Matcher m = Pattern.compile("\"name\":\"" + Pattern.quote(name) + "\",\"balance\":[^,]+,\"blocked\":(true|false)").matcher(usersJson);
        return m.find() && m.group(1).equals("true");
    }

    private static Boolean connectedFlag(String usersJson, String name) {
        Matcher m = Pattern.compile("\"name\":\"" + Pattern.quote(name) + "\",\"balance\":[^,]+,\"blocked\":(?:true|false),\"connected\":(true|false)").matcher(usersJson);
        return m.find() ? Boolean.valueOf(m.group(1)) : null;
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
