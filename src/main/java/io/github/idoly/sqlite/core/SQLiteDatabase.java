package io.github.idoly.sqlite.core;

import static io.github.idoly.sqlite.core.SQLiteResultCodes.*;

import io.github.idoly.sqlite.SQLiteBusyHandler;
import io.github.idoly.sqlite.SQLiteCollation;
import io.github.idoly.sqlite.SQLiteCommitListener;
import io.github.idoly.sqlite.SQLiteConfig;
import io.github.idoly.sqlite.SQLiteErrorCode;
import io.github.idoly.sqlite.SQLiteException;
import io.github.idoly.sqlite.SQLiteFunction;
import io.github.idoly.sqlite.SQLiteProgressHandler;
import io.github.idoly.sqlite.SQLiteUpdateListener;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * This class is the interface to SQLite. It provides some helper functions
 * used by other parts of the driver. The goal of the helper functions here
 * are not only to provide functionality, but to handle contractual
 * differences between the JDBC specification and the SQLite C API.
 *
 * The process of moving SQLite weirdness into this class is incomplete.
 * Some statement and prepared-statement implementations still perform
 * implicit contract conversions.
 *
 * The subclass, FfmDatabase, provides the actual access to SQLite functions.
 */
public abstract class SQLiteDatabase {
    private final String url;
    private final String fileName;
    private final SQLiteConfig config;
    private final AtomicBoolean closed = new AtomicBoolean(true);

    /** The "begin;"and "commit;" statement handles. */
    volatile StatementHandle begin;

    volatile StatementHandle commit;

    /** Tracer for statements to avoid unfinalized statements on db close. */
    private final Set<StatementHandle> stmts = ConcurrentHashMap.newKeySet();

    private final Set<SQLiteUpdateListener> updateListeners = new HashSet<>();
    private final Set<SQLiteCommitListener> commitListeners = new HashSet<>();

    public SQLiteDatabase(String url, String fileName, SQLiteConfig config) throws SQLException {
        this.url = url;
        this.fileName = fileName;
        this.config = config;
    }

    public String getUrl() {
        return url;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public SQLiteConfig getConfig() {
        return config;
    }

    /**
     * Aborts any pending operation and returns at its earliest opportunity.
     *
     * @see <a
     *     href="https://www.sqlite.org/c3ref/interrupt.html">https://www.sqlite.org/c3ref/interrupt.html</a>
     */
    public abstract void interrupt() throws SQLException;

    /**
     * Sets a <a href="https://www.sqlite.org/c3ref/busy_handler.html">busy handler</a> that sleeps
     * for a specified amount of time when a table is locked.
     *
     * @param ms Time to sleep in milliseconds.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/busy_timeout.html">https://www.sqlite.org/c3ref/busy_timeout.html</a>
     */
    public abstract void busy_timeout(int ms) throws SQLException;

    /**
     * Sets a <a href="https://www.sqlite.org/c3ref/busy_handler.html">busy handler</a> that sleeps
     * for a specified amount of time when a table is locked.
     *
     * @see <a
     *     href="https://www.sqlite.org/c3ref/busy_handler.html">https://www.sqlite.org/c3ref/busy_timeout.html</a>
     */
    public abstract void busy_handler(SQLiteBusyHandler busyHandler) throws SQLException;

    /**
     * Return English-language text that describes the error as either UTF-8 or UTF-16.
     *
     * @return Error description in English.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/errcode.html">https://www.sqlite.org/c3ref/errcode.html</a>
     */
    protected abstract String errmsg() throws SQLException;

    /**
     * Returns the value for SQLITE_VERSION, SQLITE_VERSION_NUMBER, and SQLITE_SOURCE_ID C
     * preprocessor macros that are associated with the library.
     *
     * @return Compile-time SQLite version information.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/libversion.html">https://www.sqlite.org/c3ref/libversion.html</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/c_source_id.html">https://www.sqlite.org/c3ref/c_source_id.html</a>
     */
    public abstract String libversion() throws SQLException;

    /**
     * @return Number of rows that were changed, inserted or deleted by the last SQL statement
     * @see <a
     *     href="https://www.sqlite.org/c3ref/changes.html">https://www.sqlite.org/c3ref/changes.html</a>
     */
    public abstract long changes() throws SQLException;

    /**
     * @return Number of row changes caused by INSERT, UPDATE or DELETE statements since the
     *     database connection was opened.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/total_changes.html">https://www.sqlite.org/c3ref/total_changes.html</a>
     */
    public abstract long total_changes() throws SQLException;

    /**
     * Enables or disables the sharing of the database cache and schema data structures between
     * connections to the same database.
     *
     * @param enable True to enable; false otherwise.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/enable_shared_cache.html">https://www.sqlite.org/c3ref/enable_shared_cache.html</a>
     * @see io.github.idoly.sqlite.SQLiteErrorCode
     */
    public abstract int shared_cache(boolean enable) throws SQLException;

    /**
     * Enables or disables loading of SQLite extensions.
     *
     * @param enable True to enable; false otherwise.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/load_extension.html">https://www.sqlite.org/c3ref/load_extension.html</a>
     */
    public abstract int enable_load_extension(boolean enable) throws SQLException;

    /**
     * Executes an SQL statement using the process of compiling, evaluating, and destroying the
     * prepared statement object.
     *
     * @param sql SQL statement to be executed.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/exec.html">https://www.sqlite.org/c3ref/exec.html</a>
     */
    public final synchronized void exec(String sql, boolean autoCommit) throws SQLException {
        StatementHandle pointer = prepare(sql);
        try {
            int rc = pointer.safeRunInt(SQLiteDatabase::step);
            switch (rc) {
                case SQLITE_DONE:
                    ensureAutoCommit(autoCommit);
                    return;
                case SQLITE_ROW:
                    return;
                default:
                    throwex(rc);
            }
        } finally {
            pointer.close();
        }
    }

    /**
     * Creates an SQLite interface to a database for the given connection.
     *
     * @param file The database.
     * @param openFlags File opening configurations (<a
     *     href="https://www.sqlite.org/c3ref/c_open_autoproxy.html">https://www.sqlite.org/c3ref/c_open_autoproxy.html</a>)
     * @see <a
     *     href="https://www.sqlite.org/c3ref/open.html">https://www.sqlite.org/c3ref/open.html</a>
     */
    public final synchronized void open(String file, int openFlags) throws SQLException {
        _open(file, openFlags);
        closed.set(false);

        if (fileName.startsWith("file:") && !fileName.contains("cache=")) {
            // URI cache overrides flags
            shared_cache(config.isEnabledSharedCache());
        }
        enable_load_extension(config.isEnabledLoadExtension());
        busy_timeout(config.getBusyTimeout());
    }

    /**
     * Closes a database connection and finalizes any remaining statements before the closing
     * operation.
     *
     * @see <a
     *     href="https://www.sqlite.org/c3ref/close.html">https://www.sqlite.org/c3ref/close.html</a>
     */
    public final synchronized void close() throws SQLException {
        // finalize any remaining statements before closing db
        for (StatementHandle element : stmts) {
            element.close();
        }

        // clean up commit object
        if (begin != null) begin.close();
        if (commit != null) commit.close();

        closed.set(true);
        _close();
    }

    /**
     * Compiles an SQL statement and tracks its handle until finalization.
     *
     * @param sql SQL statement to compile.
     * @return tracked statement handle
     * @see <a
     *     href="https://www.sqlite.org/c3ref/prepare.html">https://www.sqlite.org/c3ref/prepare.html</a>
     */
    public final synchronized StatementHandle prepareStatement(String sql) throws SQLException {
        if (sql == null) throw new NullPointerException("sql");
        StatementHandle statement = prepare(sql);
        if (!stmts.add(statement)) {
            statement.close();
            throw new IllegalStateException("Already tracking statement handle");
        }
        return statement;
    }

    /**
     * Destroys a statement.
     *
     * @param safePtr the pointer wrapper to remove from internal structures
     * @param ptr the raw pointer to free
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @throws SQLException if finalization fails
     * @see <a
     *     href="https://www.sqlite.org/c3ref/finalize.html">https://www.sqlite.org/c3ref/finalize.html</a>
     */
    public synchronized int finalize(StatementHandle safePtr, long ptr) throws SQLException {
        try {
            return finalize(ptr);
        } finally {
            stmts.remove(safePtr);
        }
    }

    /**
     * Creates an SQLite interface to a database with the provided open flags.
     *
     * @param filename The database to open.
     * @param openFlags File opening configurations (<a
     *     href="https://www.sqlite.org/c3ref/c_open_autoproxy.html">https://www.sqlite.org/c3ref/c_open_autoproxy.html</a>)
     * @see <a
     *     href="https://www.sqlite.org/c3ref/open.html">https://www.sqlite.org/c3ref/open.html</a>
     */
    protected abstract void _open(String filename, int openFlags) throws SQLException;

    /**
     * Closes the SQLite interface to a database.
     *
     * @see <a
     *     href="https://www.sqlite.org/c3ref/close.html">https://www.sqlite.org/c3ref/close.html</a>
     */
    protected abstract void _close() throws SQLException;

    /**
     * Complies, evaluates, executes and commits an SQL statement.
     *
     * @param sql An SQL statement.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/exec.html">https://www.sqlite.org/c3ref/exec.html</a>
     */
    public abstract int _exec(String sql) throws SQLException;

    /**
     * Complies an SQL statement.
     *
     * @param sql An SQL statement.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/prepare.html">https://www.sqlite.org/c3ref/prepare.html</a>
     */
    protected abstract StatementHandle prepare(String sql) throws SQLException;

    /**
     * Destroys a prepared statement.
     *
     * @param stmt Pointer to the statement pointer.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/finalize.html">https://www.sqlite.org/c3ref/finalize.html</a>
     */
    protected abstract int finalize(long stmt) throws SQLException;

    /**
     * Evaluates a statement.
     *
     * @param stmt Pointer to the statement.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/step.html">https://www.sqlite.org/c3ref/step.html</a>
     */
    public abstract int step(long stmt) throws SQLException;

    /**
     * Sets a prepared statement object back to its initial state, ready to be re-executed.
     *
     * @param stmt Pointer to the statement.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/reset.html">https://www.sqlite.org/c3ref/reset.html</a>
     */
    public abstract int reset(long stmt) throws SQLException;

    /** Clears all bound values on a prepared statement. */
    public abstract int clearBindings(long statement) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @return Number of parameters in a prepared SQL.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_parameter_count.html">https://www.sqlite.org/c3ref/bind_parameter_count.html</a>
     */
    public abstract int bind_parameter_count(long stmt) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @return Number of columns in the result set returned by the prepared statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_count.html">https://www.sqlite.org/c3ref/column_count.html</a>
     */
    public abstract int column_count(long stmt) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return Datatype code for the initial data type of the result column.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_blob.html">https://www.sqlite.org/c3ref/column_blob.html</a>
     */
    public abstract int column_type(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return Declared type of the table column for prepared statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_decltype.html">https://www.sqlite.org/c3ref/column_decltype.html</a>
     */
    public abstract String column_decltype(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return Original text of column name which is the declared in the CREATE TABLE statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_database_name.html">https://www.sqlite.org/c3ref/column_database_name.html</a>
     */
    public abstract String column_table_name(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col The number of column.
     * @return Name assigned to a particular column in the result set of a SELECT statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_name.html">https://www.sqlite.org/c3ref/column_name.html</a>
     */
    public abstract String column_name(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return Value of the column as text data type in the result set of a SELECT statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_blob.html">https://www.sqlite.org/c3ref/column_blob.html</a>
     */
    public abstract String column_text(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return BLOB value of the column in the result set of a SELECT statement
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_blob.html">https://www.sqlite.org/c3ref/column_blob.html</a>
     */
    public abstract byte[] column_blob(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return DOUBLE value of the column in the result set of a SELECT statement
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_blob.html">https://www.sqlite.org/c3ref/column_blob.html</a>
     */
    public abstract double column_double(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return LONG value of the column in the result set of a SELECT statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_blob.html">https://www.sqlite.org/c3ref/column_blob.html</a>
     */
    public abstract long column_long(long stmt, int col) throws SQLException;

    /**
     * @param stmt Pointer to the statement.
     * @param col Number of column.
     * @return INT value of column in the result set of a SELECT statement.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/column_blob.html">https://www.sqlite.org/c3ref/column_blob.html</a>
     */
    public abstract int column_int(long stmt, int col) throws SQLException;

    /**
     * Binds NULL value to prepared statements with the pointer to the statement object and the
     * index of the SQL parameter to be set to NULL.
     *
     * @param stmt Pointer to the statement.
     * @param pos The index of the SQL parameter to be set to NULL.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    protected abstract int bind_null(long stmt, int pos) throws SQLException;

    /**
     * Binds int value to prepared statements with the pointer to the statement object, the index of
     * the SQL parameter to be set and the value to bind to the parameter.
     *
     * @param stmt Pointer to the statement.
     * @param pos The index of the SQL parameter to be set.
     * @param v Value to bind to the parameter.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_blob.html">https://www.sqlite.org/c3ref/bind_blob.html</a>
     */
    protected abstract int bind_int(long stmt, int pos, int v) throws SQLException;

    /**
     * Binds long value to prepared statements with the pointer to the statement object, the index
     * of the SQL parameter to be set and the value to bind to the parameter.
     *
     * @param stmt Pointer to the statement.
     * @param pos The index of the SQL parameter to be set.
     * @param v Value to bind to the parameter.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_blob.html">https://www.sqlite.org/c3ref/bind_blob.html</a>
     */
    protected abstract int bind_long(long stmt, int pos, long v) throws SQLException;

    /**
     * Binds double value to prepared statements with the pointer to the statement object, the index
     * of the SQL parameter to be set and the value to bind to the parameter.
     *
     * @param stmt Pointer to the statement.
     * @param pos Index of the SQL parameter to be set.
     * @param v Value to bind to the parameter.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_blob.html">https://www.sqlite.org/c3ref/bind_blob.html</a>
     */
    protected abstract int bind_double(long stmt, int pos, double v) throws SQLException;

    /**
     * Binds text value to prepared statements with the pointer to the statement object, the index
     * of the SQL parameter to be set and the value to bind to the parameter.
     *
     * @param stmt Pointer to the statement.
     * @param pos Index of the SQL parameter to be set.
     * @param v value to bind to the parameter.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_blob.html">https://www.sqlite.org/c3ref/bind_blob.html</a>
     */
    protected abstract int bind_text(long stmt, int pos, String v) throws SQLException;

    /**
     * Binds blob value to prepared statements with the pointer to the statement object, the index
     * of the SQL parameter to be set and the value to bind to the parameter.
     *
     * @param stmt Pointer to the statement.
     * @param pos Index of the SQL parameter to be set.
     * @param v Value to bind to the parameter.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_blob.html">https://www.sqlite.org/c3ref/bind_blob.html</a>
     */
    protected abstract int bind_blob(long stmt, int pos, byte[] v) throws SQLException;

    /**
     * Sets the result of an SQL function as NULL with the pointer to the SQLite database context.
     *
     * @param context Pointer to the SQLite database context.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_null(long context) throws SQLException;

    /**
     * Sets the result of an SQL function as text data type with the pointer to the SQLite database
     * context and the the result value of String.
     *
     * @param context Pointer to the SQLite database context.
     * @param val Result value of an SQL function.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_text(long context, String val) throws SQLException;

    /**
     * Sets the result of an SQL function as blob data type with the pointer to the SQLite database
     * context and the the result value of byte array.
     *
     * @param context Pointer to the SQLite database context.
     * @param val Result value of an SQL function.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_blob(long context, byte[] val) throws SQLException;

    /**
     * Sets the result of an SQL function as double data type with the pointer to the SQLite
     * database context and the the result value of double.
     *
     * @param context Pointer to the SQLite database context.
     * @param val Result value of an SQL function.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_double(long context, double val) throws SQLException;

    /**
     * Sets the result of an SQL function as long data type with the pointer to the SQLite database
     * context and the the result value of long.
     *
     * @param context Pointer to the SQLite database context.
     * @param val Result value of an SQL function.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_long(long context, long val) throws SQLException;

    /**
     * Sets the result of an SQL function as int data type with the pointer to the SQLite database
     * context and the the result value of int.
     *
     * @param context Pointer to the SQLite database context.
     * @param val Result value of an SQL function.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_int(long context, int val) throws SQLException;

    /**
     * Sets the result of an SQL function as an error with the pointer to the SQLite database
     * context and the the error of String.
     *
     * @param context Pointer to the SQLite database context.
     * @param err Error result of an SQL function.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/result_blob.html">https://www.sqlite.org/c3ref/result_blob.html</a>
     */
    public abstract void result_error(long context, String err) throws SQLException;

    /**
     * @param f SQLite function object.
     * @param arg Pointer to the parameter of the SQLite function or aggregate.
     * @return Parameter value of the given SQLite function or aggregate in text data type.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/value_blob.html">https://www.sqlite.org/c3ref/value_blob.html</a>
     */
    public abstract String value_text(SQLiteFunction f, int arg) throws SQLException;

    /**
     * @param f SQLite function object.
     * @param arg Pointer to the parameter of the SQLite function or aggregate.
     * @return Parameter value of the given SQLite function or aggregate in blob data type.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/value_blob.html">https://www.sqlite.org/c3ref/value_blob.html</a>
     */
    public abstract byte[] value_blob(SQLiteFunction f, int arg) throws SQLException;

    /**
     * @param f SQLite function object.
     * @param arg Pointer to the parameter of the SQLite function or aggregate.
     * @return Parameter value of the given SQLite function or aggregate in double data type
     * @see <a
     *     href="https://www.sqlite.org/c3ref/value_blob.html">https://www.sqlite.org/c3ref/value_blob.html</a>
     */
    public abstract double value_double(SQLiteFunction f, int arg) throws SQLException;

    /**
     * @param f SQLite function object.
     * @param arg Pointer to the parameter of the SQLite function or aggregate.
     * @return Parameter value of the given SQLite function or aggregate in long data type.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/value_blob.html">https://www.sqlite.org/c3ref/value_blob.html</a>
     */
    public abstract long value_long(SQLiteFunction f, int arg) throws SQLException;

    /**
     * Accesses the parameter values on the function or aggregate in int data type with the function
     * object and the parameter value.
     *
     * @param f SQLite function object.
     * @param arg Pointer to the parameter of the SQLite function or aggregate.
     * @return Parameter value of the given SQLite function or aggregate.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/value_blob.html">https://www.sqlite.org/c3ref/value_blob.html</a>
     */
    public abstract int value_int(SQLiteFunction f, int arg) throws SQLException;

    /**
     * @param f SQLite function object.
     * @param arg Pointer to the parameter of the SQLite function or aggregate.
     * @return Parameter datatype of the function or aggregate in int data type.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/value_blob.html">https://www.sqlite.org/c3ref/value_blob.html</a>
     */
    public abstract int value_type(SQLiteFunction f, int arg) throws SQLException;

    /**
     * Create a user defined function with given function name and the function object.
     *
     * @param name The function name to be created.
     * @param f SQLite function object.
     * @param flags Extra flags to use when creating the function, such as {@link
     *     SQLiteFunction#FLAG_DETERMINISTIC}
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/create_function.html">https://www.sqlite.org/c3ref/create_function.html</a>
     */
    public abstract int create_function(String name, SQLiteFunction f, int nArgs, int flags)
            throws SQLException;

    /**
     * De-registers a user defined function
     *
     * @param name Name of the function to deregister.
     * @param nArgs Number of function arguments, or -1 for a variadic function.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    public abstract int destroy_function(String name, int nArgs) throws SQLException;

    /**
     * Create a user defined collation with given collation name and the collation object.
     *
     * @param name The collation name to be created.
     * @param c SQLite collation object.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/create_collation.html">https://www.sqlite.org/c3ref/create_collation.html</a>
     */
    public abstract int create_collation(String name, SQLiteCollation c) throws SQLException;

    /**
     * Create a user defined collation with given collation name and the collation object.
     *
     * @param name The collation name to be created.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    public abstract int destroy_collation(String name) throws SQLException;

    /**
     * @param dbName Database name to be backed up.
     * @param destFileName Target backup file name.
     * @param observer ProgressObserver object.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    public abstract int backup(String dbName, String destFileName, ProgressObserver observer)
            throws SQLException;

    /**
     * @param dbName Database name to be backed up.
     * @param destFileName Target backup file name.
     * @param observer ProgressObserver object.
     * @param sleepTimeMillis time to wait during a backup/restore operation if sqlite3_backup_step
     *     returns SQLITE_BUSY before continuing
     * @param nTimeouts the number of times sqlite3_backup_step can return SQLITE_BUSY before
     *     failing
     * @param pagesPerStep the number of pages to copy in each sqlite3_backup_step. If this is
     *     negative, the entire database is copied at once.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    public abstract int backup(
            String dbName,
            String destFileName,
            ProgressObserver observer,
            int sleepTimeMillis,
            int nTimeouts,
            int pagesPerStep)
            throws SQLException;

    /**
     * @param dbName Database name for restoring data.
     * @param sourceFileName Source file name.
     * @param observer ProgressObserver object.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    public abstract int restore(String dbName, String sourceFileName, ProgressObserver observer)
            throws SQLException;

    /**
     * @param dbName the name of the db to restore
     * @param sourceFileName the filename of the source db to restore
     * @param observer ProgressObserver object.
     * @param sleepTimeMillis time to wait during a backup/restore operation if sqlite3_backup_step
     *     returns SQLITE_BUSY before continuing
     * @param nTimeouts the number of times sqlite3_backup_step can return SQLITE_BUSY before
     *     failing
     * @param pagesPerStep the number of pages to copy in each sqlite3_backup_step. If this is
     *     negative, the entire database is copied at once.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     */
    public abstract int restore(
            String dbName,
            String sourceFileName,
            ProgressObserver observer,
            int sleepTimeMillis,
            int nTimeouts,
            int pagesPerStep)
            throws SQLException;

    /**
     * @param id The id of the limit.
     * @param value The new value of the limit.
     * @return The prior value of the limit
     * @see <a
     *     href="https://www.sqlite.org/c3ref/limit.html">https://www.sqlite.org/c3ref/limit.html</a>
     */
    public abstract int limit(int id, int value) throws SQLException;

    public interface ProgressObserver {
        void progress(int remaining, int pageCount);
    }

    /** Progress handler */
    public abstract void register_progress_handler(
            int vmCalls, SQLiteProgressHandler progressHandler) throws SQLException;

    public abstract void clear_progress_handler() throws SQLException;

    /**
     * Returns an array describing the attributes (not null, primary key and auto increment) of
     * columns.
     *
     * @param stmt Pointer to the statement.
     * @return Column attribute array.<br>
     *     index[col][0] = true if column constrained NOT NULL;<br>
     *     index[col][1] = true if column is part of the primary key; <br>
     *     index[col][2] = true if column is auto-increment.
     */
    public abstract boolean[][] column_metadata(long stmt) throws SQLException;

    // COMPOUND FUNCTIONS ////////////////////////////////////////////

    /**
     * Returns an array of column names in the result set of the SELECT statement.
     *
     * @param stmt Stmt object.
     * @return String array of column names.
     */
    public final synchronized String[] column_names(long stmt) throws SQLException {
        String[] names = new String[column_count(stmt)];
        for (int i = 0; i < names.length; i++) {
            names[i] = column_name(stmt, i);
        }
        return names;
    }

    /**
     * Bind values to prepared statements
     *
     * @param stmt Pointer to the statement.
     * @param pos Index of the SQL parameter to be set to NULL.
     * @param v Value to bind to the parameter.
     * @return <a href="https://www.sqlite.org/c3ref/c_abort.html">SQLite result code</a>
     * @see <a
     *     href="https://www.sqlite.org/c3ref/bind_blob.html">https://www.sqlite.org/c3ref/bind_blob.html</a>
     */
    final synchronized int sqlbind(long stmt, int pos, Object v) throws SQLException {
        int parameter = pos + 1;
        return switch (v) {
            case null -> bind_null(stmt, parameter);
            case Integer value -> bind_int(stmt, parameter, value);
            case Short value -> bind_int(stmt, parameter, value.intValue());
            case Long value -> bind_long(stmt, parameter, value);
            case Float value -> bind_double(stmt, parameter, value.doubleValue());
            case Double value -> bind_double(stmt, parameter, value);
            case String value -> bind_text(stmt, parameter, value);
            case byte[] value -> bind_blob(stmt, parameter, value);
            default -> throw new SQLException("unexpected param type: " + v.getClass());
        };
    }

    /**
     * Submits a batch of commands to the database for execution.
     *
     * @see java.sql.Statement#executeBatch()
     * @param stmt Pointer of Stmt object.
     * @param count Number of SQL statements.
     * @param vals Array of parameter values.
     * @return Array of the number of rows changed or inserted or deleted for each command if all
     *     commands execute successfully;
     * @throws SQLException if statement is not open or is being used elsewhere
     */
    public final synchronized long[] executeBatch(
            StatementHandle stmt, int count, Object[] vals, boolean autoCommit)
            throws SQLException {
        return stmt.safeRun((db, ptr) -> this.executeBatch(ptr, count, vals, autoCommit));
    }

    private synchronized long[] executeBatch(
            long stmt, int count, Object[] vals, boolean autoCommit) throws SQLException {
        if (count < 1) {
            throw new SQLException("count (" + count + ") < 1");
        }

        final int params = bind_parameter_count(stmt);

        int rc;
        long[] changes = new long[count];

        try {
            for (int i = 0; i < count; i++) {
                reset(stmt);
                for (int j = 0; j < params; j++) {
                    rc = sqlbind(stmt, j, vals[(i * params) + j]);
                    if (rc != SQLITE_OK) {
                        throwex(rc);
                    }
                }

                rc = step(stmt);
                if (rc != SQLITE_DONE) {
                    reset(stmt);
                    if (rc == SQLITE_ROW) {
                        // don't use the constructor with long because of
                        // https://github.com/xerial/sqlite-jdbc/issues/1378
                        throw new BatchUpdateException(
                                "batch entry " + i + ": query returns results",
                                null,
                                0,
                                Arrays.stream(changes).mapToInt(l -> (int) l).toArray(),
                                null);
                    }
                    throwex(rc);
                }

                changes[i] = changes();
            }
        } finally {
            ensureAutoCommit(autoCommit);
        }

        reset(stmt);
        return changes;
    }

    /**
     * @see <a
     *     href="https://www.sqlite.org/c_interface.html#sqlite_exec">https://www.sqlite.org/c_interface.html#sqlite_exec</a>
     * @param stmt Stmt object.
     * @param vals Array of parameter values.
     * @return True if a row of ResultSet is ready; false otherwise.
     */
    public final synchronized boolean execute(
            StatementHandle statement, Object[] values, boolean autoCommit) throws SQLException {
        int statusCode = statement.safeRunInt((database, pointer) -> execute(pointer, values));
        switch (statusCode & 0xFF) {
            case SQLITE_DONE:
                ensureAutoCommit(autoCommit);
                return false;
            case SQLITE_ROW:
                return true;
            case SQLITE_BUSY:
            case SQLITE_LOCKED:
            case SQLITE_MISUSE:
            case SQLITE_CONSTRAINT:
                throw newSQLException(statusCode);
            default:
                statement.close();
                throw newSQLException(statusCode);
        }
    }

    private synchronized int execute(long ptr, Object[] vals) throws SQLException {
        if (vals != null) {
            final int params = bind_parameter_count(ptr);
            if (params > vals.length) {
                throw new SQLException(
                        "assertion failure: param count ("
                                + params
                                + ") > value count ("
                                + vals.length
                                + ")");
            }

            for (int i = 0; i < params; i++) {
                int rc = sqlbind(ptr, i, vals[i]);
                if (rc != SQLITE_OK) {
                    throwex(rc);
                }
            }
        }

        int statusCode = step(ptr);
        if ((statusCode & 0xFF) == SQLITE_DONE) reset(ptr);
        return statusCode;
    }

    /**
     * Executes the given SQL statement using the one-step query execution interface.
     *
     * @param sql SQL statement to be executed.
     * @return True if a row of ResultSet is ready; false otherwise.
     * @see <a
     *     href="https://www.sqlite.org/c3ref/exec.html">https://www.sqlite.org/c3ref/exec.html</a>
     */
    public final synchronized boolean execute(String sql, boolean autoCommit) throws SQLException {
        int statusCode = _exec(sql);
        switch (statusCode) {
            case SQLITE_OK:
                return false;
            case SQLITE_DONE:
                ensureAutoCommit(autoCommit);
                return false;
            case SQLITE_ROW:
                return true;
            default:
                throw newSQLException(statusCode);
        }
    }

    /**
     * Execute an SQL INSERT, UPDATE or DELETE statement with the Stmt object and an array of
     * parameter values of the SQL statement..
     *
     * @param stmt Stmt object.
     * @param vals Array of parameter values.
     * @return Number of database rows that were changed or inserted or deleted by the most recently
     *     completed SQL.
     */
    public final synchronized long executeUpdate(
            StatementHandle statement, Object[] values, boolean autoCommit) throws SQLException {
        try {
            if (execute(statement, values, autoCommit)) {
                throw new SQLException("query returns results");
            }
        } finally {
            if (!statement.isClosed()) {
                statement.safeRunInt(SQLiteDatabase::reset);
            }
        }
        return changes();
    }

    protected abstract void set_commit_listener(boolean enabled);

    protected abstract void set_update_listener(boolean enabled);

    public synchronized void addUpdateListener(SQLiteUpdateListener listener) {
        if (updateListeners.add(listener) && updateListeners.size() == 1) {
            set_update_listener(true);
        }
    }

    public synchronized void addCommitListener(SQLiteCommitListener listener) {
        if (commitListeners.add(listener) && commitListeners.size() == 1) {
            set_commit_listener(true);
        }
    }

    public synchronized void removeUpdateListener(SQLiteUpdateListener listener) {
        if (updateListeners.remove(listener) && updateListeners.isEmpty()) {
            set_update_listener(false);
        }
    }

    public synchronized void removeCommitListener(SQLiteCommitListener listener) {
        if (commitListeners.remove(listener) && commitListeners.isEmpty()) {
            set_commit_listener(false);
        }
    }

    public final void onUpdate(int type, String database, String table, long rowId) {
        Set<SQLiteUpdateListener> listeners;

        synchronized (this) {
            listeners = new HashSet<>(updateListeners);
        }

        for (SQLiteUpdateListener listener : listeners) {
            SQLiteUpdateListener.Type operationType;

            switch (type) {
                case 18:
                    operationType = SQLiteUpdateListener.Type.INSERT;
                    break;
                case 9:
                    operationType = SQLiteUpdateListener.Type.DELETE;
                    break;
                case 23:
                    operationType = SQLiteUpdateListener.Type.UPDATE;
                    break;
                default:
                    throw new AssertionError("Unknown type: " + type);
            }

            listener.onUpdate(operationType, database, table, rowId);
        }
    }

    public final void onCommit(boolean commit) {
        Set<SQLiteCommitListener> listeners;

        synchronized (this) {
            listeners = new HashSet<>(commitListeners);
        }

        for (SQLiteCommitListener listener : listeners) {
            if (commit) listener.onCommit();
            else listener.onRollback();
        }
    }

    /** Throws SQLException with error message. */
    final void throwex() throws SQLException {
        throw new SQLException(errmsg());
    }

    /**
     * Throws SQLException with error code.
     *
     * @param errorCode Error code to be passed.
     * @throws SQLException Formatted SQLException with error code.
     */
    public final void throwex(int errorCode) throws SQLException {
        throw newSQLException(errorCode);
    }

    /**
     * Throws SQL Exception with error code and message.
     *
     * @param errorCode Error code to be passed.
     * @param errorMessage Error message to be passed.
     * @throws SQLException Formatted SQLException with error code and message.
     */
    static void throwex(int errorCode, String errorMessage) throws SQLException {
        throw newSQLException(errorCode, errorMessage);
    }

    /**
     * Throws formatted SQLException with error code and message.
     *
     * @param errorCode Error code to be passed.
     * @param errorMessage Error message to be passed.
     * @return Formatted SQLException with error code and message.
     */
    public static SQLiteException newSQLException(int errorCode, String errorMessage) {
        SQLiteErrorCode code = SQLiteErrorCode.getErrorCode(errorCode);
        String msg;
        if (code == SQLiteErrorCode.UNKNOWN_ERROR) {
            msg = String.format("%s:%s (%s)", code, errorCode, errorMessage);
        } else {
            msg = String.format("%s (%s)", code, errorMessage);
        }
        return new SQLiteException(msg, code);
    }

    /**
     * Throws SQL Exception with error code.
     *
     * @param errorCode Error code to be passed.
     * @return SQLException with error code and message.
     * @throws SQLException Formatted SQLException with error code
     */
    private SQLiteException newSQLException(int errorCode) throws SQLException {
        return newSQLException(errorCode, errmsg());
    }

    /**
     * SQLite and the JDBC API have very different ideas about the meaning of auto-commit. Under
     * SQLiteDriver, when executeUpdate() returns in auto-commit mode (the default), the programmer
     * assumes the data has been written to disk. In SQLite however, a call to sqlite3_step() with
     * an INSERT statement can return SQLITE_OK, and yet the data is still in limbo.
     *
     * <p>This limbo appears when another statement on the database is active, e.g. a SELECT. SQLite
     * auto-commit waits until the final read statement finishes, and then writes whatever updates
     * have already been OKed. So if a program crashes before the reads are complete, data is lost.
     * E.g:
     *
     * <p>select begins insert select continues select finishes
     *
     * <p>Works as expected, however
     *
     * <p>select beings insert select continues crash
     *
     * <p>Results in the data never being written to disk.
     *
     * <p>As a solution, we call "commit" after every statement in auto-commit mode.
     */
    final void ensureAutoCommit(boolean autoCommit) throws SQLException {
        if (!autoCommit) {
            return;
        }

        ensureBeginAndCommit();

        begin.safeRunConsume(
                (db, beginPtr) -> {
                    commit.safeRunConsume(
                            (db2, commitPtr) -> ensureAutocommit(beginPtr, commitPtr));
                });
    }

    private void ensureBeginAndCommit() throws SQLException {
        if (begin == null) {
            synchronized (this) {
                if (begin == null) {
                    begin = prepare("begin;");
                }
            }
        }
        if (commit == null) {
            synchronized (this) {
                if (commit == null) {
                    commit = prepare("commit;");
                }
            }
        }
    }

    private void ensureAutocommit(long beginPtr, long commitPtr) throws SQLException {
        try {
            if (step(beginPtr) != SQLITE_DONE) {
                return; // assume we are in a transaction
            }
            int rc = step(commitPtr);
            if (rc != SQLITE_DONE) {
                reset(commitPtr);
                throwex(rc);
            }
            // throw new SQLException("unable to auto-commit");
        } finally {
            reset(beginPtr);
            reset(commitPtr);
        }
    }

    public abstract byte[] serialize(String schema) throws SQLException;

    public abstract void deserialize(String schema, byte[] buff) throws SQLException;
}
