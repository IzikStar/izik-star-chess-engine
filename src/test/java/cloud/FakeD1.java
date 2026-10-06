package cloud;

import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/** The shared database for tests: an in-memory SQLite, which is what D1 is. Can be switched off. */
final class FakeD1 implements Sql {

    final Connection db;
    volatile boolean down;
    volatile boolean overQuota;
    int statements;

    FakeD1() throws SQLException {
        db = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @Override
    public synchronized List<JsonObject> query(String sql, Object... params) {
        if (down) {
            throw new SqlException("cannot reach Cloudflare: test network is down", (Throwable) null);
        }
        if (overQuota) {
            throw new SqlException("D1 refused (HTTP 429): daily write limit exceeded", true);
        }
        statements++;
        try {
            return CloudSync.rows(db, sql, params);
        } catch (SQLException e) {
            throw new SqlException(e.getMessage(), e);
        }
    }

    synchronized List<JsonObject> select(String sql, Object... params) throws SQLException {
        return CloudSync.rows(db, sql, params);
    }
}
