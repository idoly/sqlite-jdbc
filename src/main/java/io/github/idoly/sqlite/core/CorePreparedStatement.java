package io.github.idoly.sqlite.core;

import io.github.idoly.sqlite.SQLiteConnection;
import io.github.idoly.sqlite.SQLiteConnectionConfig;
import io.github.idoly.sqlite.internal.BaseConnection;
import io.github.idoly.sqlite.internal.StatementImpl;
import java.sql.Date;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Calendar;

public abstract class CorePreparedStatement extends StatementImpl {
    protected int columnCount;
    protected int paramCount;
    protected int batchQueryCount;

    /**
     * Constructs a prepared statement on a provided connection.
     *
     * @param conn Connection on which to create the prepared statement.
     * @param sql The SQL script to prepare.
     */
    protected CorePreparedStatement(SQLiteConnection conn, String sql) throws SQLException {
        super(conn);

        this.sql = sql;
        SQLiteDatabase db = conn.getDatabase();
        db.prepare(this);
        rs.colsMeta = pointer.safeRun(SQLiteDatabase::column_names);
        columnCount = pointer.safeRunInt(SQLiteDatabase::column_count);
        paramCount = pointer.safeRunInt(SQLiteDatabase::bind_parameter_count);
        batchQueryCount = 0;
        batch = null;
        batchPos = 0;
    }

    /**
     * @see io.github.idoly.sqlite.internal.BaseStatement#executeBatch()
     */
    @Override
    public int[] executeBatch() throws SQLException {
        return Arrays.stream(executeLargeBatch()).mapToInt(l -> (int) l).toArray();
    }

    /**
     * @see io.github.idoly.sqlite.internal.BaseStatement#executeLargeBatch()
     */
    @Override
    public long[] executeLargeBatch() throws SQLException {
        if (batchQueryCount == 0) {
            return new long[] {};
        }

        if (this.conn instanceof BaseConnection) {
            ((BaseConnection) this.conn).tryEnforceTransactionMode();
        }

        return this.withConnectionTimeout(
                () -> {
                    try {
                        return conn.getDatabase()
                                .executeBatch(
                                        pointer, batchQueryCount, batch, conn.getAutoCommit());
                    } finally {
                        clearBatch();
                    }
                });
    }

    /**
     * @see io.github.idoly.sqlite.internal.BaseStatement#clearBatch() ()
     */
    @Override
    public void clearBatch() throws SQLException {
        super.clearBatch();
        batchQueryCount = 0;
    }

    // PARAMETER FUNCTIONS //////////////////////////////////////////

    /** Assigns the object value to the element at the specific position of array batch. */
    protected void batch(int pos, Object value) throws SQLException {
        checkOpen();
        if (batch == null) {
            batch = new Object[paramCount];
        }
        batch[batchPos + pos - 1] = value;
    }

    /** Store the date in the user's preferred format (text, int, or real) */
    protected void setDateByMilliseconds(int pos, Long value, Calendar calendar)
            throws SQLException {
        SQLiteConnectionConfig config = conn.getConnectionConfig();
        switch (config.getDateClass()) {
            case TEXT:
                batch(pos, config.formatDate(new Date(value), calendar.getTimeZone()));
                break;

            case REAL:
                // long to Julian date
                batch(pos, new Double((value / 86400000.0) + 2440587.5));
                break;

            default: // INTEGER:
                batch(pos, new Long(value / config.getDateMultiplier()));
        }
    }
}
