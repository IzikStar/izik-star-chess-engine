package cloud;

import com.google.gson.JsonObject;

import java.util.List;

/** The shared database, one statement at a time: D1 over HTTP, or an SQLite stand-in in tests. */
public interface Sql {

    /** Runs {@code sql} with {@code params} bound to its {@code ?}s; the rows it returns, if any. */
    List<JsonObject> query(String sql, Object... params);

    /** The database refused or could not be reached. {@code limit}: a quota ran out; try much later. */
    final class SqlException extends RuntimeException {
        private final boolean limit;

        public SqlException(String message, boolean limit) {
            super(message);
            this.limit = limit;
        }

        public SqlException(String message, Throwable cause) {
            super(message, cause);
            this.limit = false;
        }

        public boolean limit() {
            return limit;
        }
    }
}
