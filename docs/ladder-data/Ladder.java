import arena.Opening;
import engine.MinimaxEngine;
import engine.SearchRequest;
import rules.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** usage: Ladder threads openings maxPlies sfMoveMs pair... ; pair = A:B, player = R | D<n> | E<elo> | K<skill> */
public class Ladder {
    interface Mover extends Closeable { String move(Game g); }

    static final String SF = "/usr/games/stockfish";
    static long sfMs;

    static Mover make(String spec, long seed) throws IOException {
        if (spec.equals("R")) { Random r = new Random(seed); return new Mover() {
            public String move(Game g) { List<ChessMove> l = g.legalMoves(); return l.get(r.nextInt(l.size())).toUci(); }
            public void close() {} }; }
        if (spec.startsWith("M")) { int pct = Integer.parseInt(spec.substring(1)); Random r = new Random(seed); MinimaxEngine e = new MinimaxEngine(new Random(seed));
            return new Mover() { public String move(Game g) { List<ChessMove> l = g.legalMoves();
                if (r.nextInt(100) < pct) return l.get(r.nextInt(l.size())).toUci();
                return e.bestMove(SearchRequest.of(g.fen(), 2)).toUci(); }
            public void close() {} }; }
        if (spec.startsWith("V")) { String[] dv = spec.substring(1).split("_"); int n = Integer.parseInt(dv[0]);
            MinimaxEngine e = new MinimaxEngine(new Random(seed), 5000, ai.BitBoard.BitBoardEvaluate.DEFAULT, Integer.parseInt(dv[1]));
            return new Mover() { public String move(Game g) { return e.bestMove(SearchRequest.of(g.fen(), 2 * n)).toUci(); }
            public void close() {} }; }
        if (spec.startsWith("D")) { int n = Integer.parseInt(spec.substring(1)); MinimaxEngine e = new MinimaxEngine(new Random(seed));
            return new Mover() { public String move(Game g) { return e.bestMove(SearchRequest.of(g.fen(), 2 * n)).toUci(); }
            public void close() {} }; }
        Process p = new ProcessBuilder(SF).redirectErrorStream(true).start();
        BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(p.getOutputStream()), true);
        out.println("uci"); out.println("setoption name Threads value 1"); out.println("setoption name Hash value 16");
        if (spec.startsWith("E")) { out.println("setoption name UCI_LimitStrength value true"); out.println("setoption name UCI_Elo value " + spec.substring(1)); }
        else if (spec.startsWith("K")) out.println("setoption name Skill Level value " + spec.substring(1));
        out.println("ucinewgame"); out.println("isready");
        String line; while (!(line = in.readLine()).equals("readyok")) {}
        return new Mover() {
            public String move(Game g) {
                StringBuilder sb = new StringBuilder("position startpos moves");
                for (MoveResult m : g.moves()) sb.append(' ').append(m.move().toUci());
                out.println(sb); out.println("go movetime " + sfMs);
                try { String l; while ((l = in.readLine()) != null) if (l.startsWith("bestmove")) return l.split(" ")[1]; }
                catch (IOException ex) { throw new UncheckedIOException(ex); }
                throw new IllegalStateException("stockfish died");
            }
            public void close() { out.println("quit"); p.destroy(); } };
    }

    /** returns score for A: 1, .5, 0 */
    static double play(String a, String b, boolean aWhite, Opening op, int maxPlies, long seed) throws IOException {
        try (Mover ma = make(a, seed); Mover mb = make(b, seed ^ 0x9E3779B97F4A7C15L)) {
            Game g = new Game();
            for (String u : op.moves()) g.play(u);
            while (!g.status().isGameOver() && g.plyCount() < maxPlies) {
                boolean white = g.fen().split(" ")[1].equals("w");
                g.play((white == aWhite ? ma : mb).move(g));
            }
            if (g.status() == GameStatus.CHECKMATE) {
                boolean whiteMated = g.fen().split(" ")[1].equals("w");
                return (whiteMated != aWhite) ? 1 : 0;
            }
            return 0.5;
        }
    }

    public static void main(String[] args) throws Exception {
        int threads = Integer.parseInt(args[0]); int nOpen = Integer.parseInt(args[1]);
        int maxPlies = Integer.parseInt(args[2]); sfMs = Long.parseLong(args[3]);
        List<Opening> ops = Opening.suite().subList(0, Math.min(nOpen, Opening.suite().size()));
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        Map<String, double[]> res = new ConcurrentHashMap<>();
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 4; i < args.length; i++) {
            String pair = args[i]; String[] ab = pair.split(":");
            res.put(pair, new double[3]);
            for (int o = 0; o < ops.size(); o++) for (int c = 0; c < 2; c++) {
                final int oo = o, cc = c;
                fs.add(ex.submit(() -> {
                    try {
                        double s = play(ab[0], ab[1], cc == 0, ops.get(oo), maxPlies, oo * 31L + cc);
                        double[] r = res.get(pair);
                        synchronized (r) { r[0] += s; r[1]++; if (s == 0.5) r[2]++;
                            System.out.printf("G %s %s %s %.1f%n", pair, ops.get(oo).name().replace(' ', '_'), cc == 0 ? "Awhite" : "Ablack", s); }
                    } catch (Exception e) { e.printStackTrace(); }
                }));
            }
        }
        for (Future<?> f : fs) f.get();
        ex.shutdown();
        for (String pair : res.keySet()) {
            double[] r = res.get(pair); double p = r[0] / r[1];
            double pc = Math.min(Math.max(p, 0.5 / r[1]), 1 - 0.5 / r[1]);
            double elo = -400 * Math.log10(1 / pc - 1);
            double se = Math.sqrt(pc * (1 - pc) / r[1]);
            double lo = -400 * Math.log10(1 / Math.max(1e-3, pc - 1.96 * se) - 1), hi = -400 * Math.log10(1 / Math.min(1 - 1e-3, pc + 1.96 * se) - 1);
            System.out.printf("R %s score %.1f/%d draws %d  elo %+.0f [%+.0f, %+.0f]%n", pair, r[0], (int) r[1], (int) r[2], elo, lo, hi);
        }
    }
}
