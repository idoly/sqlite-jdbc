package io.github.idoly.sqlite;

import io.github.idoly.sqlite.core.SQLiteDatabase;
import io.github.idoly.sqlite.core.SQLiteResultCodes;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides an interface for creating SQLite user-defined functions.
 *
 * <p>A subclass of <code>io.github.idoly.sqlite.SQLiteFunction</code> can be registered with <code>
 * SQLiteFunction.create()</code> and called by the name it was given. All functions must implement
 * <code>
 * xFunc()</code>, which is called when SQLite runs the custom function. E.g.
 *
 * <pre>
 *      Class.forName("io.github.idoly.sqlite.SQLiteDriver");
 *      Connection conn = DriverManager.getConnection("jdbc:sqlite:");
 *
 *      SQLiteFunction.create(conn, "myFunc", new SQLiteFunction() {
 *          protected void xFunc() {
 *              System.out.println("myFunc called!");
 *          }
 *      });
 *
 *      conn.createStatement().execute("select myFunc();");
 *  </pre>
 *
 * <p>Arguments passed to a custom function can be accessed using the <code>protected</code>
 * functions provided. <code>args()</code> returns the number of arguments passed, while <code>
 * value_&lt;type&gt;(int)</code> returns the value of the specific argument. Similarly, a function
 * can return a value using the <code>result(&lt;type&gt;)</code> function.
 */
public abstract class SQLiteFunction {
    /**
     * Flag to provide to {@link #create(Connection, String, SQLiteFunction, int)} that marks this
     * SQLiteFunction as deterministic, making is usable in Indexes on Expressions.
     */
    public static final int FLAG_DETERMINISTIC = 0x800;

    private SQLiteConnection conn;
    private SQLiteDatabase db;

    long context = 0; // pointer sqlite3_context*
    long value = 0; // pointer sqlite3_value**
    int args = 0;

    /**
     * Registers a given function with the connection.
     *
     * @param connection The connection.
     * @param name The name of the function.
     * @param function The function to register.
     */
    public static void create(Connection connection, String name, SQLiteFunction function)
            throws SQLException {
        create(connection, name, function, 0);
    }

    /**
     * Registers a given function with the connection.
     *
     * @param connection The connection.
     * @param name The name of the function.
     * @param function The function to register.
     * @param flags Extra flags to pass, such as {@link #FLAG_DETERMINISTIC}
     */
    public static void create(
            Connection connection, String name, SQLiteFunction function, int flags)
            throws SQLException {
        create(connection, name, function, -1, flags);
    }

    /**
     * Registers a given function with the connection.
     *
     * @param connection The connection.
     * @param name The name of the function.
     * @param function The function to register.
     * @param argumentCount The number of arguments that the function takes.
     * @param flags Extra flags to pass, such as {@link #FLAG_DETERMINISTIC}
     */
    public static void create(
            Connection connection,
            String name,
            SQLiteFunction function,
            int argumentCount,
            int flags)
            throws SQLException {
        SQLiteConnection sqliteConnection = requireSQLiteConnection(connection);
        if (name == null || name.isEmpty())
            throw new SQLException("function name must not be empty");
        if (function == null) throw new SQLException("function must not be null");
        validateArgumentCount(argumentCount);

        function.conn = sqliteConnection;
        function.db = sqliteConnection.getDatabase();
        if (function.db.create_function(name, function, argumentCount, flags)
                != SQLiteResultCodes.SQLITE_OK) {
            throw new SQLException("error creating function");
        }
    }

    /**
     * Removes a named function from the given connection.
     *
     * @param connection The connection to remove the function from.
     * @param name The name of the function.
     * @param argumentCount Number of function arguments, or -1 for a variadic function.
     */
    public static void destroy(Connection connection, String name, int argumentCount)
            throws SQLException {
        SQLiteConnection sqliteConnection = requireSQLiteConnection(connection);
        if (name == null || name.isEmpty())
            throw new SQLException("function name must not be empty");
        validateArgumentCount(argumentCount);
        sqliteConnection.getDatabase().destroy_function(name, argumentCount);
    }

    /**
     * Removes a named function from the given connection.
     *
     * @param connection The connection to remove the function from.
     * @param name The name of the function.
     */
    public static void destroy(Connection connection, String name) throws SQLException {
        destroy(connection, name, -1);
    }

    private static SQLiteConnection requireSQLiteConnection(Connection connection)
            throws SQLException {
        if (!(connection instanceof SQLiteConnection sqliteConnection)) {
            throw new SQLException("connection must be a SQLite connection");
        }
        if (connection.isClosed()) throw new SQLException("connection closed");
        return sqliteConnection;
    }

    private static void validateArgumentCount(int argumentCount) throws SQLException {
        if (argumentCount < -1 || argumentCount > 127) {
            throw new SQLException("argument count must be between -1 and 127: " + argumentCount);
        }
    }

    /**
     * Called by SQLite as a custom function. Should access arguments through <code>value_*(int)
     * </code>, return results with <code>result(*)</code> and throw errors with <code>error(String)
     * </code>.
     */
    protected abstract void xFunc() throws SQLException;

    /** Internal FFM scalar-function dispatch entry point. */
    public final synchronized void invokeFunction(
            long contextAddress, long valuesAddress, int argumentCount) throws SQLException {
        context = contextAddress;
        value = valuesAddress;
        args = argumentCount;
        try {
            xFunc();
        } finally {
            context = 0;
            value = 0;
            args = 0;
        }
    }

    /** Internal FFM access to the current sqlite3_value pointer array. */
    public final synchronized long argumentValuesAddress() {
        return value;
    }

    /**
     * Returns the number of arguments passed to the function. Can only be called from <code>xFunc()
     * </code>.
     */
    protected final synchronized int args() throws SQLException {
        checkContext();
        return args;
    }

    /** Called by <code>xFunc</code> to return a value. */
    protected final synchronized void result(byte[] value) throws SQLException {
        checkContext();
        db.result_blob(context, value);
    }

    /** Called by <code>xFunc</code> to return a value. */
    protected final synchronized void result(double value) throws SQLException {
        checkContext();
        db.result_double(context, value);
    }

    /** Called by <code>xFunc</code> to return a value. */
    protected final synchronized void result(int value) throws SQLException {
        checkContext();
        db.result_int(context, value);
    }

    /** Called by <code>xFunc</code> to return a value. */
    protected final synchronized void result(long value) throws SQLException {
        checkContext();
        db.result_long(context, value);
    }

    /** Called by <code>xFunc</code> to return a value. */
    protected final synchronized void result() throws SQLException {
        checkContext();
        db.result_null(context);
    }

    /** Called by <code>xFunc</code> to return a value. */
    protected final synchronized void result(String value) throws SQLException {
        checkContext();
        db.result_text(context, value);
    }

    /** Called by <code>xFunc</code> to throw an error. */
    protected final synchronized void error(String err) throws SQLException {
        checkContext();
        db.result_error(context, err);
    }

    /** Called by <code>xFunc</code> to access the value of an argument. */
    protected final synchronized String value_text(int arg) throws SQLException {
        checkValue(arg);
        return db.value_text(this, arg);
    }

    /** Called by <code>xFunc</code> to access the value of an argument. */
    protected final synchronized byte[] value_blob(int arg) throws SQLException {
        checkValue(arg);
        return db.value_blob(this, arg);
    }

    /** Called by <code>xFunc</code> to access the value of an argument. */
    protected final synchronized double value_double(int arg) throws SQLException {
        checkValue(arg);
        return db.value_double(this, arg);
    }

    /** Called by <code>xFunc</code> to access the value of an argument. */
    protected final synchronized int value_int(int arg) throws SQLException {
        checkValue(arg);
        return db.value_int(this, arg);
    }

    /** Called by <code>xFunc</code> to access the value of an argument. */
    protected final synchronized long value_long(int arg) throws SQLException {
        checkValue(arg);
        return db.value_long(this, arg);
    }

    /** Called by <code>xFunc</code> to access the value of an argument. */
    protected final synchronized int value_type(int arg) throws SQLException {
        checkValue(arg);
        return db.value_type(this, arg);
    }

    private void checkContext() throws SQLException {
        if (conn == null || conn.getDatabase() == null || context == 0) {
            throw new SQLException("no context, not allowed to read value");
        }
    }

    private void checkValue(int arg) throws SQLException {
        if (conn == null || conn.getDatabase() == null || value == 0) {
            throw new SQLException("not in value access state");
        }
        if (arg >= args) {
            throw new SQLException("arg " + arg + " out bounds [0," + args + ")");
        }
    }

    /**
     * Provides an interface for creating SQLite user-defined aggregate functions.
     *
     * @see SQLiteFunction
     */
    public abstract static class Aggregate extends SQLiteFunction implements Cloneable {
        /**
         * @see io.github.idoly.sqlite.SQLiteFunction#xFunc()
         */
        protected final void xFunc() {}

        /**
         * Defines the abstract aggregate callback function
         *
         * @see <a
         *     href="https://www.sqlite.org/c3ref/aggregate_context.html">https://www.sqlite.org/c3ref/aggregate_context.html</a>
         */
        protected abstract void xStep() throws SQLException;

        /**
         * Defines the abstract aggregate callback function
         *
         * @see <a
         *     href="https://www.sqlite.org/c3ref/aggregate_context.html">https://www.sqlite.org/c3ref/aggregate_context.html</a>
         */
        protected abstract void xFinal() throws SQLException;

        /** Internal FFM aggregate-step dispatch entry point. */
        public final synchronized void invokeStep(
                long contextAddress, long valuesAddress, int argumentCount) throws SQLException {
            context = contextAddress;
            value = valuesAddress;
            args = argumentCount;
            try {
                xStep();
            } finally {
                context = 0;
                value = 0;
                args = 0;
            }
        }

        /** Internal FFM aggregate-final dispatch entry point. */
        public final synchronized void invokeFinal(long contextAddress) throws SQLException {
            context = contextAddress;
            try {
                xFinal();
            } finally {
                context = 0;
            }
        }

        /**
         * @see java.lang.Object#clone()
         */
        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    /**
     * Provides an interface for creating SQLite user-defined window functions.
     *
     * @see Aggregate
     */
    public abstract static class Window extends Aggregate {
        /**
         * Defines the abstract window callback function
         *
         * @see <a
         *     href="https://www.sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions">https://www.sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions</a>
         */
        protected abstract void xInverse() throws SQLException;

        /**
         * Defines the abstract window callback function
         *
         * @see <a
         *     href="https://www.sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions">https://www.sqlite.org/windowfunctions.html#user_defined_aggregate_window_functions</a>
         */
        protected abstract void xValue() throws SQLException;

        /** Internal FFM window-inverse dispatch entry point. */
        public final synchronized void invokeInverse(
                long contextAddress, long valuesAddress, int argumentCount) throws SQLException {
            context = contextAddress;
            value = valuesAddress;
            args = argumentCount;
            try {
                xInverse();
            } finally {
                context = 0;
                value = 0;
                args = 0;
            }
        }

        /** Internal FFM window-value dispatch entry point. */
        public final synchronized void invokeValue(long contextAddress) throws SQLException {
            context = contextAddress;
            try {
                xValue();
            } finally {
                context = 0;
            }
        }
    }
}
