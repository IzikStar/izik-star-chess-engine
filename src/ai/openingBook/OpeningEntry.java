package ai.openingBook;


public class OpeningEntry {
    private long hash;
    private int count;
    private int whiteWins;
    private int blackWins;
    private int draws;

    public OpeningEntry(long hash, int count, int whiteWins, int blackWins, int draws) {
        this.hash = hash;
        this.count = count;
        this.whiteWins = whiteWins;
        this.blackWins = blackWins;
        this.draws = draws;
    }

    // גטרים ושטרים לפי הצורך
    public long getHash() { return hash; }
    public int getCount() { return count; }
    public int getWhiteWins() { return whiteWins; }
    public int getBlackWins() { return blackWins; }
    public int getDraws() { return draws; }
}


