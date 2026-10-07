package lab;

import arena.GamePgn;
import arena.GameRecord;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Arena and run games as PGN, for any chess program. Players are named as the run names them
 * ("#3" for member 3, "classic", "sf1320"); the opening, the depth and why the game ended are
 * tags.
 */
public final class RunPgn {

    private RunPgn() {}

    /**
     * Writes the games of a run: every generation, or only {@code generation} (-1 for all); only
     * the games of {@code member} when it is 0 or more. Returns how many games it wrote.
     */
    public static int write(RunStore store, int generation, int member, Writer out) throws IOException {
        String event = store.run().map(RunStore.RunRow::name).orElse("evolution run");
        ai.variant.Variant variant = store.run().map(r -> r.settings().variant()).orElse(ai.variant.Variants.CHESS);
        int written = 0;
        for (RunStore.GenerationRow row : store.generations()) {
            if (generation >= 0 && row.number() != generation) {
                continue;
            }
            for (String kind : List.of("population", "yardstick", "stockfish")) {
                List<GameRecord> games = store.games(row.number(), kind);
                List<Integer> depths = store.gameDepths(row.number(), kind);
                for (int i = 0; i < games.size(); i++) {
                    GameRecord g = games.get(i);
                    if (member >= 0 && !isMember(g, member, kind, row)) {
                        continue;
                    }
                    out.write(GamePgn.write(variant, g, event, row.number(), depths.get(i)));
                    out.write('\n');
                    written++;
                }
            }
        }
        return written;
    }

    private static boolean isMember(GameRecord g, int member, String kind, RunStore.GenerationRow row) {
        String self = String.valueOf(member);
        return g.white().equals(self) || g.black().equals(self)
                || kind.equals("yardstick") && row.champion() == member;
    }
}
