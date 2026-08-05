package io.github.idoly.sqlite.ffm;

import java.util.Objects;

/**
 * Raw result from sqlite3_open_v2.
 *
 * @param resultCode SQLite result code
 * @param database returned database handle
 */
public record OpenResult(int resultCode, DatabaseHandle database) {
    /** Validates the returned handle. */
    public OpenResult {
        Objects.requireNonNull(database, "database");
    }
}
