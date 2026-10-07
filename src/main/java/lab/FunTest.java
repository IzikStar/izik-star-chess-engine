package lab;

import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.piece.StandardPieces;
import ai.variant.CastlingRule;
import ai.variant.Variant;
import ai.variant.VariantJson;
import ai.variant.WinCondition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The blind fun test (docs/fun-test.md): does the health check tell which variants people enjoy?
 *
 * <p>Makes random variants (8x8, pawns on the second or third row, a mix of chess and fairy pieces, one of five ways to win),
 * gives each a health check and a score from it, then picks the four best (each with a different way
 * to win) and four at random from the rest. The eight are written under neutral names in a shuffled order; which group each came from goes
 * to a separate key file, to be opened only after the ratings are in.
 *
 * <pre>
 * java -cp target/izikstar-chess-3.1.0.jar lab.FunTest OUT_DIR [--candidates N --games N --depth N --seed N]
 * </pre>
 */
public final class FunTest {

    private FunTest() {}

    /** A piece the generator may use: its Betza moves and a rough starting value. */
    private record Fairy(String name, char letter, String betza, int value) {
        PieceType type() {
            return PieceType.of(name, letter, value, Betza.parse(betza).toArray(ai.piece.Atom[]::new));
        }
    }

    private static final List<Fairy> POOL = List.of(
            new Fairy("Knight", 'N', "N", 320),
            new Fairy("Bishop", 'B', "B", 330),
            new Fairy("Rook", 'R', "R", 500),
            new Fairy("Queen", 'Q', "Q", 900),
            new Fairy("Mann", 'M', "WF", 300),
            new Fairy("Champion", 'C', "WAD", 450),
            new Fairy("Wizard", 'Z', "FC", 400),
            new Fairy("Archbishop", 'A', "BN", 800),
            new Fairy("Chancellor", 'E', "RN", 850),
            new Fairy("Camel", 'L', "C", 250),
            new Fairy("Elephant", 'U', "FA", 250));

    /** The five ways to win the generator picks from. */
    enum Rules { CHECKMATE, HILL, THREE_CHECKS, PAWNS_GONE, GIVE_AWAY }

    /** A candidate with its health report and score. */
    record Candidate(Variant variant, Rules rules, VariantHealth.Report report, double score) {}

    /**
     * The fixed score, 0 to 100, decided before any person plays: balance 30, decisive games 25,
     * length 20, choice 15, games that end 10.
     */
    static double score(VariantHealth.Report r) {
        double balance = clamp(1 - Math.abs(r.whiteScore() - 0.5) * 4);
        double decisive = r.decisiveShare();
        double plies = r.averagePlies();
        double length = plies < 30 ? clamp((plies - 10) / 20) : plies <= 100 ? 1 : clamp((200 - plies) / 100);
        double choice = clamp(r.movesPerTurn() / 20);
        double capped = r.endings().getOrDefault("PLY_CAP", 0) / (double) Math.max(1, r.games());
        return 30 * balance + 25 * decisive + 20 * length + 15 * choice + 10 * (1 - capped);
    }

    private static double clamp(double x) {
        return Math.max(0, Math.min(1, x));
    }

    /** A random variant; {@code index} names it. */
    static Draft random(Random random, int index) {
        // the play side (moves, game, board on screen) is 8x8 only for now
        int width = 8;
        int height = 8;
        boolean pawnsForward = random.nextBoolean();
        Rules rules = Rules.values()[random.nextInt(Rules.values().length)];

        List<Fairy> pool = new ArrayList<>(POOL);
        Collections.shuffle(pool, random);
        int kinds = 2 + random.nextInt(3);
        List<Fairy> chosen = pool.subList(0, kinds);

        // the back row: the royal piece in the middle, the others spread symmetrically where possible
        char[] back = new char[width];
        int kingCol = width / 2;
        back[kingCol] = 'K';
        for (int col = 0; col < width; col++) {
            if (col == kingCol) {
                continue;
            }
            int mirror = width - 1 - col;
            if (mirror < col && mirror != kingCol) {
                back[col] = back[mirror];
            } else {
                back[col] = chosen.get(random.nextInt(chosen.size())).letter();
            }
        }

        List<PieceType> pieces = new ArrayList<>();
        PieceType k = StandardPieces.KING;
        boolean giveAway = rules == Rules.GIVE_AWAY;
        pieces.add(new PieceType(k.name(), 'K', k.atoms(), !giveAway, List.of(), false, PieceType.Castling.NONE,
                giveAway ? 300 : 0));
        List<Character> promotions = new ArrayList<>();
        for (Fairy f : chosen) {
            if (new String(back).indexOf(f.letter()) >= 0) {
                pieces.add(f.type());
                promotions.add(f.letter());
            }
        }
        PieceType p = StandardPieces.PAWN;
        pieces.add(new PieceType(p.name(), 'P', p.atoms(), false, promotions, true, PieceType.Castling.NONE, 100));

        String backRow = new String(back);
        String pawns = "P".repeat(width);
        // pawns on the second row, or one row forward (on the third row) for a faster clash
        List<String> rows = new ArrayList<>();
        rows.add(backRow.toLowerCase(Locale.ROOT));
        if (pawnsForward) {
            rows.add(String.valueOf(width));
        }
        rows.add(pawns.toLowerCase(Locale.ROOT));
        for (int i = 0; i < height - 4 - (pawnsForward ? 2 : 0); i++) {
            rows.add(String.valueOf(width));
        }
        rows.add(pawns);
        if (pawnsForward) {
            rows.add(String.valueOf(width));
        }
        rows.add(backRow);
        String fen = String.join("/", rows) + " w - - 0 1";

        List<WinCondition> goals = switch (rules) {
            case CHECKMATE -> List.of(WinCondition.checkmate());
            case HILL -> List.of(WinCondition.checkmate(), WinCondition.reach(WinCondition.centre(width, height), ""));
            case THREE_CHECKS -> List.of(WinCondition.checkmate(), WinCondition.checks(3));
            case PAWNS_GONE -> List.of(WinCondition.checkmate(), WinCondition.captureAllOf("P"));
            case GIVE_AWAY -> List.of(WinCondition.loseEverything());
        };
        Variant.Stalemate stalemate = giveAway ? Variant.Stalemate.WIN
                : Variant.Stalemate.values()[random.nextInt(Variant.Stalemate.values().length)];
        String id = String.format("candidate-%02d", index);
        Variant v = new Variant(id, id, pieces, new Grid(width, height), fen, goals, Variant.RoyalMode.ALL_SAFE,
                stalemate, true, 50, giveAway, CastlingRule.NONE);
        return new Draft(v, rules);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: lab.FunTest OUT_DIR [--candidates N --games N --depth N --seed N]");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        int candidates = 40;
        int games = 40;
        int depth = 2;
        long seed = 20261007L;
        for (int i = 1; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--candidates" -> candidates = Integer.parseInt(args[i + 1]);
                case "--games" -> games = Integer.parseInt(args[i + 1]);
                case "--depth" -> depth = Integer.parseInt(args[i + 1]);
                case "--seed" -> seed = Long.parseLong(args[i + 1]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        Random random = new Random(seed);
        VariantHealth.Settings settings = new VariantHealth.Settings(games, depth, 4, 300, seed);
        List<Candidate> all = new ArrayList<>();
        for (int i = 1; i <= candidates; i++) {
            Draft b = random(random, i);
            try {
                VariantHealth.Report r = VariantHealth.run(b.variant(), settings, n -> {}, () -> false);
                Candidate c = new Candidate(b.variant(), b.rules(), r, score(r));
                all.add(c);
                System.out.printf(Locale.ROOT, "%s %-12s %dx%d score %5.1f  white %.2f decisive %.2f plies %5.1f choice %4.1f  %s%n",
                        c.variant().id(), c.rules(), c.variant().grid().width(), c.variant().grid().height(), c.score(),
                        r.whiteScore(), r.decisiveShare(), r.averagePlies(), r.movesPerTurn(), r.endings());
            } catch (RuntimeException e) {
                System.out.println(b.variant().id() + " skipped: " + e.getMessage());
            }
        }
        if (all.size() < 8) {
            throw new IllegalStateException("only " + all.size() + " candidates could be played; 8 are needed");
        }
        all.sort(Comparator.comparingDouble(Candidate::score).reversed());
        // the best of four different ways to win: else the short-game rules (three checks, give-away)
        // fill the top group, and the test would measure the rules rather than the score
        List<Candidate> top = new ArrayList<>();
        for (Candidate c : all) {
            if (top.size() < 4 && top.stream().noneMatch(t -> t.rules() == c.rules())) {
                top.add(c);
            }
        }
        List<Candidate> rest = new ArrayList<>(all);
        rest.removeAll(top);
        Collections.shuffle(rest, random);
        List<Candidate> control = rest.subList(0, 4);

        List<String[]> picked = new ArrayList<>();
        top.forEach(c -> picked.add(new String[]{"top", c.variant().id()}));
        control.forEach(c -> picked.add(new String[]{"random", c.variant().id()}));
        Collections.shuffle(picked, random);

        Files.createDirectories(out.resolve("variants"));
        StringBuilder key = new StringBuilder("# Fun test key: open only after every rating is in\n\n");
        key.append("| Name | Group | Candidate | Rules | Board | Score |\n|---|---|---|---|---|---|\n");
        StringBuilder ranking = new StringBuilder("rank,candidate,rules,board,score,white_score,decisive,avg_plies,moves_per_turn\n");
        for (int i = 0; i < all.size(); i++) {
            Candidate c = all.get(i);
            VariantHealth.Report r = c.report();
            ranking.append(String.format(Locale.ROOT, "%d,%s,%s,%dx%d,%.1f,%.3f,%.3f,%.1f,%.1f%n", i + 1, c.variant().id(),
                    c.rules(), c.variant().grid().width(), c.variant().grid().height(), c.score(), r.whiteScore(),
                    r.decisiveShare(), r.averagePlies(), r.movesPerTurn()));
        }
        for (int i = 0; i < picked.size(); i++) {
            String letter = String.valueOf((char) ('A' + i));
            String pickedId = picked.get(i)[1];
            Candidate c = all.stream().filter(x -> x.variant().id().equals(pickedId)).findFirst().orElseThrow();
            Variant v = c.variant();
            Variant named = new Variant("fun-test-" + letter.toLowerCase(Locale.ROOT), "Fun test " + letter, v.pieces(),
                    v.grid(), v.startFen(), v.goals(), v.royalMode(), v.stalemate(), v.repetition(), v.moveLimit(),
                    v.forcedCapture(), v.castling());
            Files.writeString(out.resolve("variants").resolve(named.id() + ".json"), VariantJson.write(named));
            key.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %dx%d | %.1f |%n", named.name(), picked.get(i)[0],
                    v.id(), c.rules(), v.grid().width(), v.grid().height(), c.score()));
        }
        Files.writeString(out.resolve("KEY-do-not-open.md"), key.toString());
        Files.writeString(out.resolve("ranking.csv"), ranking.toString());
        System.out.println("wrote " + picked.size() + " variants to " + out.resolve("variants"));
    }

    /** A variant before its health check. */
    record Draft(Variant variant, Rules rules) {}
}
