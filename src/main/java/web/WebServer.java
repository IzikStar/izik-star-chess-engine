package web;

import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import game.GameArchive;
import game.GameConfig;
import game.GameSession;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.awt.Desktop;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The application's entry point since Phase 4c: a local web server for the browser UI.
 *
 * <p>It serves the React app built into the jar under {@code /webapp} and one WebSocket at {@code /ws} that carries the game (see {@link GameHub}
 * for the protocol). It listens on 127.0.0.1 only, so nothing outside this computer can reach it,
 * and opens the default browser on start.
 *
 * <p>It also serves the lab page's API over the evolution runs in a folder ({@link LabApi}) and
 * game analysis with Stockfish ({@link AnalysisApi}), and the player's saved games ({@link GamesApi}).
 *
 * <p>Arguments: {@code --port N} (default 7070, then the next free one up to 7079),
 * {@code --no-browser}, {@code --runs DIR} (the evolution runs, default {@code runs}), {@code --games DIR}
 * (where the player's games are saved, default {@code games}).
 */
public final class WebServer {

    public static final int DEFAULT_PORT = 7070;

    private final Javalin app;
    private final GameHub hub;
    private final AnalysisApi analysis;

    private WebServer(Javalin app, GameHub hub, AnalysisApi analysis) {
        this.app = app;
        this.hub = hub;
        this.analysis = analysis;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean browser = true;
        Path runs = Path.of("runs");
        Path games = Path.of("games");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> browser = false;
                case "--runs" -> runs = Path.of(args[++i]);
                case "--games" -> games = Path.of(args[++i]);
                default -> {
                    System.err.println("unknown argument: " + args[i] + " (use --port N, --no-browser, --runs DIR, --games DIR)");
                    System.exit(2);
                }
            }
        }
        WebServer server = startOnFreePort(port, port == DEFAULT_PORT ? 10 : 1, runs, games);
        String url = "http://localhost:" + server.port() + "/";
        System.out.println("IzikStar Chess is running at " + url + " (Ctrl+C to stop)");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        if (browser) {
            openBrowser(url);
        }
    }

    private static WebServer startOnFreePort(int first, int attempts, Path runs, Path games) {
        RuntimeException last = null;
        for (int port = first; port < first + attempts; port++) {
            try {
                return start(port, defaultSession(), runs, games);
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

    /** Starts a server on {@code port} (0 = any free port) around a new session. */
    static WebServer start(int port, SessionFactory sessions, Path runs, Path games) {
        GameHub hub = new GameHub();
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
        AnalysisApi analysis = new AnalysisApi();
        analysis.routes(app);
        new StockfishApi(hub::stockfish, hub::refresh).routes(app);
        new GamesApi(archive).routes(app);
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
        app.start("127.0.0.1", port);
        hub.execute(session::start);
        return new WebServer(app, hub, analysis);
    }

    int port() {
        return app.port();
    }

    void stop() {
        hub.shutdown();
        analysis.shutdown();
        app.stop();
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
