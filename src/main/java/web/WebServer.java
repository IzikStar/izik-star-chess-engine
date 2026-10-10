package web;

import cloud.CloudConfig;
import cloud.CloudSync;
import cloud.D1;
import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import game.GameArchive;
import game.GameConfig;
import game.GameSession;
import game.PieceBank;
import game.VariantStore;
import io.javalin.Javalin;
import lab.HallOfFame;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The application's entry point since Phase 4c: a local web server for the browser UI.
 *
 * <p>It serves the React app built into the jar under {@code /webapp} and one WebSocket at {@code /ws} that carries the game (see {@link GameHub}
 * for the protocol). It listens on 127.0.0.1 only, so nothing outside this computer can reach it,
 * unless started with {@code --lan}, and opens the default browser on start.
 *
 * <p>It also serves the lab page's API over the evolution runs in a folder ({@link LabApi}) and
 * game analysis with Stockfish ({@link AnalysisApi}), the live evaluation bar ({@link EvalApi}), the player's saved games ({@link GamesApi}),
 * the variants ({@link VariantsApi}) and their health check ({@link HealthApi}).
 *
 * <p>Arguments: {@code --port N} (default 7070, then the next free one up to 7079),
 * {@code --no-browser}, {@code --runs DIR} (the evolution runs, default {@code runs}), {@code --games DIR}
 * (where the player's games are saved, default {@code games}), {@code --variants DIR} (the variants
 * the player made, default {@code variants}), {@code --lan} (listen on every network
 * interface, so a phone on the same Wi-Fi or tailnet can open the game; there is no password),
 * {@code --host ADDR} (listen on that one address only: a cloud server passes its Tailscale address,
 * so only the owner's own devices reach it; see docs/cloud-server.md).
 */
public final class WebServer {

    public static final int DEFAULT_PORT = 7070;
    /** Where this copy remembers what it last shared, in the working directory. */
    static final String SYNC_STATE = "cloud-sync.db";

    /** Listen on this computer only (the default). */
    static final String LOCAL_ONLY = "127.0.0.1";
    /** Listen on every network interface ({@code --lan}). */
    static final String ALL_INTERFACES = "0.0.0.0";

    private final Javalin app;
    private final GameHub hub;
    private final AnalysisApi analysis;
    private final EvalApi eval;
    private final HealthApi health;

    private WebServer(Javalin app, GameHub hub, AnalysisApi analysis, EvalApi eval, HealthApi health) {
        this.app = app;
        this.hub = hub;
        this.analysis = analysis;
        this.eval = eval;
        this.health = health;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean browser = true;
        Path runs = Path.of("runs");
        Path games = Path.of("games");
        Path variants = Path.of("variants");
        String host = LOCAL_ONLY;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> browser = false;
                case "--runs" -> runs = Path.of(args[++i]);
                case "--games" -> games = Path.of(args[++i]);
                case "--variants" -> variants = Path.of(args[++i]);
                case "--lan" -> host = ALL_INTERFACES;
                case "--host" -> host = args[++i];
                default -> {
                    System.err.println("unknown argument: " + args[i]
                            + " (use --port N, --no-browser, --runs DIR, --games DIR, --variants DIR, --lan, --host ADDR)");
                    System.exit(2);
                }
            }
        }
        Path runsDir = runs;
        Path gamesDir = games;
        Path variantsDir = variants;
        CloudSync sync = CloudConfig.load().map(c -> {
            System.out.println("Shared database: Cloudflare D1, this copy is \"" + c.copy() + "\"");
            return new CloudSync(new D1(c), c.copy(), Map.of("games", gamesDir, "variants", variantsDir,
                    "hall-of-fame", runsDir.resolve(HallOfFame.FOLDER)), runsDir, Path.of(SYNC_STATE));
        }).orElse(null);
        WebServer server = startOnFreePort(port, port == DEFAULT_PORT ? 10 : 1, runs, games, variants, host, sync);
        if (sync != null) {
            sync.start();
        }
        String url = "http://" + (host.equals(LOCAL_ONLY) || host.equals(ALL_INTERFACES) ? "localhost" : host)
                + ":" + server.port() + "/";
        System.out.println("IzikStar Chess is running at " + url + " (Ctrl+C to stop)");
        if (host.equals(ALL_INTERFACES)) {
            List<String> addresses = networkAddresses();
            System.out.println(addresses.isEmpty()
                    ? "--lan: no network address found; is this computer connected to a network?"
                    : "--lan: open it from your phone at " + addresses.stream()
                            .map(a -> "http://" + a + ":" + server.port() + "/")
                            .collect(Collectors.joining(" or ")));
            System.out.println("Anyone on the same network can open it too: there is no password.");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        if (browser) {
            openBrowser(url);
        }
    }

    private static WebServer startOnFreePort(int first, int attempts, Path runs, Path games, Path variants, String host,
                                             CloudSync sync) {
        RuntimeException last = null;
        for (int port = first; port < first + attempts; port++) {
            try {
                return start(port, defaultSession(), runs, games, variants, host, sync);
            } catch (RuntimeException e) {
                last = e; // port taken: try the next one
            }
        }
        throw last;
    }

    private static GameSession defaultSession(GameHub hub) {
        MinimaxEngine builtIn = new MinimaxEngine();
        hub.useBuiltIn(builtIn); // so a new game can play an evolved champion
        StockfishEngine stockfish = new StockfishEngine();
        hub.useStockfish(stockfish);
        System.out.println(stockfish.path() != null
                ? "Stockfish: " + stockfish.path()
                : "Stockfish not found: Levels 9-13 will play the built-in engine at Level 8. Download it from the game"
                        + " (New game, or Analyse) or put it in engine/ (see engine/README.md).");
        return new GameSession(GameConfig.defaults(), new EngineSelector(builtIn, stockfish), hub::execute);
    }

    /** A session factory, so tests can pass their own engines. */
    interface SessionFactory {
        GameSession create(GameHub hub);
    }

    private static SessionFactory defaultSession() {
        return WebServer::defaultSession;
    }

    /**
     * Starts a server on {@code port} (0 = any free port) around a new session, runs in {@code runs/},
     * games saved to a new temporary folder (for tests).
     */
    static WebServer start(int port, SessionFactory sessions) {
        return start(port, sessions, Path.of("runs"));
    }

    /** As {@link #start(int, SessionFactory)}, with the runs in {@code runs}. */
    static WebServer start(int port, SessionFactory sessions, Path runs) {
        try {
            return start(port, sessions, runs, Files.createTempDirectory("izikstar-games"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Starts a server on {@code port} (0 = any free port) around a new session, on this computer only. */
    static WebServer start(int port, SessionFactory sessions, Path runs, Path games) {
        return start(port, sessions, runs, games, LOCAL_ONLY);
    }

    /** As {@link #start(int, SessionFactory, Path, Path)}, listening on {@code host}; made variants next to the games. */
    static WebServer start(int port, SessionFactory sessions, Path runs, Path games, String host) {
        return start(port, sessions, runs, games, games.resolveSibling(games.getFileName() + "-variants"), host);
    }

    /** As {@link #start(int, SessionFactory, Path, Path, String)}, with the player's variants in {@code variants}. */
    static WebServer start(int port, SessionFactory sessions, Path runs, Path games, Path variants, String host) {
        return start(port, sessions, runs, games, variants, host, null);
    }

    /**
     * As {@link #start(int, SessionFactory, Path, Path, Path, String)}, sharing the runs, games and
     * variants through {@code sync} (null: this computer only).
     */
    static WebServer start(int port, SessionFactory sessions, Path runs, Path games, Path variants, String host,
                           CloudSync sync) {
        GameHub hub = new GameHub();
        VariantStore variantStore = new VariantStore(variants);
        hub.useVariants(variantStore);
        GameArchive archive = new GameArchive(games);
        hub.useArchive(archive);
        LabApi lab = new LabApi(runs);
        hub.useLab(lab);
        GameSession session = sessions.create(hub);
        hub.attach(session);

        Map<WsContext, GameHub.Client> clients = new ConcurrentHashMap<>();
        boolean uiBuilt = WebServer.class.getResource("/webapp/index.html") != null;

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            if (uiBuilt) {
                config.staticFiles.add(files -> {
                    files.hostedPath = "/";
                    files.directory = "/webapp";
                    files.location = Location.CLASSPATH;
                });
            }
            // a player can think for a long time; don't let Jetty drop an idle game connection
            config.jetty.modifyWebSocketServletFactory(factory -> factory.setIdleTimeout(Duration.ofHours(12)));
        });
        if (!uiBuilt) {
            app.get("/", ctx -> ctx.html("<p>The web UI is not built into this jar. Run <code>mvn package</code>, "
                    + "or <code>npm run dev</code> in <code>web/</code> during development.</p>"));
        }
        lab.routes(app);
        LabJobs jobs = new LabJobs(lab, variantStore);
        if (sync != null) {
            jobs.useReplicas(file -> sync.isReplica(file) ? sync.origin(file) : null);
        }
        jobs.routes(app);
        app.get("/api/sync", ctx -> ctx.contentType("application/json").result(sync == null
                ? "{\"enabled\":false}" : sync.status().toString()));
        AnalysisApi analysis = new AnalysisApi();
        analysis.routes(app);
        EvalApi eval = new EvalApi();
        eval.routes(app);
        new StockfishApi(hub::stockfish, hub::refresh).routes(app);
        new GamesApi(archive).routes(app);
        new VariantsApi(variantStore).routes(app);
        new PieceBankApi(new PieceBank(variants.resolve("piece-bank"))).routes(app);
        HealthApi health = new HealthApi();
        health.routes(app);
        new FunTestApi(games.resolveSibling("fun-test-ratings.jsonl")).routes(app);
        app.ws("/ws", ws -> {
            ws.onConnect(ctx -> {
                GameHub.Client client = ctx::send;
                clients.put(ctx, client);
                hub.connect(client);
            });
            ws.onMessage(ctx -> {
                GameHub.Client client = clients.get(ctx);
                if (client != null) {
                    hub.onMessage(client, ctx.message());
                }
            });
            ws.onClose(ctx -> {
                GameHub.Client client = clients.remove(ctx);
                if (client != null) {
                    hub.disconnect(client);
                }
            });
        });
        app.start(host, port);
        hub.execute(session::start);
        return new WebServer(app, hub, analysis, eval, health);
    }

    int port() {
        return app.port();
    }

    void stop() {
        hub.shutdown();
        analysis.shutdown();
        eval.shutdown();
        health.stop();
        app.stop();
    }

    /**
     * The IPv4 addresses other devices can reach this computer at: home Wi-Fi ({@code 192.168.x.x})
     * and VPNs such as Tailscale ({@code 100.x.x.x}) alike.
     */
    static List<String> networkAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLinkLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // no addresses to show; the server still runs
        }
        return addresses;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            // fall through to the message below
        }
        System.out.println("Open " + url + " in your browser.");
    }
}
