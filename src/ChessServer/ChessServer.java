//package ChessServer;
//
//import static spark.Spark.*;
//
//public class ChessServer {
//    public static void main(String[] args) {
//        port(4567);
//
//        get("/move", (req, res) -> {
//            String move = req.queryParams("move");
//            // Process the move
//            return "Move received";
//        });
//
//        post("/game", (req, res) -> {
//            String gameData = req.body();
//            // Save game data
//            return "Game saved";
//        });
//    }
//}
//
