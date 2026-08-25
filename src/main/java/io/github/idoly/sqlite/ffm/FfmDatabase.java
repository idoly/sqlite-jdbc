package io.github.idoly.sqlite.ffm;

import static io.github.idoly.sqlite.core.SQLiteResultCodes.*;

import io.github.idoly.sqlite.SQLiteBusyHandler;
import io.github.idoly.sqlite.SQLiteCollation;
import io.github.idoly.sqlite.SQLiteConfig;
import io.github.idoly.sqlite.SQLiteFunction;
import io.github.idoly.sqlite.SQLiteProgressHandler;
import io.github.idoly.sqlite.core.SQLiteDatabase;
import io.github.idoly.sqlite.core.StatementHandle;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** SQLite backend implemented directly with the JDK Foreign Function and Memory API. */
public final class FfmDatabase extends SQLiteDatabase {
    private static final int DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS = 100;
    private static final int DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL = 3;
    private static final int DEFAULT_PAGES_PER_BACKUP_STEP = 100;

    /** SQLite connection handle. */
    private volatile long pointer;

    private final Arena callbackArena = Arena.ofShared();
    private final Map<String, MemorySegment> collationStubs = new HashMap<>();
    private final Map<String, SQLiteCollation> collations = new HashMap<>();
    private final Map<FunctionKey, SQLiteFfmBindings.FunctionRegistration> functions =
            new HashMap<>();
    private SQLiteBusyHandler busyHandler;
    private MemorySegment busyHandlerStub = MemorySegment.NULL;
    private SQLiteProgressHandler progressHandler;
    private MemorySegment progressHandlerStub = MemorySegment.NULL;
    private MemorySegment updateHookStub = MemorySegment.NULL;
    private MemorySegment commitHookStub = MemorySegment.NULL;
    private MemorySegment rollbackHookStub = MemorySegment.NULL;

    public FfmDatabase(String url, String fileName, SQLiteConfig config) throws SQLException {
        super(url, fileName, config);
    }

    /** Initializes the FFM symbol table. */
    public static boolean load() {
        return SQLiteFfmBindings.initialize();
    }

    @Override
    protected synchronized void _open(String file, int openFlags) throws SQLException {
        pointer = SQLiteFfmBindings.open(stringToUtf8ByteArray(file), openFlags);
    }

    @Override
    protected synchronized void _close() throws SQLException {
        if (pointer == 0) return;
        SQLiteFfmBindings.setBusyHandler(pointer, MemorySegment.NULL);
        SQLiteFfmBindings.setProgressHandler(pointer, 0, MemorySegment.NULL);
        SQLiteFfmBindings.setUpdateHook(pointer, MemorySegment.NULL);
        SQLiteFfmBindings.setTransactionHooks(pointer, MemorySegment.NULL, MemorySegment.NULL);
        int result = SQLiteFfmBindings.close(pointer);
        if (result != SQLITE_OK)
            throw newSQLException(result, SQLiteFfmBindings.errorMessage(pointer));
        pointer = 0;
        busyHandler = null;
        busyHandlerStub = MemorySegment.NULL;
        progressHandler = null;
        progressHandlerStub = MemorySegment.NULL;
        updateHookStub = MemorySegment.NULL;
        commitHookStub = MemorySegment.NULL;
        rollbackHookStub = MemorySegment.NULL;
        collations.clear();
        collationStubs.clear();
        functions.clear();
        callbackArena.close();
    }

    @Override
    public synchronized int _exec(String sql) throws SQLException {
        return SQLiteFfmBindings.execute(databasePointer(), stringToUtf8ByteArray(sql));
    }

    @Override
    public synchronized int shared_cache(boolean enable) throws SQLException {
        return SQLiteFfmBindings.enableSharedCache(enable);
    }

    @Override
    public synchronized int enable_load_extension(boolean enable) throws SQLException {
        return SQLiteFfmBindings.enableLoadExtension(databasePointer(), enable);
    }

    @Override
    public void interrupt() throws SQLException {
        long database = pointer;
        if (database != 0) SQLiteFfmBindings.interrupt(database);
    }

    @Override
    public synchronized void busy_timeout(int ms) throws SQLException {
        SQLiteFfmBindings.busyTimeout(databasePointer(), ms);
    }

    @Override
    public synchronized void busy_handler(SQLiteBusyHandler handler) throws SQLException {
        MemorySegment callback =
                handler == null
                        ? MemorySegment.NULL
                        : SQLiteFfmBindings.busyCallbackStub(callbackArena, handler);
        int result = SQLiteFfmBindings.setBusyHandler(databasePointer(), callback);
        if (result != SQLITE_OK) throw newSQLException(result, errmsg());
        busyHandler = handler;
        busyHandlerStub = callback;
    }

    @Override
    protected synchronized StatementHandle prepare(String sql) throws SQLException {
        return new StatementHandle(
                this, SQLiteFfmBindings.prepare(databasePointer(), stringToUtf8ByteArray(sql)));
    }

    @Override
    protected synchronized String errmsg() {
        return pointer == 0 ? "SQLite database is closed" : SQLiteFfmBindings.errorMessage(pointer);
    }

    @Override
    public synchronized String libversion() {
        return SQLiteFfmBindings.libraryVersion();
    }

    @Override
    public synchronized long changes() throws SQLException {
        return SQLiteFfmBindings.changes(databasePointer());
    }

    @Override
    public synchronized long total_changes() throws SQLException {
        return SQLiteFfmBindings.totalChanges(databasePointer());
    }

    @Override
    protected synchronized int finalize(long stmt) throws SQLException {
        return SQLiteFfmBindings.finalizeStatement(stmt);
    }

    @Override
    public synchronized int step(long stmt) throws SQLException {
        return SQLiteFfmBindings.step(stmt);
    }

    @Override
    public synchronized int reset(long stmt) throws SQLException {
        return SQLiteFfmBindings.reset(stmt);
    }

    @Override
    public synchronized int clearBindings(long statement) throws SQLException {
        return SQLiteFfmBindings.clearBindings(statement);
    }

    @Override
    public synchronized int bind_parameter_count(long stmt) throws SQLException {
        return SQLiteFfmBindings.bindParameterCount(stmt);
    }

    @Override
    public synchronized int column_count(long stmt) throws SQLException {
        return SQLiteFfmBindings.columnCount(stmt);
    }

    @Override
    public synchronized int column_type(long stmt, int col) throws SQLException {
        return SQLiteFfmBindings.columnType(stmt, col);
    }

    @Override
    public synchronized String column_decltype(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(SQLiteFfmBindings.columnDeclaredType(stmt, col));
    }

    @Override
    public synchronized String column_table_name(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(SQLiteFfmBindings.columnTableName(stmt, col));
    }

    @Override
    public synchronized String column_name(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(SQLiteFfmBindings.columnName(stmt, col));
    }

    @Override
    public synchronized String column_text(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(SQLiteFfmBindings.columnText(stmt, col));
    }

    @Override
    public synchronized byte[] column_blob(long stmt, int col) throws SQLException {
        return SQLiteFfmBindings.columnBlob(stmt, col);
    }

    @Override
    public synchronized double column_double(long stmt, int col) throws SQLException {
        return SQLiteFfmBindings.columnDouble(stmt, col);
    }

    @Override
    public synchronized long column_long(long stmt, int col) throws SQLException {
        return SQLiteFfmBindings.columnLong(stmt, col);
    }

    @Override
    public synchronized int column_int(long stmt, int col) throws SQLException {
        return SQLiteFfmBindings.columnInt(stmt, col);
    }

    @Override
    protected synchronized int bind_null(long stmt, int pos) throws SQLException {
        return SQLiteFfmBindings.bindNull(stmt, pos);
    }

    @Override
    protected synchronized int bind_int(long stmt, int pos, int value) throws SQLException {
        return SQLiteFfmBindings.bindInt(stmt, pos, value);
    }

    @Override
    protected synchronized int bind_long(long stmt, int pos, long value) throws SQLException {
        return SQLiteFfmBindings.bindLong(stmt, pos, value);
    }

    @Override
    protected synchronized int bind_double(long stmt, int pos, double value) throws SQLException {
        return SQLiteFfmBindings.bindDouble(stmt, pos, value);
    }

    @Override
    protected synchronized int bind_text(long stmt, int pos, String value) throws SQLException {
        return SQLiteFfmBindings.bindText(stmt, pos, stringToUtf8ByteArray(value));
    }

    @Override
    protected synchronized int bind_blob(long stmt, int pos, byte[] value) throws SQLException {
        return SQLiteFfmBindings.bindBlob(stmt, pos, value);
    }

    @Override
    public synchronized void result_null(long context) throws SQLException {
        SQLiteFfmBindings.resultNull(context);
    }

    @Override
    public synchronized void result_text(long context, String value) throws SQLException {
        SQLiteFfmBindings.resultText(context, value);
    }

    @Override
    public synchronized void result_blob(long context, byte[] value) throws SQLException {
        SQLiteFfmBindings.resultBlob(context, value);
    }

    @Override
    public synchronized void result_double(long context, double value) throws SQLException {
        SQLiteFfmBindings.resultDouble(context, value);
    }

    @Override
    public synchronized void result_long(long context, long value) throws SQLException {
        SQLiteFfmBindings.resultLong(context, value);
    }

    @Override
    public synchronized void result_int(long context, int value) throws SQLException {
        SQLiteFfmBindings.resultInt(context, value);
    }

    @Override
    public synchronized void result_error(long context, String error) throws SQLException {
        SQLiteFfmBindings.resultError(context, error);
    }

    @Override
    public synchronized String value_text(SQLiteFunction function, int argument)
            throws SQLException {
        return SQLiteFfmBindings.valueText(function, argument);
    }

    @Override
    public synchronized byte[] value_blob(SQLiteFunction function, int argument)
            throws SQLException {
        return SQLiteFfmBindings.valueBlob(function, argument);
    }

    @Override
    public synchronized double value_double(SQLiteFunction function, int argument)
            throws SQLException {
        return SQLiteFfmBindings.valueDouble(function, argument);
    }

    @Override
    public synchronized long value_long(SQLiteFunction function, int argument) throws SQLException {
        return SQLiteFfmBindings.valueLong(function, argument);
    }

    @Override
    public synchronized int value_int(SQLiteFunction function, int argument) throws SQLException {
        return SQLiteFfmBindings.valueInt(function, argument);
    }

    @Override
    public synchronized int value_type(SQLiteFunction function, int argument) throws SQLException {
        return SQLiteFfmBindings.valueType(function, argument);
    }

    @Override
    public synchronized int create_function(
            String name, SQLiteFunction function, int nArgs, int flags) throws SQLException {
        SQLiteFfmBindings.FunctionRegistration registration =
                SQLiteFfmBindings.createFunction(
                        callbackArena, databasePointer(), name, function, nArgs, flags);
        functions.put(FunctionKey.of(name, nArgs), registration);
        return SQLITE_OK;
    }

    @Override
    public synchronized int destroy_function(String name, int nArgs) throws SQLException {
        int result = SQLiteFfmBindings.destroyFunction(databasePointer(), name, nArgs);
        if (result == SQLITE_OK) functions.remove(FunctionKey.of(name, nArgs));
        return result;
    }

    @Override
    public synchronized int create_collation(String name, SQLiteCollation collation)
            throws SQLException {
        MemorySegment callback = SQLiteFfmBindings.collationCallbackStub(callbackArena, collation);
        int result = SQLiteFfmBindings.setCollation(databasePointer(), name, callback);
        if (result == SQLITE_OK) {
            collations.put(name, collation);
            collationStubs.put(name, callback);
        }
        return result;
    }

    @Override
    public synchronized int destroy_collation(String name) throws SQLException {
        int result = SQLiteFfmBindings.setCollation(databasePointer(), name, MemorySegment.NULL);
        if (result == SQLITE_OK) {
            collations.remove(name);
            collationStubs.remove(name);
        }
        return result;
    }

    @Override
    public synchronized int limit(int id, int value) throws SQLException {
        return SQLiteFfmBindings.limit(databasePointer(), id, value);
    }

    @Override
    public int backup(String dbName, String destFileName, ProgressObserver observer)
            throws SQLException {
        return backup(
                dbName,
                destFileName,
                observer,
                DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS,
                DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL,
                DEFAULT_PAGES_PER_BACKUP_STEP);
    }

    @Override
    public int backup(
            String dbName,
            String destFileName,
            ProgressObserver observer,
            int sleepTimeMillis,
            int nTimeouts,
            int pagesPerStep)
            throws SQLException {
        return SQLiteFfmBindings.copyDatabase(
                databasePointer(),
                dbName,
                destFileName,
                false,
                observer,
                sleepTimeMillis,
                nTimeouts,
                pagesPerStep);
    }

    @Override
    public int restore(String dbName, String sourceFileName, ProgressObserver observer)
            throws SQLException {
        return restore(
                dbName,
                sourceFileName,
                observer,
                DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS,
                DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL,
                DEFAULT_PAGES_PER_BACKUP_STEP);
    }

    @Override
    public int restore(
            String dbName,
            String sourceFileName,
            ProgressObserver observer,
            int sleepTimeMillis,
            int nTimeouts,
            int pagesPerStep)
            throws SQLException {
        return SQLiteFfmBindings.copyDatabase(
                databasePointer(),
                dbName,
                sourceFileName,
                true,
                observer,
                sleepTimeMillis,
                nTimeouts,
                pagesPerStep);
    }

    @Override
    public synchronized boolean[][] column_metadata(long stmt) throws SQLException {
        int columns = column_count(stmt);
        boolean[][] metadata = new boolean[columns][3];
        long database = databasePointer();
        for (int column = 0; column < columns; column++) {
            metadata[column] =
                    SQLiteFfmBindings.columnMetadata(
                            database, column_table_name(stmt, column), column_name(stmt, column));
        }
        return metadata;
    }

    @Override
    protected synchronized void set_commit_listener(boolean enabled) {
        try {
            if (enabled && commitHookStub.address() == 0) {
                commitHookStub = SQLiteFfmBindings.commitCallbackStub(callbackArena, this);
                rollbackHookStub = SQLiteFfmBindings.rollbackCallbackStub(callbackArena, this);
            }
            SQLiteFfmBindings.setTransactionHooks(
                    databasePointer(),
                    enabled ? commitHookStub : MemorySegment.NULL,
                    enabled ? rollbackHookStub : MemorySegment.NULL);
            if (!enabled) {
                commitHookStub = MemorySegment.NULL;
                rollbackHookStub = MemorySegment.NULL;
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not configure transaction listeners", error);
        }
    }

    @Override
    protected synchronized void set_update_listener(boolean enabled) {
        try {
            if (enabled && updateHookStub.address() == 0) {
                updateHookStub = SQLiteFfmBindings.updateCallbackStub(callbackArena, this);
            }
            SQLiteFfmBindings.setUpdateHook(
                    databasePointer(), enabled ? updateHookStub : MemorySegment.NULL);
            if (!enabled) updateHookStub = MemorySegment.NULL;
        } catch (SQLException error) {
            throw new IllegalStateException("Could not configure update listeners", error);
        }
    }

    @Override
    public synchronized void register_progress_handler(int vmCalls, SQLiteProgressHandler handler)
            throws SQLException {
        if (handler == null) throw new SQLException("Progress handler cannot be null");
        MemorySegment callback = SQLiteFfmBindings.progressCallbackStub(callbackArena, handler);
        SQLiteFfmBindings.setProgressHandler(databasePointer(), vmCalls, callback);
        progressHandler = handler;
        progressHandlerStub = callback;
    }

    @Override
    public synchronized void clear_progress_handler() throws SQLException {
        SQLiteFfmBindings.setProgressHandler(databasePointer(), 0, MemorySegment.NULL);
        progressHandler = null;
        progressHandlerStub = MemorySegment.NULL;
    }

    long getBusyHandler() {
        return busyHandlerStub.address();
    }

    long getCommitListener() {
        return commitHookStub.address();
    }

    long getUpdateListener() {
        return updateHookStub.address();
    }

    long getProgressHandler() {
        return progressHandlerStub.address();
    }

    @Override
    public synchronized byte[] serialize(String schema) throws SQLException {
        return SQLiteFfmBindings.serialize(databasePointer(), schema);
    }

    @Override
    public synchronized void deserialize(String schema, byte[] buffer) throws SQLException {
        SQLiteFfmBindings.deserialize(databasePointer(), schema, buffer);
    }

    static byte[] stringToUtf8ByteArray(String value) {
        return value == null ? null : value.getBytes(StandardCharsets.UTF_8);
    }

    static String utf8ByteBufferToString(ByteBuffer buffer) {
        if (buffer == null) return null;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record FunctionKey(String name, int arguments) {
        private static FunctionKey of(String name, int arguments) {
            return new FunctionKey(name.toLowerCase(Locale.ROOT), arguments);
        }
    }

    private long databasePointer() throws SQLException {
        long database = pointer;
        if (database == 0) throw new SQLException("The database has been closed");
        return database;
    }

    private static SQLFeatureNotSupportedException unsupported(String message) {
        return new SQLFeatureNotSupportedException(message, "0A000");
    }
}
