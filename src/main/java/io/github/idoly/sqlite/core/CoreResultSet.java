package io.github.idoly.sqlite.core;

import io.github.idoly.sqlite.SQLiteConnectionConfig;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Implements a JDBC ResultSet. */
public abstract class CoreResultSet implements Codes {
    protected final CoreStatement stmt;

    /** If the result set does not have any rows. */
    public boolean emptyResultSet = false;

    /** If the result set is open. Doesn't mean it has results. */
    public boolean open = false;

    /** Maximum number of rows as set by a Statement */
    public long maxRows;

    /** if null, the RS is closed() */
    public String[] cols = null;

    /** same as cols, but used by Meta interface */
    public String[] colsMeta = null;

    protected boolean[][] meta = null;

    /** 0 means no limit, must check against maxRows */
    protected int limitRows;

    /** number of current row, starts at 1 (0 is for before loading data) */
    protected int row = 0;

    protected boolean pastLastRow = false;

    /** last column accessed, for wasNull(). -1 if none */
    protected int lastCol;

    public boolean closeStmt;
    protected Map<String, Integer> columnNameToIndex = null;

    /**
     * Default constructor for a given statement.
     *
     * @param stmt The statement.
     */
    protected CoreResultSet(CoreStatement stmt) {
        this.stmt = stmt;
    }

    // INTERNAL FUNCTIONS ///////////////////////////////////////////

    protected DB getDatabase() {
        return stmt.getDatabase();
    }

    protected SQLiteConnectionConfig getConnectionConfig() {
        return stmt.getConnectionConfig();
    }

    /**
     * Checks the status of the result set.
     *
     * @return True if has results and can iterate them; false otherwise.
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * @throws SQLException if ResultSet is not open.
     */
    protected void checkOpen() throws SQLException {
        if (!open || stmt.conn.isClosed()) {
            throw new SQLException("ResultSet closed");
        }
    }

    /**
     * Takes col in [1,x] form, returns in [0,x-1] form
     *
     * @return
     */
    public int checkCol(int col) throws SQLException {
        if (colsMeta == null) {
            throw new SQLException("SQLite JDBC: inconsistent internal state");
        }
        if (col < 1 || col > colsMeta.length) {
            throw new SQLException("column " + col + " out of bounds [1," + colsMeta.length + "]");
        }
        return --col;
    }

    /**
     * Takes col in [1,x] form, marks it as last accessed and returns [0,x-1]
     *
     * @return
     */
    protected int markCol(int col) throws SQLException {
        checkCol(col);
        lastCol = col;
        return --col;
    }

    public void checkMeta() throws SQLException {
        checkCol(1);
        if (meta == null) {
            meta = stmt.pointer.safeRun(DB::column_metadata);
        }
    }

    public void close() throws SQLException {
        cols = null;
        colsMeta = null;
        meta = null;
        limitRows = 0;
        row = 0;
        pastLastRow = false;
        lastCol = -1;
        columnNameToIndex = null;
        emptyResultSet = false;

        if (stmt.pointer.isClosed() || (!open && !closeStmt)) {
            return;
        }

        DB db = stmt.getDatabase();
        synchronized (db) {
            if (!stmt.pointer.isClosed()) {
                stmt.pointer.safeRunInt(DB::reset);

                if (closeStmt) {
                    closeStmt = false; // break recursive call
                    ((Statement) stmt).close();
                }
            }
        }

        open = false;
    }

    protected Integer findColumnIndexInCache(String col) {
        if (columnNameToIndex == null) {
            return null;
        }
        return columnNameToIndex.get(col);
    }

    protected int addColumnIndexInCache(String col, int index) {
        if (columnNameToIndex == null) {
            columnNameToIndex = new HashMap<String, Integer>(cols.length);
        }
        columnNameToIndex.put(col, index);
        return index;
    }
}
