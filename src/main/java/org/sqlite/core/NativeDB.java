/*
 * Copyright (c) 2007 David Crawshaw <david@zentus.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 */
package org.sqlite.core;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import org.sqlite.BusyHandler;
import org.sqlite.Collation;
import org.sqlite.Function;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteConfig;
import org.sqlite.util.Logger;
import org.sqlite.util.LoggerFactory;

/** SQLite backend implemented directly with the JDK Foreign Function and Memory API. */
public final class NativeDB extends DB {
    private static final Logger logger = LoggerFactory.getLogger(NativeDB.class);
    private static final int DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS = 100;
    private static final int DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL = 3;
    private static final int DEFAULT_PAGES_PER_BACKUP_STEP = 100;

    /** SQLite connection handle. */
    private volatile long pointer;

    private final Arena callbackArena = Arena.ofShared();
    private final Map<String, MemorySegment> collationStubs = new HashMap<>();
    private final Map<String, Collation> collations = new HashMap<>();
    private final Map<String, FfmNative.FunctionRegistration> functions = new HashMap<>();
    private BusyHandler busyHandler;
    private MemorySegment busyHandlerStub = MemorySegment.NULL;
    private ProgressHandler progressHandler;
    private MemorySegment progressHandlerStub = MemorySegment.NULL;
    private MemorySegment updateHookStub = MemorySegment.NULL;
    private MemorySegment commitHookStub = MemorySegment.NULL;
    private MemorySegment rollbackHookStub = MemorySegment.NULL;

    public NativeDB(String url, String fileName, SQLiteConfig config) throws SQLException {
        super(url, fileName, config);
    }

    /** Initializes the FFM symbol table. */
    public static boolean load() {
        return FfmNative.initialize();
    }

    @Override
    protected synchronized void _open(String file, int openFlags) throws SQLException {
        pointer = FfmNative.open(stringToUtf8ByteArray(file), openFlags);
    }

    @Override
    protected synchronized void _close() throws SQLException {
        if (pointer == 0) return;
        FfmNative.setBusyHandler(pointer, MemorySegment.NULL);
        FfmNative.setProgressHandler(pointer, 0, MemorySegment.NULL);
        FfmNative.setUpdateHook(pointer, MemorySegment.NULL);
        FfmNative.setTransactionHooks(pointer, MemorySegment.NULL, MemorySegment.NULL);
        int result = FfmNative.close(pointer);
        if (result != SQLITE_OK) throw newSQLException(result, FfmNative.errorMessage(pointer));
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
        logger.trace(
                () ->
                        MessageFormat.format(
                                "DriverManager [{0}] [SQLite EXEC] {1}",
                                Thread.currentThread().getName(), sql));
        return FfmNative.execute(databasePointer(), stringToUtf8ByteArray(sql));
    }

    @Override
    public synchronized int shared_cache(boolean enable) throws SQLException {
        return FfmNative.enableSharedCache(enable);
    }

    @Override
    public synchronized int enable_load_extension(boolean enable) throws SQLException {
        return FfmNative.enableLoadExtension(databasePointer(), enable);
    }

    @Override
    public void interrupt() throws SQLException {
        long database = pointer;
        if (database != 0) FfmNative.interrupt(database);
    }

    @Override
    public synchronized void busy_timeout(int ms) throws SQLException {
        FfmNative.busyTimeout(databasePointer(), ms);
    }

    @Override
    public synchronized void busy_handler(BusyHandler handler) throws SQLException {
        MemorySegment callback =
                handler == null
                        ? MemorySegment.NULL
                        : FfmNative.busyCallbackStub(callbackArena, handler);
        int result = FfmNative.setBusyHandler(databasePointer(), callback);
        if (result != SQLITE_OK) throw newSQLException(result, errmsg());
        busyHandler = handler;
        busyHandlerStub = callback;
    }

    @Override
    protected synchronized SafeStmtPtr prepare(String sql) throws SQLException {
        logger.trace(
                () ->
                        MessageFormat.format(
                                "DriverManager [{0}] [SQLite EXEC] {1}",
                                Thread.currentThread().getName(), sql));
        return new SafeStmtPtr(
                this, FfmNative.prepare(databasePointer(), stringToUtf8ByteArray(sql)));
    }

    @Override
    synchronized String errmsg() {
        return pointer == 0 ? "SQLite database is closed" : FfmNative.errorMessage(pointer);
    }

    @Override
    public synchronized String libversion() {
        return FfmNative.libraryVersion();
    }

    @Override
    public synchronized long changes() throws SQLException {
        return FfmNative.changes(databasePointer());
    }

    @Override
    public synchronized long total_changes() throws SQLException {
        return FfmNative.totalChanges(databasePointer());
    }

    @Override
    protected synchronized int finalize(long stmt) throws SQLException {
        return FfmNative.finalizeStatement(stmt);
    }

    @Override
    public synchronized int step(long stmt) throws SQLException {
        return FfmNative.step(stmt);
    }

    @Override
    public synchronized int reset(long stmt) throws SQLException {
        return FfmNative.reset(stmt);
    }

    @Override
    public synchronized int clear_bindings(long stmt) throws SQLException {
        return FfmNative.clearBindings(stmt);
    }

    @Override
    synchronized int bind_parameter_count(long stmt) throws SQLException {
        return FfmNative.bindParameterCount(stmt);
    }

    @Override
    public synchronized int column_count(long stmt) throws SQLException {
        return FfmNative.columnCount(stmt);
    }

    @Override
    public synchronized int column_type(long stmt, int col) throws SQLException {
        return FfmNative.columnType(stmt, col);
    }

    @Override
    public synchronized String column_decltype(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(FfmNative.columnDeclaredType(stmt, col));
    }

    @Override
    public synchronized String column_table_name(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(FfmNative.columnTableName(stmt, col));
    }

    @Override
    public synchronized String column_name(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(FfmNative.columnName(stmt, col));
    }

    @Override
    public synchronized String column_text(long stmt, int col) throws SQLException {
        return utf8ByteBufferToString(FfmNative.columnText(stmt, col));
    }

    @Override
    public synchronized byte[] column_blob(long stmt, int col) throws SQLException {
        return FfmNative.columnBlob(stmt, col);
    }

    @Override
    public synchronized double column_double(long stmt, int col) throws SQLException {
        return FfmNative.columnDouble(stmt, col);
    }

    @Override
    public synchronized long column_long(long stmt, int col) throws SQLException {
        return FfmNative.columnLong(stmt, col);
    }

    @Override
    public synchronized int column_int(long stmt, int col) throws SQLException {
        return FfmNative.columnInt(stmt, col);
    }

    @Override
    synchronized int bind_null(long stmt, int pos) throws SQLException {
        return FfmNative.bindNull(stmt, pos);
    }

    @Override
    synchronized int bind_int(long stmt, int pos, int value) throws SQLException {
        return FfmNative.bindInt(stmt, pos, value);
    }

    @Override
    synchronized int bind_long(long stmt, int pos, long value) throws SQLException {
        return FfmNative.bindLong(stmt, pos, value);
    }

    @Override
    synchronized int bind_double(long stmt, int pos, double value) throws SQLException {
        return FfmNative.bindDouble(stmt, pos, value);
    }

    @Override
    synchronized int bind_text(long stmt, int pos, String value) throws SQLException {
        return FfmNative.bindText(stmt, pos, stringToUtf8ByteArray(value));
    }

    @Override
    synchronized int bind_blob(long stmt, int pos, byte[] value) throws SQLException {
        return FfmNative.bindBlob(stmt, pos, value);
    }

    @Override
    public synchronized void result_null(long context) throws SQLException {
        FfmNative.resultNull(context);
    }

    @Override
    public synchronized void result_text(long context, String value) throws SQLException {
        FfmNative.resultText(context, value);
    }

    @Override
    public synchronized void result_blob(long context, byte[] value) throws SQLException {
        FfmNative.resultBlob(context, value);
    }

    @Override
    public synchronized void result_double(long context, double value) throws SQLException {
        FfmNative.resultDouble(context, value);
    }

    @Override
    public synchronized void result_long(long context, long value) throws SQLException {
        FfmNative.resultLong(context, value);
    }

    @Override
    public synchronized void result_int(long context, int value) throws SQLException {
        FfmNative.resultInt(context, value);
    }

    @Override
    public synchronized void result_error(long context, String error) throws SQLException {
        FfmNative.resultError(context, error);
    }

    @Override
    public synchronized String value_text(Function function, int argument) throws SQLException {
        return FfmNative.valueText(function, argument);
    }

    @Override
    public synchronized byte[] value_blob(Function function, int argument) throws SQLException {
        return FfmNative.valueBlob(function, argument);
    }

    @Override
    public synchronized double value_double(Function function, int argument) throws SQLException {
        return FfmNative.valueDouble(function, argument);
    }

    @Override
    public synchronized long value_long(Function function, int argument) throws SQLException {
        return FfmNative.valueLong(function, argument);
    }

    @Override
    public synchronized int value_int(Function function, int argument) throws SQLException {
        return FfmNative.valueInt(function, argument);
    }

    @Override
    public synchronized int value_type(Function function, int argument) throws SQLException {
        return FfmNative.valueType(function, argument);
    }

    @Override
    public synchronized int create_function(String name, Function function, int nArgs, int flags)
            throws SQLException {
        FfmNative.FunctionRegistration registration =
                FfmNative.createFunction(
                        callbackArena, databasePointer(), name, function, nArgs, flags);
        functions.put(name, registration);
        return SQLITE_OK;
    }

    @Override
    public synchronized int destroy_function(String name) throws SQLException {
        int result = FfmNative.destroyFunction(databasePointer(), name);
        if (result == SQLITE_OK) functions.remove(name);
        return result;
    }

    @Override
    public synchronized int create_collation(String name, Collation collation) throws SQLException {
        MemorySegment callback = FfmNative.collationCallbackStub(callbackArena, collation);
        int result = FfmNative.setCollation(databasePointer(), name, callback);
        if (result == SQLITE_OK) {
            collations.put(name, collation);
            collationStubs.put(name, callback);
        }
        return result;
    }

    @Override
    public synchronized int destroy_collation(String name) throws SQLException {
        int result = FfmNative.setCollation(databasePointer(), name, MemorySegment.NULL);
        if (result == SQLITE_OK) {
            collations.remove(name);
            collationStubs.remove(name);
        }
        return result;
    }

    @Override
    public synchronized int limit(int id, int value) throws SQLException {
        return FfmNative.limit(databasePointer(), id, value);
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
        return FfmNative.copyDatabase(
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
        return FfmNative.copyDatabase(
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
    synchronized boolean[][] column_metadata(long stmt) throws SQLException {
        int columns = column_count(stmt);
        boolean[][] metadata = new boolean[columns][3];
        long database = databasePointer();
        for (int column = 0; column < columns; column++) {
            metadata[column] =
                    FfmNative.columnMetadata(
                            database, column_table_name(stmt, column), column_name(stmt, column));
        }
        return metadata;
    }

    @Override
    synchronized void set_commit_listener(boolean enabled) {
        try {
            if (enabled && commitHookStub.address() == 0) {
                commitHookStub = FfmNative.commitCallbackStub(callbackArena, this);
                rollbackHookStub = FfmNative.rollbackCallbackStub(callbackArena, this);
            }
            FfmNative.setTransactionHooks(
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
    synchronized void set_update_listener(boolean enabled) {
        try {
            if (enabled && updateHookStub.address() == 0) {
                updateHookStub = FfmNative.updateCallbackStub(callbackArena, this);
            }
            FfmNative.setUpdateHook(
                    databasePointer(), enabled ? updateHookStub : MemorySegment.NULL);
            if (!enabled) updateHookStub = MemorySegment.NULL;
        } catch (SQLException error) {
            throw new IllegalStateException("Could not configure update listeners", error);
        }
    }

    @Override
    public synchronized void register_progress_handler(int vmCalls, ProgressHandler handler)
            throws SQLException {
        if (handler == null) throw new SQLException("Progress handler cannot be null");
        MemorySegment callback = FfmNative.progressCallbackStub(callbackArena, handler);
        FfmNative.setProgressHandler(databasePointer(), vmCalls, callback);
        progressHandler = handler;
        progressHandlerStub = callback;
    }

    @Override
    public synchronized void clear_progress_handler() throws SQLException {
        FfmNative.setProgressHandler(databasePointer(), 0, MemorySegment.NULL);
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
        return FfmNative.serialize(databasePointer(), schema);
    }

    @Override
    public synchronized void deserialize(String schema, byte[] buffer) throws SQLException {
        FfmNative.deserialize(databasePointer(), schema, buffer);
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

    private long databasePointer() throws SQLException {
        long database = pointer;
        if (database == 0) throw new SQLException("The database has been closed");
        return database;
    }

    private static SQLFeatureNotSupportedException unsupported(String message) {
        return new SQLFeatureNotSupportedException(message, "0A000");
    }
}
