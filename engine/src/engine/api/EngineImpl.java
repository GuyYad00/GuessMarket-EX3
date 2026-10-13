package engine.api;

import engine.core.CommissionType;
import engine.core.Event;
import engine.core.LmsrTradeRecord;
import engine.core.Market;
import engine.core.MarketMethod;
import engine.core.OptionBook;
import engine.core.Order;
import engine.core.OrderSide;
import engine.core.Participation;
import engine.core.Transaction;
import engine.core.User;
import engine.core.mechanism.LmsrMechanism;
import engine.core.mechanism.OrderBookMechanism;
import engine.core.mechanism.TradeLedger;
import engine.exception.EngineOperationException;
import engine.exception.InvalidFileException;
import engine.jaxb.GmEventJaxb;
import engine.jaxb.GmOrderBookJaxb;
import engine.jaxb.GuessMarketJaxb;
import gm.dto.BuyResultDTO;
import gm.dto.CloseResultDTO;
import gm.dto.EventDTO;
import gm.dto.EventDetailsDTO;
import gm.dto.EventsListDTO;
import gm.dto.FundsResultDTO;
import gm.dto.LmsrTradeDTO;
import gm.dto.LoginResultDTO;
import gm.dto.OpenResultDTO;
import gm.dto.OptionStateDTO;
import gm.dto.OrderBookOptionDTO;
import gm.dto.OrderDTO;
import gm.dto.OrderFillDTO;
import gm.dto.OrderResultDTO;
import gm.dto.ParticipantDTO;
import gm.dto.TransactionDTO;
import gm.dto.UploadResultDTO;
import gm.dto.UserDTO;
import gm.dto.UserDetailsDTO;
import gm.dto.UserParticipationDTO;
import gm.dto.UsersListDTO;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The concrete engine. Holds the single {@link Market} of the server and
 * orchestrates all money and share movements, while delegating the pure LMSR
 * math and order-book matching to the mechanism classes (unchanged from
 * exercise 2).
 * <p>
 * Exercise-3 additions: login/logout bookkeeping, accumulating file uploads
 * with the uploader as market maker, event-name uniqueness, loading funds, a
 * per-user ledger line for every balance change, and a state version counter.
 */
public class EngineImpl implements Engine {

    private static final int MAX_COMMISSION = 90;
    private static final int OPTIONS_PER_EVENT = 2;
    private static final int MAX_USER_NAME_LENGTH = 40;

    private final Market market = new Market();
    private long version = 0;

    // ==================== Users / sessions ====================

    @Override
    public LoginResultDTO login(String userName) {
        String name = normaliseUserName(userName);
        User user;
        if (market.containsUser(name)) {
            user = market.getUserByName(name);
            if (user.isConnected()) {
                throw new EngineOperationException("The user name '" + name
                        + "' is already connected. Please choose a different name.");
            }
        } else {
            user = new User(name);
            market.addUser(user);
        }
        user.setConnected(true);
        bumpVersion();
        return new LoginResultDTO(name, "Welcome, " + name + "!");
    }

    private String normaliseUserName(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            throw new EngineOperationException("The user name cannot be empty.");
        }
        String name = userName.trim();
        if (name.length() > MAX_USER_NAME_LENGTH) {
            throw new EngineOperationException("The user name is too long (maximum "
                    + MAX_USER_NAME_LENGTH + " characters).");
        }
        return name;
    }

    @Override
    public void logout(String userName) {
        if (userName != null && market.containsUser(userName)) {
            market.getUserByName(userName).setConnected(false);
            bumpVersion();
        }
    }

    @Override
    public boolean isUserRegistered(String userName) {
        return market.containsUser(userName);
    }

    // ==================== File upload + validation ====================

    @Override
    public UploadResultDTO uploadEvents(InputStream xmlContent, String uploaderName) {
        User uploader = market.getUserByName(uploaderName);
        requireNotBlocked(uploader);

        GuessMarketJaxb root = unmarshal(xmlContent);
        List<Event> newEvents = buildValidatedEvents(root);

        // Everything validated: only now does the system change (all or nothing).
        List<String> names = new ArrayList<>();
        for (Event event : newEvents) {
            event.setMarketMakerName(uploader.getName());
            market.addEvent(event);
            uploader.addMarketMakerEvent(event.getId());
            names.add(event.getName());
        }
        bumpVersion();
        String message = newEvents.size() == 1
                ? "The file was loaded successfully. 1 event was added and you are its market maker."
                : "The file was loaded successfully. " + newEvents.size()
                + " events were added and you are their market maker.";
        return new UploadResultDTO(message, newEvents.size(), names);
    }

    private GuessMarketJaxb unmarshal(InputStream xmlContent) {
        if (xmlContent == null) {
            throw new InvalidFileException("No file content was received.");
        }
        try {
            JAXBContext context = JAXBContext.newInstance(GuessMarketJaxb.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (GuessMarketJaxb) unmarshaller.unmarshal(xmlContent);
        } catch (JAXBException e) {
            throw new InvalidFileException("The file could not be read as a valid Guess Market XML file. "
                    + "Make sure it is a well-formed XML that matches the exercise-3 schema (GM-EX3-Schema.xsd).", e);
        } catch (ClassCastException e) {
            throw new InvalidFileException("The file's root element is not 'Guess-Market'.", e);
        }
    }

    private List<Event> buildValidatedEvents(GuessMarketJaxb root) {
        if (root == null || root.getEvents() == null || root.getEvents().getEventList().isEmpty()) {
            throw new InvalidFileException("The file contains no events (missing or empty 'GM-events' section).");
        }
        List<Event> result = new ArrayList<>();
        Set<String> namesInFile = new HashSet<>();
        int nextId = market.peekNextEventId();
        for (GmEventJaxb jaxbEvent : root.getEvents().getEventList()) {
            String name = requireName(jaxbEvent);
            if (market.containsEventNamed(name)) {
                throw new InvalidFileException("An event named '" + name
                        + "' already exists in the system. Event names must be unique.");
            }
            if (!namesInFile.add(Market.normalise(name))) {
                throw new InvalidFileException("The event name '" + name
                        + "' appears more than once in the file. Event names must be unique.");
            }
            result.add(buildValidatedEvent(jaxbEvent, name, nextId++));
        }
        return result;
    }

    private Event buildValidatedEvent(GmEventJaxb jaxbEvent, String name, int id) {
        if (jaxbEvent.getDescription() == null || jaxbEvent.getDescription().trim().isEmpty()) {
            throw new InvalidFileException("Event '" + name + "' has no description.");
        }
        if (jaxbEvent.getCommission() == null) {
            throw new InvalidFileException("Event '" + name + "' has no commission definition.");
        }
        int commission = jaxbEvent.getCommission().getPercentage();
        if (commission < 0 || commission > MAX_COMMISSION) {
            throw new InvalidFileException("Event '" + name + "' has an illegal commission of " + commission
                    + ". It must be between 0 and " + MAX_COMMISSION + " (inclusive).");
        }
        CommissionType commissionType = parseCommissionType(jaxbEvent, name);
        List<String> optionNames = extractOptionNames(jaxbEvent, name);
        return buildByMethod(jaxbEvent, id, name, commission, commissionType, optionNames);
    }

    private Event buildByMethod(GmEventJaxb jaxbEvent, int id, String name, int commission,
                                CommissionType commissionType, List<String> optionNames) {
        if (jaxbEvent.getMethod() == null) {
            throw new InvalidFileException("Event '" + name + "' has no trading method (GM-method).");
        }
        boolean hasLmsr = jaxbEvent.getMethod().getLmsr() != null;
        boolean hasOrderBook = jaxbEvent.getMethod().getOrderBook() != null;
        if (hasLmsr == hasOrderBook) {
            throw new InvalidFileException("Event '" + name
                    + "' must define exactly one trading method, either GM-LMSR or GM-order-book.");
        }

        if (hasLmsr) {
            int b = jaxbEvent.getMethod().getLmsr().getB();
            if (b <= 0) {
                throw new InvalidFileException("Event '" + name + "' has an illegal liquidity value b=" + b
                        + ". It must be a positive whole number.");
            }
            return Event.lmsrEvent(id, name, jaxbEvent.getDescription().trim(),
                    commission, commissionType, optionNames, new LmsrMechanism(b));
        }

        GmOrderBookJaxb ob = jaxbEvent.getMethod().getOrderBook();
        if (ob.getD() <= 0) {
            throw new InvalidFileException("Event '" + name + "' has an illegal base value d=" + ob.getD()
                    + ". It must be a positive whole number.");
        }
        if (ob.getInitial() < 0) {
            throw new InvalidFileException("Event '" + name + "' has a negative initial amount ("
                    + ob.getInitial() + ").");
        }
        boolean allowMint = parseAllowMint(ob, name);
        return Event.orderBookEvent(id, name, jaxbEvent.getDescription().trim(),
                commission, commissionType, optionNames,
                new OrderBookMechanism(ob.getD(), ob.getInitial(), allowMint));
    }

    private CommissionType parseCommissionType(GmEventJaxb jaxbEvent, String name) {
        try {
            return CommissionType.fromXmlValue(jaxbEvent.getCommission().getType());
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException("Event '" + name + "' has an unknown commission type '"
                    + jaxbEvent.getCommission().getType() + "'. Allowed values: 'on-close', 'on-purchase'.");
        }
    }

    private boolean parseAllowMint(GmOrderBookJaxb ob, String name) {
        String value = ob.getAllowMint() == null ? "" : ob.getAllowMint().trim();
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new InvalidFileException("Event '" + name + "' has an illegal allow-mint value '"
                + ob.getAllowMint() + "'. It must be 'true' or 'false'.");
    }

    private List<String> extractOptionNames(GmEventJaxb jaxbEvent, String eventName) {
        int found = (jaxbEvent.getOptions() == null) ? 0 : jaxbEvent.getOptions().getOptionList().size();
        if (found != OPTIONS_PER_EVENT) {
            throw new InvalidFileException("Event '" + eventName + "' must have exactly " + OPTIONS_PER_EVENT
                    + " options, but " + found + (found == 1 ? " was" : " were") + " found.");
        }
        List<String> optionNames = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String option : jaxbEvent.getOptions().getOptionList()) {
            String trimmed = (option == null) ? "" : option.trim();
            if (trimmed.isEmpty()) {
                throw new InvalidFileException("Event '" + eventName + "' contains an empty option name.");
            }
            if (!seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                throw new InvalidFileException("Event '" + eventName + "' contains the option '" + trimmed
                        + "' twice. The two options must differ.");
            }
            optionNames.add(trimmed);
        }
        return optionNames;
    }

    private String requireName(GmEventJaxb jaxbEvent) {
        if (jaxbEvent.getName() == null || jaxbEvent.getName().trim().isEmpty()) {
            throw new InvalidFileException("An event with no name was found. Every event must have a name.");
        }
        return jaxbEvent.getName().trim();
    }

    // ==================== Queries ====================

    @Override
    public long getVersion() {
        return version;
    }

    private void bumpVersion() {
        version++;
    }

    @Override
    public EventsListDTO getEvents() {
        List<EventDTO> result = new ArrayList<>();
        for (Event event : market.getAllEvents()) {
            result.add(toEventDTO(event));
        }
        return new EventsListDTO(version, result);
    }

    @Override
    public UsersListDTO getUsers() {
        List<UserDTO> result = new ArrayList<>();
        for (User user : market.getAllUsers()) {
            result.add(new UserDTO(user.getName(), user.getBalance(), user.isBlocked(), user.isConnected(),
                    new ArrayList<>(user.getMarketMakerEventIds()), user.getParticipations().size()));
        }
        return new UsersListDTO(version, result);
    }

    @Override
    public EventDetailsDTO getEventDetails(int eventId) {
        Event event = market.getEventById(eventId);
        EventDTO meta = toEventDTO(event);
        String winnerName = event.isClosed() ? event.getOptionNames().get(event.getWinnerIndex()) : null;

        List<List<Double>> priceHistory = new ArrayList<>();
        for (int i = 0; i < event.getOptionCount(); i++) {
            priceHistory.add(event.getPriceHistory(i));
        }

        if (event.getMethod() == MarketMethod.LMSR) {
            return new EventDetailsDTO(version, meta, buildLmsrOptionStates(event),
                    toLmsrTradeDTOs(event.getLmsrTradesNewestFirst()), null, null, priceHistory, winnerName);
        }
        return new EventDetailsDTO(version, meta, null, null, buildBooks(event),
                buildParticipants(event), priceHistory, winnerName);
    }

    private List<OptionStateDTO> buildLmsrOptionStates(Event event) {
        LmsrMechanism lmsr = event.getLmsr();
        List<OptionStateDTO> states = new ArrayList<>();
        for (int i = 0; i < event.getOptionCount(); i++) {
            states.add(new OptionStateDTO(event.getOptionNames().get(i), lmsr.price(i), lmsr.getTotalShares(i)));
        }
        return states;
    }

    private List<OrderBookOptionDTO> buildBooks(Event event) {
        OrderBookMechanism ob = event.getOrderBook();
        List<OrderBookOptionDTO> books = new ArrayList<>();
        for (int i = 0; i < event.getOptionCount(); i++) {
            OptionBook book = ob.getBook(i);
            Double last = centsToDollars(book.getLastTradeCents());
            Double bid = centsToDollars(book.getBestBidCents());
            Double ask = centsToDollars(book.getBestAskCents());
            Double mid = (bid != null && ask != null) ? (bid + ask) / 2.0 : null;
            Double spread = (bid != null && ask != null) ? (ask - bid) : null;
            books.add(new OrderBookOptionDTO(event.getOptionNames().get(i),
                    toOrderDTOs(book.getBidsForDisplay()), toOrderDTOs(book.getAsksForDisplay()),
                    last, bid, ask, mid, spread));
        }
        return books;
    }

    private List<ParticipantDTO> buildParticipants(Event event) {
        OrderBookMechanism ob = event.getOrderBook();
        double[] referencePrice = new double[event.getOptionCount()];
        for (int i = 0; i < event.getOptionCount(); i++) {
            referencePrice[i] = referencePriceDollars(ob.getBook(i));
        }

        List<ParticipantDTO> participants = new ArrayList<>();
        for (User user : market.getAllUsers()) {
            Participation p = user.existingParticipation(event.getId());
            boolean holds = p != null && p.holdsAnything();
            boolean hasOrders = userHasOrders(ob, user.getName());
            if (!holds && !hasOrders) {
                continue;
            }
            List<Long> shares = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            for (int i = 0; i < event.getOptionCount(); i++) {
                long owned = (p == null) ? 0 : p.getHoldings(i);
                shares.add(owned);
                values.add(owned * referencePrice[i]);
            }
            participants.add(new ParticipantDTO(user.getName(), shares, values));
        }
        return participants;
    }

    private boolean userHasOrders(OrderBookMechanism ob, String userName) {
        for (int i = 0; i < ob.getOptionCount(); i++) {
            OptionBook book = ob.getBook(i);
            if (book.reservedSellShares(userName) > 0) {
                return true;
            }
            for (Order bid : book.getBidsForDisplay()) {
                if (bid.getUserName().equals(userName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double referencePriceDollars(OptionBook book) {
        if (book.getLastTradeCents() != null) {
            return book.getLastTradeCents() / 100.0;
        }
        Long bid = book.getBestBidCents();
        Long ask = book.getBestAskCents();
        if (bid != null && ask != null) {
            return (bid + ask) / 200.0;
        }
        if (bid != null) {
            return bid / 100.0;
        }
        if (ask != null) {
            return ask / 100.0;
        }
        return 0.0;
    }

    @Override
    public UserDetailsDTO getUserDetails(String userName) {
        User user = market.getUserByName(userName);

        List<UserParticipationDTO> participations = new ArrayList<>();
        for (Participation p : user.getParticipations()) {
            participations.add(toUserParticipationDTO(user, p));
        }
        List<TransactionDTO> transactions = new ArrayList<>();
        for (Transaction t : user.getTransactions()) {
            transactions.add(new TransactionDTO(t.getNumber(), t.getType(), t.getDescription(),
                    t.getAmount(), t.getBalanceAfter()));
        }
        return new UserDetailsDTO(version, user.getName(), user.isConnected(), user.getTotalLoaded(),
                user.getBalance(), user.isBlocked(), new ArrayList<>(user.getMarketMakerEventIds()),
                participations, transactions, user.getBalanceHistory());
    }

    private UserParticipationDTO toUserParticipationDTO(User user, Participation p) {
        Event event = market.getEventById(p.getEventId());
        boolean isMm = user.isMarketMakerOf(event.getId());
        String status = event.getStatus().getDisplayName();
        String winnerName = event.isClosed() ? event.getOptionNames().get(event.getWinnerIndex()) : null;

        if (event.getMethod() == MarketMethod.LMSR) {
            return new UserParticipationDTO(event.getId(), event.getName(), event.getMethod().getDisplayName(),
                    status, isMm, event.getOptionNames(), toLmsrTradeDTOs(p.getTradesNewestFirst()),
                    null, null, null, winnerName);
        }

        List<Long> holdings = new ArrayList<>();
        List<Double> paid = new ArrayList<>();
        for (int i = 0; i < event.getOptionCount(); i++) {
            holdings.add(p.getHoldings(i));
            paid.add(p.getPaid(i));
        }
        Double profitLoss = event.isClosed() ? p.getCashFlow() : null;
        return new UserParticipationDTO(event.getId(), event.getName(), event.getMethod().getDisplayName(),
                status, isMm, event.getOptionNames(), null, holdings, paid, profitLoss, winnerName);
    }

    // ==================== Funds ====================

    @Override
    public FundsResultDTO loadFunds(String userName, double amount) {
        User user = market.getUserByName(userName);
        requireNotBlocked(user);
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new EngineOperationException("The amount to load must be a positive number.");
        }
        double rounded = Math.round(amount * 100.0) / 100.0;
        if (rounded <= 0) {
            throw new EngineOperationException("The amount to load must be at least 0.01.");
        }
        user.loadFunds(rounded);
        bumpVersion();
        return new FundsResultDTO(rounded, user.getBalance());
    }

    // ==================== Opening / closing events ====================

    @Override
    public OpenResultDTO openEvent(int eventId, String marketMakerName) {
        Event event = market.getEventById(eventId);
        User mm = market.getUserByName(marketMakerName);
        requireNotBlocked(mm);
        requireMarketMaker(mm, event, "open");

        if (event.getMethod() == MarketMethod.LMSR) {
            double subsidy = event.getLmsr().subsidy();
            requireFunds(mm, subsidy, "open this event (LMSR subsidy)");
            event.open();
            mm.apply("Open event", "Opened '" + event.getName() + "' (LMSR subsidy)", -subsidy);
            event.addToAccount(subsidy);
            mm.participationFor(eventId, event.getOptionCount()).addCashFlow(-subsidy);
            event.recordPrice(0, event.getLmsr().price(0));
            event.recordPrice(1, event.getLmsr().price(1));
            bumpVersion();
            return new OpenResultDTO(eventId, MarketMethod.LMSR.getDisplayName(), subsidy, mm.getBalance());
        }

        OrderBookMechanism ob = event.getOrderBook();
        int initial = ob.getInitial();
        requireFunds(mm, initial, "open this event (order-book initial purchase)");
        event.open();
        long pairs = ob.getD() == 0 ? 0 : initial / ob.getD();
        mm.apply("Open event", "Opened '" + event.getName() + "' (order-book initial purchase of "
                + pairs + " share pairs)", -initial);
        event.addToAccount(initial);
        Participation p = mm.participationFor(eventId, event.getOptionCount());
        for (int i = 0; i < event.getOptionCount(); i++) {
            p.addShares(i, pairs);
            p.addPaid(i, initial / 2.0);
        }
        p.addCashFlow(-initial);
        bumpVersion();
        return new OpenResultDTO(eventId, MarketMethod.ORDER_BOOK.getDisplayName(), initial, mm.getBalance());
    }

    @Override
    public CloseResultDTO closeEvent(int eventId, int winningOptionNumber, String marketMakerName) {
        Event event = market.getEventById(eventId);
        User mm = market.getUserByName(marketMakerName);
        requireNotBlocked(mm);
        requireMarketMaker(mm, event, "close");
        int winnerIndex = toValidatedOptionIndex(event, winningOptionNumber);
        event.requireActive("close");
        String winnerName = event.getOptionNames().get(winnerIndex);

        double perSharePayout = (event.getMethod() == MarketMethod.LMSR)
                ? LmsrMechanism.PAYOUT_PER_SHARE : event.getOrderBook().getD();

        double totalNetToWinners = 0;
        double totalCommission = 0;
        for (User user : market.getAllUsers()) {
            Participation p = user.existingParticipation(eventId);
            if (p == null) {
                continue;
            }
            long winningShares = p.getHoldings(winnerIndex);
            if (winningShares <= 0) {
                continue;
            }
            double gross = winningShares * perSharePayout;
            double commission = (event.getCommissionType() == CommissionType.ON_CLOSE)
                    ? gross * event.getCommissionPercentage() / 100.0 : 0;
            double net = gross - commission;
            user.apply("Payout", "Payout for " + winningShares + " winning shares of '" + winnerName
                    + "' in '" + event.getName() + "'" + (commission > 0
                    ? " (after " + money(commission) + " on-close commission)" : ""), net);
            event.addToAccount(-gross);
            p.addCashFlow(net);
            totalNetToWinners += net;
            totalCommission += commission;
        }

        double leftover = event.getAccountBalance();
        if (totalCommission != 0 || leftover != 0) {
            mm.apply("Event closed", "Closed '" + event.getName() + "': commission " + money(totalCommission)
                    + " + remaining event account " + money(leftover), totalCommission + leftover);
            mm.participationFor(eventId, event.getOptionCount()).addCashFlow(totalCommission + leftover);
        }
        event.addToAccount(-leftover);

        event.close(winnerIndex);
        bumpVersion();
        return new CloseResultDTO(winnerName, totalNetToWinners, totalCommission, leftover);
    }

    // ==================== LMSR trading ====================

    @Override
    public BuyResultDTO buyLmsrShares(int eventId, String userName, int optionNumber, long shares) {
        Event event = market.getEventById(eventId);
        requireMethod(event, MarketMethod.LMSR);
        User user = market.getUserByName(userName);
        requireNotBlocked(user);
        event.requireActive("buy shares");

        int optionIndex = toValidatedOptionIndex(event, optionNumber);
        if (shares <= 0) {
            throw new EngineOperationException("The number of shares to buy must be positive (got " + shares + ").");
        }
        LmsrMechanism lmsr = event.getLmsr();
        if (lmsr.wouldOverflow(optionIndex, shares)) {
            throw new EngineOperationException("Buying " + shares
                    + " more shares would exceed the maximum this option can hold.");
        }

        double cost = lmsr.applyBuy(optionIndex, shares);
        double commission = (event.getCommissionType() == CommissionType.ON_PURCHASE)
                ? cost * event.getCommissionPercentage() / 100.0 : 0;

        String optionName = event.getOptionNames().get(optionIndex);
        user.apply("Buy", "Bought " + shares + " shares of '" + optionName + "' in '" + event.getName()
                + "' (cost " + money(cost) + (commission > 0 ? " + commission " + money(commission) : "") + ")",
                -(cost + commission));
        event.addToAccount(cost);
        creditMarketMaker(event, commission, user.getName());

        Participation p = user.participationFor(eventId, event.getOptionCount());
        p.addShares(optionIndex, shares);
        p.addPaid(optionIndex, cost + commission);
        p.addCashFlow(-(cost + commission));
        LmsrTradeRecord record = new LmsrTradeRecord(user.getName(), optionName, shares, cost, commission);
        p.addLmsrTrade(record);
        event.addLmsrTrade(record);

        event.recordPrice(0, lmsr.price(0));
        event.recordPrice(1, lmsr.price(1));

        String message = applyBlockingIfNeeded(user);
        bumpVersion();
        return new BuyResultDTO(optionName, shares, cost, commission, buildLmsrOptionStates(event),
                user.getBalance(), user.isBlocked(), message);
    }

    // ==================== Order-book trading ====================

    @Override
    public OrderResultDTO placeOrder(int eventId, String userName, int optionNumber,
                                     String sideText, double price, long quantity) {
        Event event = market.getEventById(eventId);
        requireMethod(event, MarketMethod.ORDER_BOOK);
        User user = market.getUserByName(userName);
        requireNotBlocked(user);
        event.requireActive("place an order");

        OrderSide side = parseSide(sideText);
        int optionIndex = toValidatedOptionIndex(event, optionNumber);
        if (quantity <= 0) {
            throw new EngineOperationException("The order quantity must be positive (got " + quantity + ").");
        }
        if (Double.isNaN(price) || Double.isInfinite(price)) {
            throw new EngineOperationException("The price must be a valid number.");
        }
        OrderBookMechanism ob = event.getOrderBook();
        long priceCents = Math.round(price * 100.0);
        long maxPriceCents = ob.baseValueCents() - 1;
        if (priceCents < 1 || priceCents > maxPriceCents) {
            throw new EngineOperationException("The price must be between 0.01 and "
                    + String.format(Locale.US, "%.2f", maxPriceCents / 100.0)
                    + " (strictly below the base value d=" + ob.getD() + ").");
        }
        if (side == OrderSide.SELL) {
            requireEnoughSharesToSell(user, ob, eventId, optionIndex, quantity);
        }

        OrderBookLedger ledger = new OrderBookLedger(event);
        long resting = ob.process(optionIndex, side, priceCents, quantity, user.getName(), ledger);
        recordTradedPrices(event, ledger.touchedOptions);

        String message = buildOrderMessage(user, ledger.blockedNow);
        bumpVersion();
        return new OrderResultDTO(ledger.fills, resting, user.getBalance(), user.isBlocked(), message);
    }

    private OrderSide parseSide(String sideText) {
        if (sideText != null) {
            for (OrderSide side : OrderSide.values()) {
                if (side.name().equalsIgnoreCase(sideText.trim())
                        || side.getDisplayName().equalsIgnoreCase(sideText.trim())) {
                    return side;
                }
            }
        }
        throw new EngineOperationException("The order side must be 'Buy' or 'Sell' (got '" + sideText + "').");
    }

    private void requireEnoughSharesToSell(User user, OrderBookMechanism ob, int eventId,
                                           int optionIndex, long quantity) {
        Participation p = user.existingParticipation(eventId);
        long owned = (p == null) ? 0 : p.getHoldings(optionIndex);
        long reserved = ob.reservedSellShares(user.getName(), optionIndex);
        long available = owned - reserved;
        if (available < quantity) {
            throw new EngineOperationException("User '" + user.getName() + "' cannot sell " + quantity
                    + " shares. Only " + Math.max(available, 0) + " are available to sell.");
        }
    }

    private void recordTradedPrices(Event event, Set<Integer> touchedOptions) {
        for (int optionIndex : touchedOptions) {
            Long last = event.getOrderBook().getBook(optionIndex).getLastTradeCents();
            if (last != null) {
                event.recordPrice(optionIndex, last / 100.0);
            }
        }
    }

    /**
     * The ledger that applies the money and share movements for every fill the
     * order-book matcher reports, charges commission to the market maker and
     * enforces the negative-balance blocking rule. Every movement also writes
     * one line into the affected users' account ledgers.
     */
    private final class OrderBookLedger implements TradeLedger {

        private final Event event;
        private final List<OrderFillDTO> fills = new ArrayList<>();
        private final Set<String> blockedNow = new HashSet<>();
        private final Set<Integer> touchedOptions = new HashSet<>();

        private OrderBookLedger(Event event) {
            this.event = event;
        }

        @Override
        public void executeTrade(int optionIndex, long quantity, long priceCents,
                                 String buyerName, String sellerName) {
            double value = priceCents * quantity / 100.0;
            double commission = purchaseCommission(value);
            String optionName = event.getOptionNames().get(optionIndex);

            User buyer = market.getUserByName(buyerName);
            User seller = market.getUserByName(sellerName);

            buyer.apply("Buy (order)", "Bought " + quantity + " shares of '" + optionName + "' in '"
                    + event.getName() + "' at " + money(priceCents / 100.0) + " from " + sellerName
                    + (commission > 0 ? " (incl. commission " + money(commission) + ")" : ""),
                    -(value + commission));
            seller.apply("Sell (order)", "Sold " + quantity + " shares of '" + optionName + "' in '"
                    + event.getName() + "' at " + money(priceCents / 100.0) + " to " + buyerName, value);
            creditMarketMaker(event, commission, buyerName);

            Participation buyerP = buyer.participationFor(event.getId(), event.getOptionCount());
            buyerP.addShares(optionIndex, quantity);
            buyerP.addPaid(optionIndex, value + commission);
            buyerP.addCashFlow(-(value + commission));

            Participation sellerP = seller.participationFor(event.getId(), event.getOptionCount());
            sellerP.removeShares(optionIndex, quantity);
            sellerP.addCashFlow(value);

            fills.add(new OrderFillDTO(false, optionName, quantity, priceCents / 100.0, buyerName, sellerName));
            touchedOptions.add(optionIndex);
            blockIfNegative(buyer);
        }

        @Override
        public void executeMint(long quantity,
                                int incomingOption, String incomingBuyer, long incomingPriceCents,
                                int restingOption, String restingBuyer, long restingPriceCents) {
            applyMintLeg(incomingOption, incomingBuyer, incomingPriceCents, quantity);
            applyMintLeg(restingOption, restingBuyer, restingPriceCents, quantity);
            event.addToAccount((incomingPriceCents + restingPriceCents) * quantity / 100.0);
        }

        private void applyMintLeg(int optionIndex, String buyerName, long priceCents, long quantity) {
            double value = priceCents * quantity / 100.0;
            double commission = purchaseCommission(value);
            String optionName = event.getOptionNames().get(optionIndex);
            User buyer = market.getUserByName(buyerName);

            buyer.apply("Buy (mint)", "Minted " + quantity + " shares of '" + optionName + "' in '"
                    + event.getName() + "' at " + money(priceCents / 100.0)
                    + (commission > 0 ? " (incl. commission " + money(commission) + ")" : ""),
                    -(value + commission));
            creditMarketMaker(event, commission, buyerName);

            Participation buyerP = buyer.participationFor(event.getId(), event.getOptionCount());
            buyerP.addShares(optionIndex, quantity);
            buyerP.addPaid(optionIndex, value + commission);
            buyerP.addCashFlow(-(value + commission));

            fills.add(new OrderFillDTO(true, optionName, quantity, priceCents / 100.0, buyerName, null));
            touchedOptions.add(optionIndex);
            blockIfNegative(buyer);
        }

        private double purchaseCommission(double value) {
            return (event.getCommissionType() == CommissionType.ON_PURCHASE)
                    ? value * event.getCommissionPercentage() / 100.0 : 0;
        }

        private void blockIfNegative(User user) {
            if (user.getBalance() < 0 && !user.isBlocked()) {
                user.block();
                blockedNow.add(user.getName());
            }
        }
    }

    // ==================== Shared helpers ====================

    private EventDTO toEventDTO(Event event) {
        return new EventDTO(event.getId(), event.getName(), event.getDescription(),
                event.getCommissionPercentage(), event.getCommissionType().getDisplayName(),
                event.getMethod().getDisplayName(), event.getStatus().getDisplayName(),
                event.getOptionNames(), event.getAccountBalance(), event.getMarketMakerName());
    }

    private List<LmsrTradeDTO> toLmsrTradeDTOs(List<LmsrTradeRecord> records) {
        List<LmsrTradeDTO> result = new ArrayList<>();
        for (LmsrTradeRecord record : records) {
            result.add(new LmsrTradeDTO(record.getUserName(), record.getOptionName(),
                    record.getShares(), record.getCost(), record.getCommissionPaid()));
        }
        return result;
    }

    private List<OrderDTO> toOrderDTOs(List<Order> orders) {
        List<OrderDTO> result = new ArrayList<>();
        for (Order order : orders) {
            result.add(new OrderDTO(order.getUserName(), order.getSide().getDisplayName(),
                    order.getPriceCents() / 100.0, order.getRemaining()));
        }
        return result;
    }

    private void creditMarketMaker(Event event, double commission, String payerName) {
        if (commission == 0) {
            return;
        }
        User mm = market.getUserByName(event.getMarketMakerName());
        mm.apply("Commission", "Commission received from " + payerName + " in '" + event.getName() + "'",
                commission);
        mm.participationFor(event.getId(), event.getOptionCount()).addCashFlow(commission);
    }

    private String applyBlockingIfNeeded(User user) {
        if (user.getBalance() < 0 && !user.isBlocked()) {
            user.block();
            return blockedMessage(user.getName());
        }
        return null;
    }

    private String buildOrderMessage(User actingUser, Set<String> blockedNow) {
        if (blockedNow.isEmpty()) {
            return null;
        }
        if (blockedNow.contains(actingUser.getName()) && blockedNow.size() == 1) {
            return blockedMessage(actingUser.getName());
        }
        return "The following users went into a negative balance and are now blocked: "
                + String.join(", ", blockedNow) + ".";
    }

    private String blockedMessage(String name) {
        return "User '" + name + "' has gone into a negative balance and is now blocked from any further action.";
    }

    private static String money(double amount) {
        return String.format(Locale.US, "$%,.2f", amount);
    }

    private Double centsToDollars(Long cents) {
        return cents == null ? null : cents / 100.0;
    }

    private int toValidatedOptionIndex(Event event, int optionNumber) {
        if (optionNumber < 1 || optionNumber > event.getOptionCount()) {
            throw new EngineOperationException("Option number " + optionNumber + " does not exist in event '"
                    + event.getName() + "'. Choose a number between 1 and " + event.getOptionCount() + ".");
        }
        return optionNumber - 1;
    }

    private void requireMethod(Event event, MarketMethod expected) {
        if (event.getMethod() != expected) {
            throw new EngineOperationException("Event '" + event.getName() + "' is a "
                    + event.getMethod().getDisplayName() + " event; this operation requires a "
                    + expected.getDisplayName() + " event.");
        }
    }

    private void requireMarketMaker(User user, Event event, String action) {
        if (!user.isMarketMakerOf(event.getId())) {
            throw new EngineOperationException("Only the market maker of event '" + event.getName()
                    + "' may " + action + " it. '" + user.getName() + "' is not its market maker.");
        }
    }

    private void requireNotBlocked(User user) {
        if (user.isBlocked()) {
            throw new EngineOperationException("User '" + user.getName()
                    + "' is blocked (negative balance) and cannot perform any action.");
        }
    }

    private void requireFunds(User user, double amount, String action) {
        if (user.getBalance() < amount) {
            throw new EngineOperationException("User '" + user.getName() + "' does not have enough money to "
                    + action + " (needs " + money(amount) + ", has " + money(user.getBalance()) + ").");
        }
    }
}
