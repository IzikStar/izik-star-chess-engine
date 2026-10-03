import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitBoardRules;
import ai.Minimax;
import arena.*;
import rules.*;
import java.util.*;

public class DepthTiming {
    public static void main(String[] a) {
        int nOpen = Integer.parseInt(a[0]); int maxDepth = Integer.parseInt(a[1]); long capMs = Long.parseLong(a[2]);
        List<String> fens = new ArrayList<>();
        List<Opening> ops = Opening.suite();
        for (int i = 0; i < nOpen; i++) {
            Opening op = ops.get(i * ops.size() / nOpen);
            GameRecord g = Match.play(Player.yardstick(4, 2), Player.yardstick(4, 2), op, 160, i);
            Game game = new Game();
            for (int p = 0; p < g.moves().size(); p++) {
                game.play(g.moves().get(p));
                if (p >= 11 && (p - 11) % 14 == 0) fens.add(game.fen());
            }
        }
        System.out.println("positions " + fens.size());
        for (String fen : fens) {
            int pieces = Position.fromFen(fen).pieces().size();
            StringBuilder sb = new StringBuilder("P " + pieces + " " + fen.replace(' ', '_'));
            for (int d = 4; d <= maxDepth; d++) {
                long t0 = System.nanoTime(); long dl = t0 + capMs * 1_000_000;
                Minimax.getBestMove(BitBoardRules.fromFen(fen), d, BitBoardEvaluate.DEFAULT,
                        new Minimax.Options(2, new Random(1), true), () -> System.nanoTime() > dl);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                sb.append(" d").append(d).append('=').append(ms);
                if (ms >= capMs) break;
            }
            System.out.println(sb); System.out.flush();
        }
    }
}
