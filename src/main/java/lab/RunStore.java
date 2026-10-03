package lab;

import ai.eval.ParamSchema;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.Score;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The record of one evolution run: a SQLite file with the run's settings, every member of every
 * generation, every game, and each generation's champion and its score against the default
 * weights. Any SQLite browser opens it.
 *
 * <pre>
 * run(name, algorithm, settings, started_at)                      one row
 * member(generation, idx, params)                                 params: JSON of every parameter
 * game(id, generation, kind, white, black, opening, moves, result, reason, plies, seed, depth, opponent)
 *      kind 'population': white/black are member indexes; 'yardstick': the champion's index, -1 = the
 *      yardstick, named in opponent ("default" in runs from before yardstick lists). depth: the
 *      search depth of both sides (null in older runs).
 * generation(number, champion, champion_score, games, yardstick_wins, yardstick_draws,
 *            yardstick_losses, yardstick_elo, yardstick_elo_low, yardstick_elo_high, finished_at)
 *      a row means the generation is complete and the next one's members are stored; the
 *      yardstick columns hold the first yardstick's result, all depths together
 * yardstick(generation, opponent, level, depth, wins, draws, losses)
 *      the champion's score against each yardstick at each depth; level: the UCI_Elo Stockfish
 *      played at for "sf:auto", else 0
 * </pre>
 */
public final class RunStore implements AutoCloseable {

    /**
     * One finished generation, as the {@code generation} table has it.
     *
     * @param yardstick  the champion against the first yardstick, all depths together
     * @param yardsticks the champion against every yardstick played, one entry per depth
     */
    public record GenerationRow(int number, int champion, double championScore, int games,
                                Optional<Score> yardstick, String finishedAt, List<YardstickResult> yardsticks) {

        public GenerationRow {
            yardsticks = List.copyOf(yardsticks);
        }

        /** A generation without a yardstick list (older runs keep only the first yardstick). */
        public GenerationRow(int number, int champion, double championScore, int games, Optional<Score> yardstick,
                             String finishedAt) {
            this(number, champion, championScore, games, yardstick, finishedAt, List.of());
        }
    }

    /**
     * The champion's score against one yardstick at one depth.
     *
     * @param opponent as the run settings name it, e.g. "classic" or "sf:auto"
     * @param level    the UCI_Elo Stockfish played at, for "sf:auto"; 0 otherwise
     */
    public record YardstickResult(String opponent, int level, int depth, Score score) {

        /** "sf:auto" as the Stockfish it was this time ("sf1500"), others as named. */
        public String label() {
            return level > 0 ? "sf" + level : opponent;
        }

        /** The scores of {@code results} against {@code opponent}, all depths added up, or empty. */
        public static Optional<Score> combined(List<YardstickResult> results, String opponent) {
            int w = 0, d = 0, l = 0;
            boolean any = false;
            for (YardstickResult r : results) {
                if (r.opponent().equals(opponent)) {
                    any = true;
                    w += r.score().wins();
                    d += r.score().draws();
                    l += r.score().losses();
                }
            }
            return any ? Optional.of(new Score("champion", w, d, l)) : Optional.empty();
        }
    }

    /** The run's own row. */
    public record RunRow(String name, String algorithm, RunSettings settings, String startedAt) {}

    private final Connection db;
    private final Path file;

    private RunStore(Connection db, Path file) {
        this.db = db;
        this.file = file;
    }

    /** The run file. */
    public Path file() {
        return file;
    }

    /** Opens (or creates) the run file at {@code file}. */
    public static RunStore open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Connection db = DriverManager.getConnection("jdbc:sqlite:" + file);
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS run (name TEXT, algorithm TEXT, settings TEXT, started_at TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS member (generation INTEGER, idx INTEGER, params TEXT,"
                        + " PRIMARY KEY (generation, idx))");
                s.execute("CREATE TABLE IF NOT EXISTS game (id INTEGER PRIMARY KEY, generation INTEGER, kind TEXT,"
                        + " white INTEGER, black INTEGER, opening TEXT, moves TEXT, result TEXT, reason TEXT,"
                        + " plies INTEGER, seed INTEGER)");
                s.execute("CREATE INDEX IF NOT EXISTS game_generation ON game (generation)");
                s.execute("CREATE TABLE IF NOT EXISTS generation (number INTEGER PRIMARY KEY, champion INTEGER,"
                        + " champion_score REAL, games INTEGER, yardstick_wins INTEGER, yardstick_draws INTEGER,"
                        + " yardstick_losses INTEGER, yardstick_elo REAL, yardstick_elo_low REAL,"
                        + " yardstick_elo_high REAL, finished_at TEXT)");
                s.execute("CREATE TABLE IF NOT EXISTS yardstick (generation INTEGER, opponent TEXT, level INTEGER,"
                        + " depth INTEGER, wins INTEGER, draws INTEGER, losses INTEGER,"
                        + " PRIMARY KEY (generation, opponent, depth))");
                addColumn(db, "game", "depth", "INTEGER");
                addColumn(db, "game", "opponent", "TEXT");
            }
            return new RunStore(db, file);
        } catch (Exception e) {
            throw new IllegalStateException("cannot open run file " + file, e);
        }
    }

    /** Adds a column a run file from an older version lacks. */
    private static void addColumn(Connection db, String table, String column, String type) throws SQLException {
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (r.next()) {
                if (r.getString("name").equals(column)) {
                    return;
                }
            }
        }
        try (Statement s = db.createStatement()) {
            s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    public Optional<RunRow> run() {
        try (Statement s = db.createStatement();
             ResultSet r = s.executeQuery("SELECT name, algorithm, settings, started_at FROM run")) {
            return r.next()
                    ? Optional.of(new RunRow(r.getString(1), r.getString(2), RunSettings.fromJson(r.getString(3)), r.getString(4)))
                    : Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void createRun(String name, String algorithm, RunSettings settings) {
        if (run().isPresent()) {
            throw new IllegalStateException("this file already holds a run; resume it or use another file");
        }
        update("INSERT INTO run VALUES (?, ?, ?, ?)", name, algorithm, settings.toJson(), Instant.now().toString());
    }

    /** Stores a generation's members (replacing any stored before). */
    public void saveMembers(int generation, List<ParamVector> members) {
        inTransaction(() -> {
            update("DELETE FROM member WHERE generation = ?", generation);
            for (int i = 0; i < members.size(); i++) {
                update("INSERT INTO member VALUES (?, ?, ?)", generation, i, members.get(i).toJson());
            }
        });
    }

    public List<ParamVector> members(int generation, ParamSchema schema) {
        List<ParamVector> members = new ArrayList<>();
        try (PreparedStatement p = db.prepareStatement("SELECT params FROM member WHERE generation = ? ORDER BY idx")) {
            p.setInt(1, generation);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    members.add(ParamVector.fromJson(schema, r.getString(1)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return members;
    }

    /**
     * Stores a game. In a population game the players are named by member index; in a yardstick
     * game the yardstick is -1 and {@code opponent} names it.
     *
     * @param depth both sides' search depth (0 if unknown)
     */
    public void saveGame(int generation, String kind, int white, int black, GameRecord game, int depth,
                         String opponent) {
        update("INSERT INTO game (generation, kind, white, black, opening, moves, result, reason, plies, seed,"
                        + " depth, opponent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                generation, kind, white, black, game.opening(), String.join(" ", game.moves()),
                game.result().name(), game.reason(), game.plies(), game.seed(), depth == 0 ? null : depth, opponent);
    }

    /** A population game of unknown depth. */
    public void saveGame(int generation, String kind, int white, int black, GameRecord game) {
        saveGame(generation, kind, white, black, game, 0, null);
    }

    /** The games of a generation of one kind, players named as the arena named them. */
    public List<GameRecord> games(int generation, String kind) {
        List<GameRecord> games = new ArrayList<>();
        try (PreparedStatement p = db.prepareStatement("SELECT white, black, opening, moves, result, reason, seed,"
                + " opponent FROM game WHERE generation = ? AND kind = ? ORDER BY id")) {
            p.setInt(1, generation);
            p.setString(2, kind);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String moves = r.getString(4);
                    String opponent = r.getString(8);
                    games.add(new GameRecord(playerName(r.getInt(1), opponent), playerName(r.getInt(2), opponent),
                            r.getString(3),
                            moves.isEmpty() ? List.of() : Arrays.asList(moves.split(" ")),
                            GameRecord.Result.valueOf(r.getString(5)), r.getString(6), r.getLong(7)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return games;
    }

    /** Every game of the run, population and yardstick, in the order played. */
    public List<GameRecord> allGames() {
        List<GameRecord> games = new ArrayList<>();
        for (GenerationRow row : generations()) {
            games.addAll(games(row.number(), "population"));
            games.addAll(games(row.number(), "yardstick"));
        }
        return games;
    }

    static String playerName(int index, String opponent) {
        return index >= 0 ? String.valueOf(index) : opponent == null ? "default" : opponent;
    }

    /** The depth each game of a generation was played at (0 if unknown), in the order of {@link #games}. */
    public List<Integer> gameDepths(int generation, String kind) {
        List<Integer> depths = new ArrayList<>();
        try (PreparedStatement p = db.prepareStatement("SELECT depth FROM game WHERE generation = ? AND kind = ?"
                + " ORDER BY id")) {
            p.setInt(1, generation);
            p.setString(2, kind);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    depths.add(r.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return depths;
    }

    /** Drops the games of an unfinished generation, so it can be played again from the start. */
    public void deleteGames(int generation) {
        update("DELETE FROM game WHERE generation = ?", generation);
    }

    /**
     * Marks {@code row}'s generation complete and stores the next generation's members, in one
     * transaction: a run stopped at any moment resumes from a consistent point.
     */
    public void finishGeneration(GenerationRow row, List<ParamVector> next) {
        inTransaction(() -> {
            if (next != null) {
                update("DELETE FROM member WHERE generation = ?", row.number() + 1);
                for (int i = 0; i < next.size(); i++) {
                    update("INSERT INTO member VALUES (?, ?, ?)", row.number() + 1, i, next.get(i).toJson());
                }
            }
            update("DELETE FROM yardstick WHERE generation = ?", row.number());
            for (YardstickResult y : row.yardsticks()) {
                update("INSERT INTO yardstick VALUES (?, ?, ?, ?, ?, ?, ?)", row.number(), y.opponent(), y.level(),
                        y.depth(), y.score().wins(), y.score().draws(), y.score().losses());
            }
            Score y = row.yardstick().orElse(null);
            update("INSERT OR REPLACE INTO generation VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    row.number(), row.champion(), row.championScore(), row.games(),
                    y == null ? null : y.wins(), y == null ? null : y.draws(), y == null ? null : y.losses(),
                    y == null ? null : y.elo(), y == null ? null : y.eloLow(), y == null ? null : y.eloHigh(),
                    row.finishedAt());
        });
    }

    /** The finished generations, in order. */
    public List<GenerationRow> generations() {
        List<GenerationRow> rows = new ArrayList<>();
        try (Statement s = db.createStatement();
             ResultSet r = s.executeQuery("SELECT number, champion, champion_score, games, yardstick_wins,"
                     + " yardstick_draws, yardstick_losses, finished_at FROM generation ORDER BY number")) {
            while (r.next()) {
                int wins = r.getInt(5);
                Optional<Score> yardstick = r.wasNull()
                        ? Optional.empty()
                        : Optional.of(new Score("champion", wins, r.getInt(6), r.getInt(7)));
                rows.add(new GenerationRow(r.getInt(1), r.getInt(2), r.getDouble(3), r.getInt(4), yardstick,
                        r.getString(8), yardsticks(r.getInt(1))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }

    private List<YardstickResult> yardsticks(int generation) throws SQLException {
        List<YardstickResult> results = new ArrayList<>();
        try (PreparedStatement p = db.prepareStatement("SELECT opponent, level, depth, wins, draws, losses"
                + " FROM yardstick WHERE generation = ? ORDER BY rowid")) {
            p.setInt(1, generation);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    results.add(new YardstickResult(r.getString(1), r.getInt(2), r.getInt(3),
                            new Score("champion", r.getInt(4), r.getInt(5), r.getInt(6))));
                }
            }
        }
        return results;
    }

    private interface SqlWork {
        void run() throws SQLException;
    }

    private synchronized void inTransaction(SqlWork work) {
        try {
            db.setAutoCommit(false);
            try {
                work.run();
                db.commit();
            } catch (SQLException | RuntimeException e) {
                db.rollback();
                throw e;
            } finally {
                db.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private synchronized void update(String sql, Object... args) {
        try (PreparedStatement p = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                p.setObject(i + 1, args[i]);
            }
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
