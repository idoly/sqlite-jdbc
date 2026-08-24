/*--------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
package io.github.idoly.sqlite.javax;

import io.github.idoly.sqlite.SQLiteConnection;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;

/** A pooled physical SQLite connection with replaceable logical connection handles. */
public final class SQLitePooledConnection implements PooledConnection {
    private final CopyOnWriteArrayList<ConnectionEventListener> connectionListeners =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<StatementEventListener> statementListeners =
            new CopyOnWriteArrayList<>();

    private SQLiteConnection physicalConnection;
    private LogicalConnection logicalConnection;
    private final int defaultTransactionIsolation;

    SQLitePooledConnection(SQLiteConnection physicalConnection) {
        this.physicalConnection = physicalConnection;
        defaultTransactionIsolation = physicalConnection.getTransactionIsolation();
    }

    @Override
    public synchronized Connection getConnection() throws SQLException {
        SQLiteConnection physical = requirePhysicalConnection();
        if (logicalConnection != null) {
            logicalConnection.close(false);
        }
        logicalConnection = new LogicalConnection(physical);
        return logicalConnection.proxy();
    }

    @Override
    public synchronized void close() throws SQLException {
        if (physicalConnection == null) return;

        SQLiteConnection physical = physicalConnection;
        SQLException failure = null;
        if (logicalConnection != null) {
            try {
                logicalConnection.invalidate();
            } catch (SQLException error) {
                failure = append(failure, error);
            }
            logicalConnection = null;
        }

        try {
            physical.close();
        } catch (SQLException error) {
            failure = append(failure, error);
        } finally {
            physicalConnection = null;
            connectionListeners.clear();
            statementListeners.clear();
        }
        if (failure != null) throw failure;
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        if (listener != null) connectionListeners.add(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        connectionListeners.remove(listener);
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        if (listener != null) statementListeners.add(listener);
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        statementListeners.remove(listener);
    }

    private SQLiteConnection requirePhysicalConnection() throws SQLException {
        if (physicalConnection == null || physicalConnection.isClosed()) {
            throw new SQLException("Pooled connection is closed");
        }
        return physicalConnection;
    }

    private void detach(LogicalConnection connection) {
        connection.closed.set(true);
        if (logicalConnection == connection) logicalConnection = null;
    }

    private void fireConnectionClosed() {
        ConnectionEvent event = new ConnectionEvent(this);
        for (ConnectionEventListener listener : connectionListeners) {
            listener.connectionClosed(event);
        }
    }

    private void fireConnectionError(SQLException error) {
        ConnectionEvent event = new ConnectionEvent(this, error);
        for (ConnectionEventListener listener : connectionListeners) {
            listener.connectionErrorOccurred(event);
        }
    }

    private final class LogicalConnection implements InvocationHandler {
        private final SQLiteConnection physical;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean errorReported = new AtomicBoolean();
        private final Set<StatementHandler> statements = ConcurrentHashMap.newKeySet();
        private final Connection proxy;

        private LogicalConnection(SQLiteConnection physical) {
            this.physical = physical;
            proxy =
                    (Connection)
                            Proxy.newProxyInstance(
                                    SQLitePooledConnection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    this);
        }

        private Connection proxy() {
            return proxy;
        }

        @Override
        public Object invoke(Object ignoredProxy, Method method, Object[] arguments)
                throws Throwable {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(ignoredProxy, method, arguments);
            }
            if (name.equals("close")) {
                close(true);
                return null;
            }
            if (name.equals("isClosed")) {
                return closed.get() || physical.isClosed();
            }
            if (name.equals("isValid") && closed.get()) return false;
            requireOpen();

            if (name.equals("abort")) {
                try {
                    method.invoke(physical, arguments);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
                synchronized (SQLitePooledConnection.this) {
                    detach(this);
                }
                if (errorReported.compareAndSet(false, true)) {
                    fireConnectionError(new SQLException("SQLite connection was aborted"));
                }
                return null;
            }

            if (name.equals("unwrap") || name.equals("isWrapperFor")) {
                Class<?> iface = (Class<?>) arguments[0];
                if (iface == null) throw new SQLException("interface must not be null");
                if (iface.isInstance(proxy)) return name.equals("unwrap") ? proxy : true;
            }

            try {
                Object result = method.invoke(physical, arguments);
                if (result instanceof Statement statement) return wrapStatement(statement);
                if (result instanceof DatabaseMetaData metadata) return wrapMetadata(metadata);
                return result;
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof SQLException sqlException) reportPhysicalFailure(sqlException);
                throw cause;
            }
        }

        private void close(boolean notifyPool) throws SQLException {
            SQLException failure;
            synchronized (SQLitePooledConnection.this) {
                if (!closed.compareAndSet(false, true)) return;

                failure = closeStatements();
                if (physicalConnection == physical && !physical.isClosed()) {
                    try {
                        if (!physical.getAutoCommit()) physical.rollback();
                        physical.setAutoCommit(true);
                        if (physical.getTransactionIsolation() != defaultTransactionIsolation) {
                            physical.setTransactionIsolation(defaultTransactionIsolation);
                        }
                    } catch (SQLException error) {
                        failure = append(failure, error);
                    }
                }
                detach(this);
            }

            if (failure != null) {
                errorReported.set(true);
                fireConnectionError(failure);
                throw failure;
            }
            if (notifyPool) fireConnectionClosed();
        }

        private void invalidate() throws SQLException {
            synchronized (SQLitePooledConnection.this) {
                if (!closed.compareAndSet(false, true)) return;
                SQLException failure = closeStatements();
                if (failure != null) throw failure;
            }
        }

        private SQLException closeStatements() {
            SQLException failure = null;
            for (StatementHandler statement : Set.copyOf(statements)) {
                try {
                    statement.close(false);
                } catch (SQLException error) {
                    failure = append(failure, error);
                }
            }
            return failure;
        }

        private void requireOpen() throws SQLException {
            if (closed.get()) throw new SQLException("Connection is closed");
        }

        private Object wrapStatement(Statement statement) {
            StatementHandler handler = new StatementHandler(statement);
            statements.add(handler);
            return handler.proxy();
        }

        private DatabaseMetaData wrapMetadata(DatabaseMetaData metadata) {
            return (DatabaseMetaData)
                    Proxy.newProxyInstance(
                            SQLitePooledConnection.class.getClassLoader(),
                            new Class<?>[] {DatabaseMetaData.class},
                            (metadataProxy, method, arguments) -> {
                                if (method.getDeclaringClass() == Object.class) {
                                    return invokeObjectMethod(metadataProxy, method, arguments);
                                }
                                requireOpen();
                                String name = method.getName();
                                if (name.equals("getConnection")) return proxy;
                                if (name.equals("unwrap") || name.equals("isWrapperFor")) {
                                    Class<?> iface = (Class<?>) arguments[0];
                                    if (iface == null) {
                                        throw new SQLException("interface must not be null");
                                    }
                                    if (iface.isInstance(metadataProxy)) {
                                        return name.equals("unwrap") ? metadataProxy : true;
                                    }
                                }
                                try {
                                    return method.invoke(metadata, arguments);
                                } catch (InvocationTargetException error) {
                                    Throwable cause = error.getCause();
                                    if (cause instanceof SQLException sqlException) {
                                        reportPhysicalFailure(sqlException);
                                    }
                                    throw cause;
                                }
                            });
        }

        private void reportPhysicalFailure(SQLException error) {
            try {
                if (physical.isClosed() && errorReported.compareAndSet(false, true)) {
                    synchronized (SQLitePooledConnection.this) {
                        detach(this);
                    }
                    fireConnectionError(error);
                }
            } catch (SQLException ignored) {
                // Keep the original database failure.
            }
        }

        private final class StatementHandler implements InvocationHandler {
            private final Statement statement;
            private final AtomicBoolean statementClosed = new AtomicBoolean();
            private final Statement proxy;

            private StatementHandler(Statement statement) {
                this.statement = statement;
                Class<?> statementType =
                        statement instanceof PreparedStatement
                                ? PreparedStatement.class
                                : Statement.class;
                proxy =
                        (Statement)
                                Proxy.newProxyInstance(
                                        SQLitePooledConnection.class.getClassLoader(),
                                        new Class<?>[] {statementType},
                                        this);
            }

            private Statement proxy() {
                return proxy;
            }

            @Override
            public Object invoke(Object ignoredProxy, Method method, Object[] arguments)
                    throws Throwable {
                String name = method.getName();
                if (method.getDeclaringClass() == Object.class) {
                    return invokeObjectMethod(ignoredProxy, method, arguments);
                }
                if (name.equals("close")) {
                    close(true);
                    return null;
                }
                if (name.equals("isClosed")) return statementClosed.get() || statement.isClosed();
                if (name.equals("getConnection")) {
                    requireOpen();
                    return LogicalConnection.this.proxy;
                }
                requireOpen();
                if (statementClosed.get()) throw new SQLException("Statement is closed");
                if (name.equals("unwrap") || name.equals("isWrapperFor")) {
                    Class<?> iface = (Class<?>) arguments[0];
                    if (iface == null) throw new SQLException("interface must not be null");
                    if (iface.isInstance(proxy)) return name.equals("unwrap") ? proxy : true;
                }

                try {
                    return method.invoke(statement, arguments);
                } catch (InvocationTargetException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof SQLException sqlException) {
                        notifyStatementError(sqlException);
                        reportPhysicalFailure(sqlException);
                    }
                    throw cause;
                }
            }

            private void close(boolean notifyPool) throws SQLException {
                SQLException failure = null;
                PreparedStatement closedPreparedStatement = null;
                synchronized (SQLitePooledConnection.this) {
                    if (!statementClosed.compareAndSet(false, true)) return;
                    statements.remove(this);
                    try {
                        statement.close();
                        if (notifyPool && proxy instanceof PreparedStatement prepared) {
                            closedPreparedStatement = prepared;
                        }
                    } catch (SQLException error) {
                        failure = error;
                    }
                }

                if (failure != null) {
                    notifyStatementError(failure);
                    throw failure;
                }
                if (closedPreparedStatement != null) {
                    StatementEvent event =
                            new StatementEvent(
                                    SQLitePooledConnection.this, closedPreparedStatement);
                    for (StatementEventListener listener : statementListeners) {
                        listener.statementClosed(event);
                    }
                }
            }

            private void notifyStatementError(SQLException error) {
                if (!(proxy instanceof PreparedStatement prepared)) return;
                StatementEvent event =
                        new StatementEvent(SQLitePooledConnection.this, prepared, error);
                for (StatementEventListener listener : statementListeners) {
                    listener.statementErrorOccurred(event);
                }
            }
        }
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> proxy.getClass().getInterfaces()[0].getSimpleName() + " handle";
            default -> throw new UnsupportedOperationException(method.getName());
        };
    }

    private static SQLException append(SQLException failure, SQLException next) {
        if (failure == null) return next;
        failure.addSuppressed(next);
        return failure;
    }
}
