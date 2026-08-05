package io.github.idoly.sqlite.ffm;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/** SQLite binding implemented directly with the JDK 25 Foreign Function and Memory API. */
final class FfmSQLiteNative implements SQLiteNative {
    private static final long MAX_C_STRING_BYTES = 1024L * 1024L;
    private static final MemorySegment SQLITE_TRANSIENT = MemorySegment.ofAddress(-1L);
    private static final Arena LIBRARY_ARENA = Arena.global();
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOLS = loadSymbols();

    // Keep one strongly typed handle per ABI symbol; invokeExact catches descriptor drift in tests.
    private static final MethodHandle LIBVERSION = downcall("sqlitejdbc_libversion", FunctionDescriptor.of(ADDRESS));
    private static final MethodHandle OPEN = downcall(
            "sqlitejdbc_open_v2", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle CLOSE = downcall(
            "sqlitejdbc_close_v2", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle EXEC = downcall(
            "sqlitejdbc_exec", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle GET_AUTOCOMMIT = downcall(
            "sqlitejdbc_get_autocommit", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BUSY_TIMEOUT = downcall(
            "sqlitejdbc_busy_timeout", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle INTERRUPT = downcall(
            "sqlitejdbc_interrupt", FunctionDescriptor.ofVoid(ADDRESS));
    private static final MethodHandle TOTAL_CHANGES = downcall(
            "sqlitejdbc_total_changes", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle PREPARE = downcall(
            "sqlitejdbc_prepare_v3",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle STEP = downcall(
            "sqlitejdbc_step", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle RESET = downcall(
            "sqlitejdbc_reset", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle CLEAR_BINDINGS = downcall(
            "sqlitejdbc_clear_bindings", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_PARAMETER_COUNT = downcall(
            "sqlitejdbc_bind_parameter_count", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_NULL = downcall(
            "sqlitejdbc_bind_null", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle BIND_LONG = downcall(
            "sqlitejdbc_bind_int64", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    private static final MethodHandle BIND_DOUBLE = downcall(
            "sqlitejdbc_bind_double", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_DOUBLE));
    private static final MethodHandle BIND_TEXT = downcall(
            "sqlitejdbc_bind_text", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle BIND_BLOB = downcall(
            "sqlitejdbc_bind_blob", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle COLUMN_COUNT = downcall(
            "sqlitejdbc_column_count", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle COLUMN_TYPE = downcall(
            "sqlitejdbc_column_type", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_LONG = downcall(
            "sqlitejdbc_column_int64", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_DOUBLE = downcall(
            "sqlitejdbc_column_double", FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_TEXT = downcall(
            "sqlitejdbc_column_text", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_BLOB = downcall(
            "sqlitejdbc_column_blob", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_BYTES = downcall(
            "sqlitejdbc_column_bytes", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_NAME = downcall(
            "sqlitejdbc_column_name", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle COLUMN_DECLTYPE = downcall(
            "sqlitejdbc_column_decltype", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    private static final MethodHandle FINALIZE = downcall(
            "sqlitejdbc_finalize", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle ERRMSG = downcall(
            "sqlitejdbc_errmsg", FunctionDescriptor.of(ADDRESS, ADDRESS));

    @Override
    public String libraryVersion() {
        try {
            return readCString((MemorySegment) LIBVERSION.invokeExact());
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public OpenResult open(String filename, int openFlags) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment databaseOut = arena.allocate(ADDRESS);
            int resultCode = (int) OPEN.invokeExact(
                    arena.allocateFrom(filename), databaseOut, openFlags, MemorySegment.NULL);
            return new OpenResult(resultCode, new DatabaseHandle(databaseOut.get(ADDRESS, 0).address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int close(DatabaseHandle database) {
        try {
            return (int) CLOSE.invokeExact(address(database.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int execute(DatabaseHandle database, String sql) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) EXEC.invokeExact(
                    address(database.address()),
                    arena.allocateFrom(sql),
                    MemorySegment.NULL,
                    MemorySegment.NULL,
                    MemorySegment.NULL);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public boolean isAutoCommit(DatabaseHandle database) {
        try {
            return (int) GET_AUTOCOMMIT.invokeExact(address(database.address())) != 0;
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int setBusyTimeoutMillis(DatabaseHandle database, int timeoutMillis) {
        try {
            return (int) BUSY_TIMEOUT.invokeExact(address(database.address()), timeoutMillis);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public void interrupt(DatabaseHandle database) {
        try {
            INTERRUPT.invokeExact(address(database.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int totalChanges(DatabaseHandle database) {
        try {
            return (int) TOTAL_CHANGES.invokeExact(address(database.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public PrepareResult prepare(DatabaseHandle database, String sql) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sqlUtf8 = arena.allocateFrom(sql);
            MemorySegment statementOut = arena.allocate(ADDRESS);
            MemorySegment tailOut = arena.allocate(ADDRESS);
            int resultCode = invokePrepare(database, sqlUtf8, statementOut, tailOut);
            long statementAddress = statementOut.get(ADDRESS, 0).address();
            MemorySegment tailAddress = tailOut.get(ADDRESS, 0);
            if (resultCode != 0 || statementAddress == 0 || tailAddress.address() == 0) {
                return new PrepareResult(resultCode, new StatementHandle(statementAddress));
            }

            // The tail must point into the SQL allocation before it can be safely sliced.
            long tailOffset = tailAddress.address() - sqlUtf8.address();
            if (tailOffset < 0 || tailOffset >= sqlUtf8.byteSize()) {
                nativeFinalize(statementAddress);
                throw new IllegalStateException("SQLite returned a SQL tail outside the input buffer");
            }
            String tailSql = sqlUtf8.asSlice(tailOffset).getString(0);
            MemorySegment tailUtf8 = arena.allocateFrom(tailSql);
            MemorySegment tailStatementOut = arena.allocate(ADDRESS);
            MemorySegment tailEndOut = arena.allocate(ADDRESS);
            int tailResult = invokePrepare(database, tailUtf8, tailStatementOut, tailEndOut);
            long tailStatementAddress = tailStatementOut.get(ADDRESS, 0).address();
            if (tailResult != 0) {
                if (tailStatementAddress != 0) nativeFinalize(tailStatementAddress);
                nativeFinalize(statementAddress);
                return new PrepareResult(tailResult, StatementHandle.NULL);
            }
            if (tailStatementAddress != 0) {
                nativeFinalize(tailStatementAddress);
                nativeFinalize(statementAddress);
                return new PrepareResult(DRIVER_MULTIPLE_STATEMENTS, StatementHandle.NULL);
            }
            return new PrepareResult(resultCode, new StatementHandle(statementAddress));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int step(StatementHandle statement) {
        try {
            return (int) STEP.invokeExact(address(statement.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int reset(StatementHandle statement) {
        try {
            return (int) RESET.invokeExact(address(statement.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int clearBindings(StatementHandle statement) {
        try {
            return (int) CLEAR_BINDINGS.invokeExact(address(statement.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int parameterCount(StatementHandle statement) {
        try {
            return (int) BIND_PARAMETER_COUNT.invokeExact(address(statement.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int bindNull(StatementHandle statement, int parameterIndex) {
        try {
            return (int) BIND_NULL.invokeExact(address(statement.address()), parameterIndex);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int bindLong(StatementHandle statement, int parameterIndex, long value) {
        try {
            return (int) BIND_LONG.invokeExact(address(statement.address()), parameterIndex, value);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int bindDouble(StatementHandle statement, int parameterIndex, double value) {
        try {
            return (int) BIND_DOUBLE.invokeExact(address(statement.address()), parameterIndex, value);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int bindText(StatementHandle statement, int parameterIndex, String value) {
        try (Arena arena = Arena.ofConfined()) {
            return (int) BIND_TEXT.invokeExact(
                    address(statement.address()), parameterIndex, arena.allocateFrom(value), -1, SQLITE_TRANSIENT);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int bindBlob(StatementHandle statement, int parameterIndex, byte[] value) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment blob = arena.allocate(Math.max(1, value.length), 1);
            if (value.length != 0) blob.asSlice(0, value.length).copyFrom(MemorySegment.ofArray(value));
            return (int) BIND_BLOB.invokeExact(
                    address(statement.address()), parameterIndex, blob, value.length, SQLITE_TRANSIENT);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int columnCount(StatementHandle statement) {
        try {
            return (int) COLUMN_COUNT.invokeExact(address(statement.address()));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int storageClass(StatementHandle statement, int columnIndex) {
        try {
            return (int) COLUMN_TYPE.invokeExact(address(statement.address()), columnIndex);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public long columnLong(StatementHandle statement, int columnIndex) {
        try {
            return (long) COLUMN_LONG.invokeExact(address(statement.address()), columnIndex);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public double columnDouble(StatementHandle statement, int columnIndex) {
        try {
            return (double) COLUMN_DOUBLE.invokeExact(address(statement.address()), columnIndex);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public String columnText(StatementHandle statement, int columnIndex) {
        try {
            MemorySegment text = (MemorySegment) COLUMN_TEXT.invokeExact(address(statement.address()), columnIndex);
            if (text.address() == 0) return null;
            int bytes = columnBytes(statement, columnIndex);
            return new String(text.reinterpret(bytes).toArray(JAVA_BYTE), StandardCharsets.UTF_8);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public byte[] columnBlob(StatementHandle statement, int columnIndex) {
        try {
            MemorySegment blob = (MemorySegment) COLUMN_BLOB.invokeExact(address(statement.address()), columnIndex);
            int bytes = columnBytes(statement, columnIndex);
            if (blob.address() == 0 || bytes == 0) return new byte[0];
            return blob.reinterpret(bytes).toArray(JAVA_BYTE);
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public String columnName(StatementHandle statement, int columnIndex) {
        try {
            return readCString((MemorySegment) COLUMN_NAME.invokeExact(address(statement.address()), columnIndex));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public String declaredType(StatementHandle statement, int columnIndex) {
        try {
            return readCString((MemorySegment) COLUMN_DECLTYPE.invokeExact(address(statement.address()), columnIndex));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    @Override
    public int finalizeStatement(StatementHandle statement) {
        return nativeFinalize(statement.address());
    }

    @Override
    public String errorMessage(DatabaseHandle database) {
        try {
            return readCString((MemorySegment) ERRMSG.invokeExact(address(database.address())));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    private static SymbolLookup loadSymbols() {
        return NativeLibraryLoader.load()
                .<SymbolLookup>map(path -> SymbolLookup.libraryLookup(path, LIBRARY_ARENA))
                .orElseGet(() -> SymbolLookup.libraryLookup("sqlitejdbc", LIBRARY_ARENA));
    }

    private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(SYMBOLS.findOrThrow(symbol), descriptor);
    }

    private static int invokePrepare(
            DatabaseHandle database, MemorySegment sql, MemorySegment statementOut, MemorySegment tailOut) throws Throwable {
        return (int) PREPARE.invokeExact(
                address(database.address()), sql, -1, 0, statementOut, tailOut);
    }

    private static int nativeFinalize(long statementAddress) {
        try {
            return (int) FINALIZE.invokeExact(address(statementAddress));
        } catch (Throwable error) {
            throw invocationFailure(error);
        }
    }

    private static int columnBytes(StatementHandle statement, int columnIndex) throws Throwable {
        return (int) COLUMN_BYTES.invokeExact(address(statement.address()), columnIndex);
    }

    private static MemorySegment address(long value) {
        return value == 0 ? MemorySegment.NULL : MemorySegment.ofAddress(value);
    }

    private static String readCString(MemorySegment value) {
        return value.address() == 0 ? null : value.reinterpret(MAX_C_STRING_BYTES).getString(0);
    }

    private static RuntimeException invocationFailure(Throwable error) {
        if (error instanceof RuntimeException runtime) return runtime;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException("SQLite native invocation failed", error);
    }
}
