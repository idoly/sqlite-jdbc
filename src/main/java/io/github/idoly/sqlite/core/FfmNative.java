package io.github.idoly.sqlite.core;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.idoly.sqlite.BusyHandler;
import io.github.idoly.sqlite.Collation;
import io.github.idoly.sqlite.Function;
import io.github.idoly.sqlite.ProgressHandler;
import io.github.idoly.sqlite.util.LibraryLoaderUtil;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Direct JDK FFM binding to SQLite's public C ABI. */
final class FfmNative {
    private static final long MAX_C_STRING_BYTES = 1024L * 1024L;
    private static final MemorySegment SQLITE_TRANSIENT = MemorySegment.ofAddress(-1);
    private static final Arena LIBRARY_ARENA = Arena.global();
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOLS = loadSymbols();

    private static final MethodHandle OPEN =
            downcall(
                    "sqlite3_open_v2",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle CLOSE =
            downcall("sqlite3_close_v2", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle EXEC =
            downcall(
                    "sqlite3_exec",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle ENABLE_SHARED_CACHE =
            downcall("sqlite3_enable_shared_cache", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    private static final MethodHandle ENABLE_LOAD_EXTENSION =
            downcall(
                    "sqlite3_enable_load_extension",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle INTERRUPT =
            downcall("sqlite3_interrupt", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle BUSY_TIMEOUT =
            downcall("sqlite3_busy_timeout", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle PREPARE =
            downcall(
                    "sqlite3_prepare_v2",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle ERROR_MESSAGE =
            downcall("sqlite3_errmsg", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle LIBRARY_VERSION =
            downcall("sqlite3_libversion", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle CHANGES =
            downcall("sqlite3_changes64", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final MethodHandle TOTAL_CHANGES =
            downcall("sqlite3_total_changes64", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final MethodHandle FINALIZE =
            downcall("sqlite3_finalize", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle STEP =
            downcall("sqlite3_step", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle RESET =
            downcall("sqlite3_reset", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle CLEAR_BINDINGS =
            downcall("sqlite3_clear_bindings", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_PARAMETER_COUNT =
            downcall("sqlite3_bind_parameter_count", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle COLUMN_COUNT =
            downcall("sqlite3_column_count", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle COLUMN_TYPE =
            downcall("sqlite3_column_type", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_DECLTYPE =
            downcall("sqlite3_column_decltype", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final Optional<MethodHandle> COLUMN_TABLE_NAME =
            optionalDowncall(
                    "sqlite3_column_table_name", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_NAME =
            downcall("sqlite3_column_name", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_TEXT =
            downcall("sqlite3_column_text", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_BLOB =
            downcall("sqlite3_column_blob", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_BYTES =
            downcall("sqlite3_column_bytes", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_DOUBLE =
            downcall(
                    "sqlite3_column_double", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_LONG =
            downcall("sqlite3_column_int64", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_INT =
            downcall("sqlite3_column_int", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle BIND_NULL =
            downcall("sqlite3_bind_null", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle BIND_INT =
            downcall(
                    "sqlite3_bind_int",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle BIND_LONG =
            downcall(
                    "sqlite3_bind_int64",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    private static final MethodHandle BIND_DOUBLE =
            downcall(
                    "sqlite3_bind_double",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_DOUBLE));
    private static final MethodHandle BIND_TEXT =
            downcall(
                    "sqlite3_bind_text",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_BLOB =
            downcall(
                    "sqlite3_bind_blob",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle LIMIT =
            downcall("sqlite3_limit", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT));
    private static final MethodHandle ERROR_CODE =
            downcall("sqlite3_errcode", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BACKUP_INIT =
            downcall(
                    "sqlite3_backup_init",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle BACKUP_STEP =
            downcall("sqlite3_backup_step", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle BACKUP_FINISH =
            downcall("sqlite3_backup_finish", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BACKUP_REMAINING =
            downcall("sqlite3_backup_remaining", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BACKUP_PAGE_COUNT =
            downcall("sqlite3_backup_pagecount", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle SERIALIZE =
            downcall(
                    "sqlite3_serialize",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle DESERIALIZE =
            downcall(
                    "sqlite3_deserialize",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT));
    private static final MethodHandle MALLOC =
            downcall("sqlite3_malloc64", FunctionDescriptor.of(ADDRESS, JAVA_LONG));
    private static final MethodHandle FREE =
            downcall("sqlite3_free", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle TABLE_COLUMN_METADATA =
            downcall(
                    "sqlite3_table_column_metadata",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
                            ADDRESS, ADDRESS));
    private static final MethodHandle BUSY_HANDLER =
            downcall(
                    "sqlite3_busy_handler",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle PROGRESS_HANDLER =
            downcall(
                    "sqlite3_progress_handler",
                    FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CREATE_COLLATION =
            downcall(
                    "sqlite3_create_collation_v2",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle UPDATE_HOOK =
            downcall(
                    "sqlite3_update_hook",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle COMMIT_HOOK =
            downcall(
                    "sqlite3_commit_hook",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle ROLLBACK_HOOK =
            downcall(
                    "sqlite3_rollback_hook",
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle CREATE_FUNCTION =
            downcall(
                    "sqlite3_create_function_v2",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS,
                            ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle CREATE_WINDOW_FUNCTION =
            downcall(
                    "sqlite3_create_window_function",
                    FunctionDescriptor.of(
                            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS,
                            ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle AGGREGATE_CONTEXT =
            downcall(
                    "sqlite3_aggregate_context", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle RESULT_NULL =
            downcall("sqlite3_result_null", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle RESULT_TEXT =
            downcall(
                    "sqlite3_result_text",
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle RESULT_BLOB =
            downcall(
                    "sqlite3_result_blob",
                    FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle RESULT_DOUBLE =
            downcall("sqlite3_result_double", FunctionDescriptor.ofVoid(ADDRESS, JAVA_DOUBLE));
    private static final MethodHandle RESULT_LONG =
            downcall("sqlite3_result_int64", FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG));
    private static final MethodHandle RESULT_INT =
            downcall("sqlite3_result_int", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
    private static final MethodHandle RESULT_ERROR =
            downcall("sqlite3_result_error", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle VALUE_TEXT =
            downcall("sqlite3_value_text", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle VALUE_BLOB =
            downcall("sqlite3_value_blob", FunctionDescriptor.of(ADDRESS, ADDRESS));
    private static final MethodHandle VALUE_BYTES =
            downcall("sqlite3_value_bytes", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle VALUE_DOUBLE =
            downcall("sqlite3_value_double", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS));
    private static final MethodHandle VALUE_LONG =
            downcall("sqlite3_value_int64", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    private static final MethodHandle VALUE_INT =
            downcall("sqlite3_value_int", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle VALUE_TYPE =
            downcall("sqlite3_value_type", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BUSY_CALLBACK =
            callbackHandle(
                    "busyCallback",
                    MethodType.methodType(
                            int.class, BusyHandler.class, MemorySegment.class, int.class));
    private static final MethodHandle PROGRESS_CALLBACK =
            callbackHandle(
                    "progressCallback",
                    MethodType.methodType(int.class, ProgressHandler.class, MemorySegment.class));
    private static final MethodHandle COLLATION_CALLBACK =
            callbackHandle(
                    "collationCallback",
                    MethodType.methodType(
                            int.class,
                            Collation.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class));
    private static final MethodHandle UPDATE_CALLBACK =
            callbackHandle(
                    "updateCallback",
                    MethodType.methodType(
                            void.class,
                            NativeDB.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class,
                            MemorySegment.class,
                            long.class));
    private static final MethodHandle COMMIT_CALLBACK =
            callbackHandle(
                    "commitCallback",
                    MethodType.methodType(int.class, NativeDB.class, MemorySegment.class));
    private static final MethodHandle ROLLBACK_CALLBACK =
            callbackHandle(
                    "rollbackCallback",
                    MethodType.methodType(void.class, NativeDB.class, MemorySegment.class));
    private static final MethodHandle FUNCTION_CALLBACK =
            callbackHandle(
                    "functionCallback",
                    MethodType.methodType(
                            void.class,
                            FunctionRegistration.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class));
    private static final MethodHandle STEP_CALLBACK =
            callbackHandle(
                    "stepCallback",
                    MethodType.methodType(
                            void.class,
                            FunctionRegistration.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class));
    private static final MethodHandle FINAL_CALLBACK =
            callbackHandle(
                    "finalCallback",
                    MethodType.methodType(
                            void.class, FunctionRegistration.class, MemorySegment.class));
    private static final MethodHandle VALUE_CALLBACK =
            callbackHandle(
                    "valueCallback",
                    MethodType.methodType(
                            void.class, FunctionRegistration.class, MemorySegment.class));
    private static final MethodHandle INVERSE_CALLBACK =
            callbackHandle(
                    "inverseCallback",
                    MethodType.methodType(
                            void.class,
                            FunctionRegistration.class,
                            MemorySegment.class,
                            int.class,
                            MemorySegment.class));
    private static final MethodHandle EXTENDED_RESULT_CODES =
            downcall(
                    "sqlite3_extended_result_codes",
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

    private FfmNative() {}

    static boolean initialize() {
        return !libraryVersion().isBlank();
    }

    static long open(byte[] filename, int flags) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ADDRESS);
            int result =
                    (int)
                            OPEN.invokeExact(
                                    cString(arena, filename), out, flags, MemorySegment.NULL);
            long database = out.get(ADDRESS, 0).address();
            if (result != 0) {
                String message =
                        database == 0
                                ? "SQLite could not open the database"
                                : errorMessage(database);
                if (database != 0) {
                    int ignoredCloseResult = (int) CLOSE.invokeExact(address(database));
                }
                throw sqliteException(message, result, null);
            }
            int extendedResult = (int) EXTENDED_RESULT_CODES.invokeExact(address(database), 1);
            if (extendedResult != 0) {
                int ignoredCloseResult = (int) CLOSE.invokeExact(address(database));
                throw sqliteException(
                        "Could not enable SQLite extended result codes", extendedResult, null);
            }
            return database;
        } catch (Throwable error) {
            throw failure("open database", error);
        }
    }

    static int close(long database) throws SQLException {
        try {
            return (int) CLOSE.invokeExact(address(database));
        } catch (Throwable error) {
            throw failure("close database", error);
        }
    }

    static int execute(long database, byte[] sql) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            return (int)
                    EXEC.invokeExact(
                            address(database),
                            cString(arena, sql),
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("execute SQL", error);
        }
    }

    static int enableSharedCache(boolean enabled) throws SQLException {
        try {
            return (int) ENABLE_SHARED_CACHE.invokeExact(enabled ? 1 : 0);
        } catch (Throwable error) {
            throw failure("configure shared cache", error);
        }
    }

    static int enableLoadExtension(long database, boolean enabled) throws SQLException {
        try {
            return (int) ENABLE_LOAD_EXTENSION.invokeExact(address(database), enabled ? 1 : 0);
        } catch (Throwable error) {
            throw failure("configure extension loading", error);
        }
    }

    static void interrupt(long database) throws SQLException {
        try {
            INTERRUPT.invokeExact(address(database));
        } catch (Throwable error) {
            throw failure("interrupt database", error);
        }
    }

    static void busyTimeout(long database, int milliseconds) throws SQLException {
        try {
            int result = (int) BUSY_TIMEOUT.invokeExact(address(database), milliseconds);
            if (result != 0) throw sqliteException(errorMessage(database), result, null);
        } catch (Throwable error) {
            throw failure("configure busy timeout", error);
        }
    }

    static long prepare(long database, byte[] sql) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment statementOut = arena.allocate(ADDRESS);
            int result =
                    (int)
                            PREPARE.invokeExact(
                                    address(database),
                                    cString(arena, sql),
                                    sql.length,
                                    statementOut,
                                    MemorySegment.NULL);
            if (result != 0) throw sqliteException(errorMessage(database), result, null);
            return statementOut.get(ADDRESS, 0).address();
        } catch (Throwable error) {
            throw failure("prepare SQL", error);
        }
    }

    static String errorMessage(long database) {
        try {
            return readCString((MemorySegment) ERROR_MESSAGE.invokeExact(address(database)));
        } catch (Throwable error) {
            throw uncheckedFailure(error);
        }
    }

    static String libraryVersion() {
        try {
            return readCString((MemorySegment) LIBRARY_VERSION.invokeExact());
        } catch (Throwable error) {
            throw uncheckedFailure(error);
        }
    }

    static long changes(long database) throws SQLException {
        try {
            return (long) CHANGES.invokeExact(address(database));
        } catch (Throwable error) {
            throw failure("read change count", error);
        }
    }

    static long totalChanges(long database) throws SQLException {
        try {
            return (long) TOTAL_CHANGES.invokeExact(address(database));
        } catch (Throwable error) {
            throw failure("read total change count", error);
        }
    }

    static int finalizeStatement(long statement) throws SQLException {
        return invokeAddressInt(FINALIZE, statement, "finalize statement");
    }

    static int step(long statement) throws SQLException {
        return invokeAddressInt(STEP, statement, "step statement");
    }

    static int reset(long statement) throws SQLException {
        return invokeAddressInt(RESET, statement, "reset statement");
    }

    static int clearBindings(long statement) throws SQLException {
        return invokeAddressInt(CLEAR_BINDINGS, statement, "clear statement bindings");
    }

    static int bindParameterCount(long statement) throws SQLException {
        return invokeAddressInt(BIND_PARAMETER_COUNT, statement, "read bind parameter count");
    }

    static int columnCount(long statement) throws SQLException {
        return invokeAddressInt(COLUMN_COUNT, statement, "read column count");
    }

    static int columnType(long statement, int column) throws SQLException {
        return invokeAddressInt(COLUMN_TYPE, statement, column, "read column type");
    }

    static ByteBuffer columnDeclaredType(long statement, int column) throws SQLException {
        return pointerString(COLUMN_DECLTYPE, statement, column, "read declared column type");
    }

    static ByteBuffer columnTableName(long statement, int column) throws SQLException {
        if (COLUMN_TABLE_NAME.isEmpty()) return null;
        return pointerString(COLUMN_TABLE_NAME.get(), statement, column, "read column table name");
    }

    static ByteBuffer columnName(long statement, int column) throws SQLException {
        return pointerString(COLUMN_NAME, statement, column, "read column name");
    }

    static ByteBuffer columnText(long statement, int column) throws SQLException {
        try {
            MemorySegment value =
                    (MemorySegment) COLUMN_TEXT.invokeExact(address(statement), column);
            if (value.address() == 0) return null;
            int bytes = (int) COLUMN_BYTES.invokeExact(address(statement), column);
            return ByteBuffer.wrap(value.reinterpret(bytes).toArray(JAVA_BYTE));
        } catch (Throwable error) {
            throw failure("read text column", error);
        }
    }

    static byte[] columnBlob(long statement, int column) throws SQLException {
        try {
            MemorySegment value =
                    (MemorySegment) COLUMN_BLOB.invokeExact(address(statement), column);
            int bytes = (int) COLUMN_BYTES.invokeExact(address(statement), column);
            if (value.address() == 0) {
                int type = (int) COLUMN_TYPE.invokeExact(address(statement), column);
                return type == 5 ? null : new byte[0];
            }
            if (bytes == 0) return new byte[0];
            return value.reinterpret(bytes).toArray(JAVA_BYTE);
        } catch (Throwable error) {
            throw failure("read blob column", error);
        }
    }

    static double columnDouble(long statement, int column) throws SQLException {
        try {
            return (double) COLUMN_DOUBLE.invokeExact(address(statement), column);
        } catch (Throwable error) {
            throw failure("read double column", error);
        }
    }

    static long columnLong(long statement, int column) throws SQLException {
        try {
            return (long) COLUMN_LONG.invokeExact(address(statement), column);
        } catch (Throwable error) {
            throw failure("read long column", error);
        }
    }

    static int columnInt(long statement, int column) throws SQLException {
        return invokeAddressInt(COLUMN_INT, statement, column, "read integer column");
    }

    static int bindNull(long statement, int position) throws SQLException {
        return invokeAddressInt(BIND_NULL, statement, position, "bind null");
    }

    static int bindInt(long statement, int position, int value) throws SQLException {
        try {
            return (int) BIND_INT.invokeExact(address(statement), position, value);
        } catch (Throwable error) {
            throw failure("bind integer", error);
        }
    }

    static int bindLong(long statement, int position, long value) throws SQLException {
        try {
            return (int) BIND_LONG.invokeExact(address(statement), position, value);
        } catch (Throwable error) {
            throw failure("bind long", error);
        }
    }

    static int bindDouble(long statement, int position, double value) throws SQLException {
        try {
            return (int) BIND_DOUBLE.invokeExact(address(statement), position, value);
        } catch (Throwable error) {
            throw failure("bind double", error);
        }
    }

    static int bindText(long statement, int position, byte[] value) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = bytes(arena, value);
            return (int)
                    BIND_TEXT.invokeExact(
                            address(statement), position, bytes, value.length, SQLITE_TRANSIENT);
        } catch (Throwable error) {
            throw failure("bind text", error);
        }
    }

    static int bindBlob(long statement, int position, byte[] value) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = bytes(arena, value);
            return (int)
                    BIND_BLOB.invokeExact(
                            address(statement), position, bytes, value.length, SQLITE_TRANSIENT);
        } catch (Throwable error) {
            throw failure("bind blob", error);
        }
    }

    static int limit(long database, int id, int value) throws SQLException {
        try {
            return (int) LIMIT.invokeExact(address(database), id, value);
        } catch (Throwable error) {
            throw failure("configure SQLite limit", error);
        }
    }

    static boolean[] columnMetadata(long database, String table, String column)
            throws SQLException {
        if (table == null || column == null) return new boolean[3];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment notNull = arena.allocate(JAVA_INT);
            MemorySegment primaryKey = arena.allocate(JAVA_INT);
            MemorySegment autoIncrement = arena.allocate(JAVA_INT);
            int result =
                    (int)
                            TABLE_COLUMN_METADATA.invokeExact(
                                    address(database),
                                    MemorySegment.NULL,
                                    arena.allocateFrom(table),
                                    arena.allocateFrom(column),
                                    MemorySegment.NULL,
                                    MemorySegment.NULL,
                                    notNull,
                                    primaryKey,
                                    autoIncrement);
            if (result != 0) return new boolean[3];
            return new boolean[] {
                notNull.get(JAVA_INT, 0) != 0,
                primaryKey.get(JAVA_INT, 0) != 0,
                autoIncrement.get(JAVA_INT, 0) != 0
            };
        } catch (Throwable error) {
            throw failure("read table column metadata", error);
        }
    }

    static int copyDatabase(
            long database,
            String schema,
            String filename,
            boolean restore,
            DB.ProgressObserver observer,
            int sleepTimeMillis,
            int timeoutLimit,
            int pagesPerStep)
            throws SQLException {
        long fileDatabase =
                open(
                        filename.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        (restore ? 1 : 2 | 4) | (filename.startsWith("file:") ? 64 : 0));
        long backup = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment main = arena.allocateFrom("main");
            MemorySegment selectedSchema = arena.allocateFrom(schema);
            MemorySegment destinationDatabase = restore ? address(database) : address(fileDatabase);
            MemorySegment destinationSchema = restore ? selectedSchema : main;
            MemorySegment sourceDatabase = restore ? address(fileDatabase) : address(database);
            MemorySegment sourceSchema = restore ? main : selectedSchema;
            MemorySegment backupSegment =
                    (MemorySegment)
                            BACKUP_INIT.invokeExact(
                                    destinationDatabase,
                                    destinationSchema,
                                    sourceDatabase,
                                    sourceSchema);
            backup = backupSegment.address();
            if (backup == 0) {
                return (int) ERROR_CODE.invokeExact(address(restore ? database : fileDatabase));
            }

            int result;
            int timeouts = 0;
            do {
                result = (int) BACKUP_STEP.invokeExact(address(backup), pagesPerStep);
                if ((result == 0 || result == 101) && observer != null) {
                    int remaining = (int) BACKUP_REMAINING.invokeExact(address(backup));
                    int pageCount = (int) BACKUP_PAGE_COUNT.invokeExact(address(backup));
                    observer.progress(remaining, pageCount);
                }
                if (result == 5 || result == 6) {
                    if (timeouts++ >= timeoutLimit) break;
                    try {
                        Thread.sleep(sleepTimeMillis);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return 9;
                    }
                }
            } while (result == 0 || result == 5 || result == 6);

            int finishResult = (int) BACKUP_FINISH.invokeExact(address(backup));
            backup = 0;
            if (result != 101) return result;
            return finishResult;
        } catch (Throwable error) {
            throw failure(restore ? "restore database" : "backup database", error);
        } finally {
            if (backup != 0) {
                try {
                    int ignoredFinishResult = (int) BACKUP_FINISH.invokeExact(address(backup));
                } catch (Throwable ignored) {
                    // Preserve the operation failure.
                }
            }
            int closeResult = close(fileDatabase);
            if (closeResult != 0) {
                throw DB.newSQLException(closeResult, "Could not close backup database");
            }
        }
    }

    static MemorySegment busyCallbackStub(Arena arena, BusyHandler handler) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(BUSY_CALLBACK, 0, handler),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT),
                arena);
    }

    static int setBusyHandler(long database, MemorySegment callback) throws SQLException {
        try {
            return (int) BUSY_HANDLER.invokeExact(address(database), callback, MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("configure busy handler", error);
        }
    }

    static MemorySegment progressCallbackStub(Arena arena, ProgressHandler handler) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(PROGRESS_CALLBACK, 0, handler),
                FunctionDescriptor.of(JAVA_INT, ADDRESS),
                arena);
    }

    static void setProgressHandler(long database, int vmCalls, MemorySegment callback)
            throws SQLException {
        try {
            PROGRESS_HANDLER.invokeExact(address(database), vmCalls, callback, MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("configure progress handler", error);
        }
    }

    static MemorySegment collationCallbackStub(Arena arena, Collation collation) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(COLLATION_CALLBACK, 0, collation),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS),
                arena);
    }

    static int setCollation(long database, String name, MemorySegment callback)
            throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            return (int)
                    CREATE_COLLATION.invokeExact(
                            address(database),
                            arena.allocateFrom(name),
                            1,
                            MemorySegment.NULL,
                            callback,
                            MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("configure collation", error);
        }
    }

    static MemorySegment updateCallbackStub(Arena arena, NativeDB database) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(UPDATE_CALLBACK, 0, database),
                FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG),
                arena);
    }

    static void setUpdateHook(long database, MemorySegment callback) throws SQLException {
        try {
            MemorySegment ignoredPrevious =
                    (MemorySegment)
                            UPDATE_HOOK.invokeExact(
                                    address(database), callback, MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("configure update hook", error);
        }
    }

    static MemorySegment commitCallbackStub(Arena arena, NativeDB database) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(COMMIT_CALLBACK, 0, database),
                FunctionDescriptor.of(JAVA_INT, ADDRESS),
                arena);
    }

    static MemorySegment rollbackCallbackStub(Arena arena, NativeDB database) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(ROLLBACK_CALLBACK, 0, database),
                FunctionDescriptor.ofVoid(ADDRESS),
                arena);
    }

    static void setTransactionHooks(
            long database, MemorySegment commitCallback, MemorySegment rollbackCallback)
            throws SQLException {
        try {
            MemorySegment ignoredCommit =
                    (MemorySegment)
                            COMMIT_HOOK.invokeExact(
                                    address(database), commitCallback, MemorySegment.NULL);
            MemorySegment ignoredRollback =
                    (MemorySegment)
                            ROLLBACK_HOOK.invokeExact(
                                    address(database), rollbackCallback, MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("configure transaction hooks", error);
        }
    }

    static FunctionRegistration createFunction(
            Arena arena,
            long database,
            String name,
            Function function,
            int argumentCount,
            int flags)
            throws SQLException {
        FunctionRegistration registration = new FunctionRegistration(function);
        try (Arena strings = Arena.ofConfined()) {
            int encoding = 1 | flags;
            int result;
            if (function instanceof Function.Window) {
                registration.step =
                        callbackStub(arena, STEP_CALLBACK, registration, functionArgsDescriptor());
                registration.finish =
                        callbackStub(arena, FINAL_CALLBACK, registration, contextDescriptor());
                registration.value =
                        callbackStub(arena, VALUE_CALLBACK, registration, contextDescriptor());
                registration.inverse =
                        callbackStub(
                                arena, INVERSE_CALLBACK, registration, functionArgsDescriptor());
                result =
                        (int)
                                CREATE_WINDOW_FUNCTION.invokeExact(
                                        address(database),
                                        strings.allocateFrom(name),
                                        argumentCount,
                                        encoding,
                                        MemorySegment.NULL,
                                        registration.step,
                                        registration.finish,
                                        registration.value,
                                        registration.inverse,
                                        MemorySegment.NULL);
            } else if (function instanceof Function.Aggregate) {
                registration.step =
                        callbackStub(arena, STEP_CALLBACK, registration, functionArgsDescriptor());
                registration.finish =
                        callbackStub(arena, FINAL_CALLBACK, registration, contextDescriptor());
                result =
                        (int)
                                CREATE_FUNCTION.invokeExact(
                                        address(database),
                                        strings.allocateFrom(name),
                                        argumentCount,
                                        encoding,
                                        MemorySegment.NULL,
                                        MemorySegment.NULL,
                                        registration.step,
                                        registration.finish,
                                        MemorySegment.NULL);
            } else {
                registration.scalar =
                        callbackStub(
                                arena, FUNCTION_CALLBACK, registration, functionArgsDescriptor());
                result =
                        (int)
                                CREATE_FUNCTION.invokeExact(
                                        address(database),
                                        strings.allocateFrom(name),
                                        argumentCount,
                                        encoding,
                                        MemorySegment.NULL,
                                        registration.scalar,
                                        MemorySegment.NULL,
                                        MemorySegment.NULL,
                                        MemorySegment.NULL);
            }
            if (result != 0) throw DB.newSQLException(result, errorMessage(database));
            return registration;
        } catch (Throwable error) {
            throw failure("create SQLite function", error);
        }
    }

    static int destroyFunction(long database, String name) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            return (int)
                    CREATE_FUNCTION.invokeExact(
                            address(database),
                            arena.allocateFrom(name),
                            -1,
                            1,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL,
                            MemorySegment.NULL);
        } catch (Throwable error) {
            throw failure("destroy SQLite function", error);
        }
    }

    static void resultNull(long context) throws SQLException {
        invokeVoid(RESULT_NULL, context, "return null function result");
    }

    static void resultText(long context, String value) throws SQLException {
        if (value == null) {
            resultNull(context);
            return;
        }
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            RESULT_TEXT.invokeExact(
                    address(context), bytes(arena, utf8), utf8.length, SQLITE_TRANSIENT);
        } catch (Throwable error) {
            throw failure("return text function result", error);
        }
    }

    static void resultBlob(long context, byte[] value) throws SQLException {
        if (value == null) {
            resultNull(context);
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            RESULT_BLOB.invokeExact(
                    address(context), bytes(arena, value), value.length, SQLITE_TRANSIENT);
        } catch (Throwable error) {
            throw failure("return blob function result", error);
        }
    }

    static void resultDouble(long context, double value) throws SQLException {
        try {
            RESULT_DOUBLE.invokeExact(address(context), value);
        } catch (Throwable error) {
            throw failure("return double function result", error);
        }
    }

    static void resultLong(long context, long value) throws SQLException {
        try {
            RESULT_LONG.invokeExact(address(context), value);
        } catch (Throwable error) {
            throw failure("return long function result", error);
        }
    }

    static void resultInt(long context, int value) throws SQLException {
        try {
            RESULT_INT.invokeExact(address(context), value);
        } catch (Throwable error) {
            throw failure("return integer function result", error);
        }
    }

    static void resultError(long context, String message) throws SQLException {
        byte[] utf8 = message.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            RESULT_ERROR.invokeExact(address(context), bytes(arena, utf8), utf8.length);
        } catch (Throwable error) {
            throw failure("return function error", error);
        }
    }

    static String valueText(Function function, int argument) throws SQLException {
        try {
            MemorySegment value = functionArgument(function, argument);
            MemorySegment text = (MemorySegment) VALUE_TEXT.invokeExact(value);
            if (text.address() == 0) return null;
            int length = (int) VALUE_BYTES.invokeExact(value);
            return new String(text.reinterpret(length).toArray(JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            throw failure("read text function argument", error);
        }
    }

    static byte[] valueBlob(Function function, int argument) throws SQLException {
        try {
            MemorySegment value = functionArgument(function, argument);
            MemorySegment blob = (MemorySegment) VALUE_BLOB.invokeExact(value);
            int length = (int) VALUE_BYTES.invokeExact(value);
            if (blob.address() == 0) return null;
            return blob.reinterpret(length).toArray(JAVA_BYTE);
        } catch (Throwable error) {
            throw failure("read blob function argument", error);
        }
    }

    static double valueDouble(Function function, int argument) throws SQLException {
        try {
            return (double) VALUE_DOUBLE.invokeExact(functionArgument(function, argument));
        } catch (Throwable error) {
            throw failure("read double function argument", error);
        }
    }

    static long valueLong(Function function, int argument) throws SQLException {
        try {
            return (long) VALUE_LONG.invokeExact(functionArgument(function, argument));
        } catch (Throwable error) {
            throw failure("read long function argument", error);
        }
    }

    static int valueInt(Function function, int argument) throws SQLException {
        try {
            return (int) VALUE_INT.invokeExact(functionArgument(function, argument));
        } catch (Throwable error) {
            throw failure("read integer function argument", error);
        }
    }

    static int valueType(Function function, int argument) throws SQLException {
        try {
            return (int) VALUE_TYPE.invokeExact(functionArgument(function, argument));
        } catch (Throwable error) {
            throw failure("read function argument type", error);
        }
    }

    static byte[] serialize(long database, String schema) throws SQLException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sizeOut = arena.allocate(JAVA_LONG);
            MemorySegment data =
                    (MemorySegment)
                            SERIALIZE.invokeExact(
                                    address(database), arena.allocateFrom(schema), sizeOut, 0);
            if (data.address() == 0) {
                int result = (int) ERROR_CODE.invokeExact(address(database));
                throw DB.newSQLException(result == 0 ? 7 : result, "Serialization failed");
            }
            long size = sizeOut.get(JAVA_LONG, 0);
            try {
                return data.reinterpret(size).toArray(JAVA_BYTE);
            } finally {
                FREE.invokeExact(data);
            }
        } catch (Throwable error) {
            throw failure("serialize database", error);
        }
    }

    static void deserialize(long database, String schema, byte[] buffer) throws SQLException {
        MemorySegment nativeBuffer = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            nativeBuffer = (MemorySegment) MALLOC.invokeExact((long) buffer.length);
            if (nativeBuffer.address() == 0)
                throw new SQLException("Failed to allocate native database buffer");
            if (buffer.length != 0) {
                nativeBuffer.reinterpret(buffer.length).copyFrom(MemorySegment.ofArray(buffer));
            }
            int result =
                    (int)
                            DESERIALIZE.invokeExact(
                                    address(database),
                                    arena.allocateFrom(schema),
                                    nativeBuffer,
                                    (long) buffer.length,
                                    (long) buffer.length,
                                    3);
            // SQLITE_DESERIALIZE_FREEONCLOSE transfers ownership even when SQLite rejects the
            // image.
            nativeBuffer = MemorySegment.NULL;
            if (result != 0) {
                throw DB.newSQLException(result, errorMessage(database));
            }
        } catch (Throwable error) {
            if (nativeBuffer.address() != 0) {
                try {
                    FREE.invokeExact(nativeBuffer);
                } catch (Throwable ignored) {
                    // Preserve the operation failure.
                }
            }
            throw failure("deserialize database", error);
        }
    }

    private static MemorySegment callbackStub(
            Arena arena, MethodHandle callback, Object receiver, FunctionDescriptor descriptor) {
        return LINKER.upcallStub(
                MethodHandles.insertArguments(callback, 0, receiver), descriptor, arena);
    }

    private static FunctionDescriptor functionArgsDescriptor() {
        return FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);
    }

    private static FunctionDescriptor contextDescriptor() {
        return FunctionDescriptor.ofVoid(ADDRESS);
    }

    private static MemorySegment functionArgument(Function function, int argument) {
        MemorySegment values =
                address(function.argumentValuesAddress())
                        .reinterpret((argument + 1L) * ADDRESS.byteSize());
        return values.get(ADDRESS, argument * ADDRESS.byteSize());
    }

    private static void invokeVoid(MethodHandle method, long pointer, String action)
            throws SQLException {
        try {
            method.invokeExact(address(pointer));
        } catch (Throwable error) {
            throw failure(action, error);
        }
    }

    private static int busyCallback(
            BusyHandler handler, MemorySegment ignored, int previousInvocations) {
        try {
            return handler.invokeCallback(previousInvocations);
        } catch (Throwable ignoredFailure) {
            return 0;
        }
    }

    private static int progressCallback(ProgressHandler handler, MemorySegment ignored) {
        try {
            return handler.invokeProgress();
        } catch (Throwable ignoredFailure) {
            return 1;
        }
    }

    private static int collationCallback(
            Collation collation,
            MemorySegment ignored,
            int firstLength,
            MemorySegment first,
            int secondLength,
            MemorySegment second) {
        try {
            String firstValue =
                    new String(
                            first.reinterpret(firstLength).toArray(JAVA_BYTE),
                            StandardCharsets.UTF_8);
            String secondValue =
                    new String(
                            second.reinterpret(secondLength).toArray(JAVA_BYTE),
                            StandardCharsets.UTF_8);
            return collation.invokeCompare(firstValue, secondValue);
        } catch (Throwable ignoredFailure) {
            return 0;
        }
    }

    private static void updateCallback(
            NativeDB database,
            MemorySegment ignored,
            int type,
            MemorySegment databaseName,
            MemorySegment tableName,
            long rowId) {
        database.onUpdate(type, readCString(databaseName), readCString(tableName), rowId);
    }

    private static int commitCallback(NativeDB database, MemorySegment ignored) {
        try {
            database.onCommit(true);
            return 0;
        } catch (Throwable failure) {
            return 1;
        }
    }

    private static void rollbackCallback(NativeDB database, MemorySegment ignored) {
        database.onCommit(false);
    }

    private static void functionCallback(
            FunctionRegistration registration,
            MemorySegment context,
            int arguments,
            MemorySegment values) {
        try {
            registration.template.invokeFunction(context.address(), values.address(), arguments);
        } catch (Throwable failure) {
            callbackError(context, failure);
        }
    }

    private static void stepCallback(
            FunctionRegistration registration,
            MemorySegment context,
            int arguments,
            MemorySegment values) {
        try {
            Function.Aggregate aggregate = registration.aggregate(context, true);
            aggregate.invokeStep(context.address(), values.address(), arguments);
        } catch (Throwable failure) {
            callbackError(context, failure);
        }
    }

    private static void inverseCallback(
            FunctionRegistration registration,
            MemorySegment context,
            int arguments,
            MemorySegment values) {
        try {
            Function.Window window = (Function.Window) registration.aggregate(context, false);
            if (window != null)
                window.invokeInverse(context.address(), values.address(), arguments);
        } catch (Throwable failure) {
            callbackError(context, failure);
        }
    }

    private static void valueCallback(FunctionRegistration registration, MemorySegment context) {
        try {
            Function.Window window = (Function.Window) registration.aggregate(context, false);
            if (window != null) window.invokeValue(context.address());
        } catch (Throwable failure) {
            callbackError(context, failure);
        }
    }

    private static void finalCallback(FunctionRegistration registration, MemorySegment context) {
        try {
            Function.Aggregate aggregate = registration.removeAggregate(context);
            if (aggregate != null) aggregate.invokeFinal(context.address());
        } catch (Throwable failure) {
            callbackError(context, failure);
        }
    }

    private static void callbackError(MemorySegment context, Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getName();
        try {
            resultError(context.address(), message);
        } catch (SQLException ignored) {
            // Exceptions cannot cross an upcall boundary.
        }
    }

    static final class FunctionRegistration {
        final Function template;
        final Map<Long, Function.Aggregate> aggregates = new ConcurrentHashMap<>();
        MemorySegment scalar = MemorySegment.NULL;
        MemorySegment step = MemorySegment.NULL;
        MemorySegment finish = MemorySegment.NULL;
        MemorySegment value = MemorySegment.NULL;
        MemorySegment inverse = MemorySegment.NULL;

        FunctionRegistration(Function template) {
            this.template = template;
        }

        Function.Aggregate aggregate(MemorySegment context, boolean create) throws Throwable {
            long key = aggregateKey(context, create);
            if (key == 0) return null;
            if (!create) return aggregates.get(key);
            return aggregates.computeIfAbsent(key, ignored -> cloneAggregate());
        }

        Function.Aggregate removeAggregate(MemorySegment context) throws Throwable {
            long key = aggregateKey(context, false);
            return key == 0 ? null : aggregates.remove(key);
        }

        private long aggregateKey(MemorySegment context, boolean create) throws Throwable {
            MemorySegment key =
                    (MemorySegment) AGGREGATE_CONTEXT.invokeExact(context, create ? 1 : 0);
            return key.address();
        }

        private Function.Aggregate cloneAggregate() {
            try {
                return (Function.Aggregate) ((Function.Aggregate) template).clone();
            } catch (CloneNotSupportedException error) {
                throw new IllegalStateException("Could not clone aggregate function", error);
            }
        }
    }

    private static MethodHandle callbackHandle(String name, MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(FfmNative.class, name, type);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static ByteBuffer pointerString(
            MethodHandle method, long statement, int column, String action) throws SQLException {
        try {
            String value =
                    readCString((MemorySegment) method.invokeExact(address(statement), column));
            return value == null
                    ? null
                    : ByteBuffer.wrap(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable error) {
            throw failure(action, error);
        }
    }

    private static int invokeAddressInt(MethodHandle method, long pointer, String action)
            throws SQLException {
        try {
            return (int) method.invokeExact(address(pointer));
        } catch (Throwable error) {
            throw failure(action, error);
        }
    }

    private static int invokeAddressInt(MethodHandle method, long pointer, int value, String action)
            throws SQLException {
        try {
            return (int) method.invokeExact(address(pointer), value);
        } catch (Throwable error) {
            throw failure(action, error);
        }
    }

    private static SymbolLookup loadSymbols() {
        String configuredPath = System.getProperty("io.github.idoly.sqlite.ffm.lib.path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return SymbolLookup.libraryLookup(
                    Path.of(configuredPath).toAbsolutePath(), LIBRARY_ARENA);
        }

        Optional<Path> packagedLibrary = extractPackagedLibrary();
        return packagedLibrary
                .<SymbolLookup>map(path -> SymbolLookup.libraryLookup(path, LIBRARY_ARENA))
                .orElseGet(() -> SymbolLookup.libraryLookup(systemLibraryName(), LIBRARY_ARENA));
    }

    private static Optional<Path> extractPackagedLibrary() {
        String libraryName = LibraryLoaderUtil.getNativeLibName();
        String resource = LibraryLoaderUtil.getNativeLibResourcePath() + "/" + libraryName;
        try (InputStream input = FfmNative.class.getResourceAsStream(resource)) {
            if (input == null) return Optional.empty();
            Path directory = Files.createTempDirectory("sqlite-jdbc-ffm-");
            Path library = directory.resolve(libraryName);
            Files.copy(input, library, StandardCopyOption.REPLACE_EXISTING);
            library.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            return Optional.of(library.toAbsolutePath());
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not extract packaged SQLite library " + resource, error);
        }
    }

    private static String systemLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "sqlite3";
        if (os.contains("mac") || os.contains("darwin")) return "libsqlite3.dylib";
        return "libsqlite3.so.0";
    }

    private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(SYMBOLS.findOrThrow(symbol), descriptor);
    }

    private static Optional<MethodHandle> optionalDowncall(
            String symbol, FunctionDescriptor descriptor) {
        return SYMBOLS.find(symbol).map(address -> LINKER.downcallHandle(address, descriptor));
    }

    private static MemorySegment cString(Arena arena, byte[] value) {
        MemorySegment result = arena.allocate(value.length + 1L, 1);
        if (value.length != 0)
            result.asSlice(0, value.length).copyFrom(MemorySegment.ofArray(value));
        return result;
    }

    private static MemorySegment bytes(Arena arena, byte[] value) {
        MemorySegment result = arena.allocate(Math.max(1, value.length), 1);
        if (value.length != 0)
            result.asSlice(0, value.length).copyFrom(MemorySegment.ofArray(value));
        return result;
    }

    private static MemorySegment address(long value) {
        return value == 0 ? MemorySegment.NULL : MemorySegment.ofAddress(value);
    }

    private static String readCString(MemorySegment value) {
        return value.address() == 0 ? null : value.reinterpret(MAX_C_STRING_BYTES).getString(0);
    }

    private static SQLException failure(String action, Throwable error) {
        if (error instanceof SQLException sqlException) return sqlException;
        if (error instanceof Error fatal) throw fatal;
        return new SQLException("Could not " + action + " through SQLite FFM", error);
    }

    private static SQLException sqliteException(String message, int resultCode, Throwable cause) {
        SQLException exception = DB.newSQLException(resultCode, message);
        if (cause != null) exception.initCause(cause);
        return exception;
    }

    private static RuntimeException uncheckedFailure(Throwable error) {
        if (error instanceof RuntimeException runtimeException) return runtimeException;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException("SQLite FFM invocation failed", error);
    }
}
