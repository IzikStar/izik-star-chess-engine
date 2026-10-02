package web;

import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import game.GameConfig;
import game.GameSession;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.websocket.WsContext;

import java.awt.Desktop;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The application's entry point since Phase 4c: a local web server for the browser UI.
 *
 * <p>It serves the React app built into the jar under {@code /webapp}, the game's sounds under
 * {@code /sounds}, and one WebSocket at {@code /ws} that carries the game (see {@link GameHub}
 * for the protocol). It listens on 127.0.0.1 only, so nothing outside this computer can reach it,
 * and opens the default browser on start.
 *
 * <p>Arguments: {@code --port N} (default 7070, then the next free one up to 7079),
 * {@code --no-browser}. The old Swing UI still runs with {@code java -cp <jar> main.Main}.
 */
public final class WebServer {

    public static final int DEFAULT_PORT = 7070;

    private final Javalin app;
    private final GameHub hub;

    private WebServer(Javalin app, GameHub hub) {
        this.app = app;
        this.hub = hub;
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean browser = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--no-browser" -> browser = false;
                default -> {
                    System.err.println("unknown argument: " + args[i] + " (use --port N, --no-browser)");
                    System.exit(2);
                }
            }
        }
        WebServer server = startOnFreePort(port, port == DEFAULT_PORT ? 10 : 1);
        String url = "http://localhost:" + server.port() + "/";
        System.out.println("IzikStar Chess is running at " + url + " (Ctrl+C to stop)");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        if (browser) {
            openBrowser(url);
        }
    }

    private static WebServer startOnFreePort(int first, int attempts) {
        RuntimeException last = null;
        for (int port = first; port < first + attempts; port++) {
            try {
                return start(port, defaultSession());
            } catch (RuntimeException e) {
                last = e; // port taken: try the next one
            }
        }
        throw last;
    }

    private static GameSession defaultSession(GameHub hub) {
        return new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), new StockfishEngine()), hub::execute);
    }

    /** A session factory, so tests can pass their own engines. */
    interface SessionFactory {
        GameSession create(GameHub hub);
    }

    private static SessionFactory defaultSession() {
        return WebServer::defaultSession;
    }

    /** Starts a server on {@code port} (0 = any free port) around a new session. */
    static WebServer start(int port, SessionFactory sessions) {
        GameHub hub = new GameHub();
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
            config.staticFiles.add(files -> {
                files.hostedPath = "/sounds";
                files.directory = "/sounds";
                files.location = Location.CLASSPATH;
            });
            // a player can think for a long time; don't let Jetty drop an idle game connection
            config.jetty.modifyWebSocketServletFactory(factory -> factory.setIdleTimeout(Duration.ofHours(12)));
        });
        if (!uiBuilt) {
            app.get("/", ctx -> ctx.html("<p>The web UI is not built into this jar. Run <code>mvn package</code>, "
                    + "or <code>npm run dev</code> in <code>web/</code> during development.</p>"));
        }
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
        return new WebServer(app, hub);
    }

    int port() {
        return app.port();
    }

    void stop() {
        hub.shutdown();
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
