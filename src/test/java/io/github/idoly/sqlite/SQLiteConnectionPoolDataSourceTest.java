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
package io.github.idoly.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.idoly.sqlite.datasource.SQLiteConnectionPoolDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;
import org.junit.jupiter.api.Test;

public class SQLiteConnectionPoolDataSourceTest {

    @Test
    public void connectionTest() throws SQLException {
        ConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();

        PooledConnection pooledConn = ds.getPooledConnection();

        Connection handle = pooledConn.getConnection();
        assertThat(handle.isClosed()).isFalse();
        assertThat(handle.createStatement().execute("select 1")).isTrue();

        Connection handle2 = pooledConn.getConnection();
        assertThat(handle.isClosed()).isTrue();
        Connection finalHandle = handle;
        assertThatThrownBy(() -> finalHandle.createStatement().execute("select 1"))
                .isInstanceOf(SQLException.class)
                .hasMessage("Connection is closed");

        assertThat(handle2.createStatement().execute("select 1")).isTrue();
        handle2.close();

        handle = pooledConn.getConnection();
        assertThat(handle.createStatement().execute("select 1")).isTrue();

        pooledConn.close();
        assertThat(handle.isClosed()).isTrue();
    }

    /**
     * When a handle is closed the physical connection must be reset (rollback + auto-commit) before
     * the pool is notified, otherwise a concurrent borrower can reuse the physical connection while
     * the reset is still running. See issue #821.
     */
    @Test
    public void concurrentReuseDoesNotRaceOnClose() throws Exception {
        SQLiteConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();
        ds.setUrl("jdbc:sqlite::memory:");

        DummyPool pool = new DummyPool(ds);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread t =
                    new Thread(
                            () -> {
                                for (int j = 0; j < 2000 && failure.get() == null; j++) {
                                    try (Connection c = pool.getConnection()) {
                                        c.setAutoCommit(false);
                                        c.createStatement().execute("select 1");
                                    } catch (Throwable e) {
                                        failure.compareAndSet(null, e);
                                    }
                                }
                            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertThat(failure.get()).isNull();
    }

    /** Minimal pool that hands out and takes back pooled connections, like a real pool would. */
    private static class DummyPool implements ConnectionEventListener {
        private final List<PooledConnection> available = new ArrayList<>();
        private final ConnectionPoolDataSource dataSource;

        DummyPool(ConnectionPoolDataSource dataSource) {
            this.dataSource = dataSource;
        }

        synchronized Connection getConnection() throws SQLException {
            Iterator<PooledConnection> it = available.iterator();
            PooledConnection pooled;
            if (it.hasNext()) {
                pooled = it.next();
                it.remove();
            } else {
                pooled = dataSource.getPooledConnection();
                pooled.addConnectionEventListener(this);
            }
            return pooled.getConnection();
        }

        @Override
        public synchronized void connectionClosed(ConnectionEvent event) {
            available.add((PooledConnection) event.getSource());
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {}
    }

    @Test
    public void replacingHandleDoesNotReturnAnInUseConnectionToThePool() throws SQLException {
        ConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();
        PooledConnection pooled = ds.getPooledConnection();
        AtomicInteger closeEvents = new AtomicInteger();
        pooled.addConnectionEventListener(
                new ConnectionEventListener() {
                    @Override
                    public void connectionClosed(ConnectionEvent event) {
                        closeEvents.incrementAndGet();
                    }

                    @Override
                    public void connectionErrorOccurred(ConnectionEvent event) {}
                });
        try {
            Connection first = pooled.getConnection();
            Connection second = pooled.getConnection();
            assertThat(first.isClosed()).isTrue();
            assertThat(closeEvents).hasValue(0);

            second.close();
            assertThat(closeEvents).hasValue(1);
        } finally {
            pooled.close();
        }
    }

    @Test
    public void abortNotifiesPoolThatPhysicalConnectionIsInvalid() throws SQLException {
        ConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();
        PooledConnection pooled = ds.getPooledConnection();
        AtomicInteger errorEvents = new AtomicInteger();
        pooled.addConnectionEventListener(
                new ConnectionEventListener() {
                    @Override
                    public void connectionClosed(ConnectionEvent event) {}

                    @Override
                    public void connectionErrorOccurred(ConnectionEvent event) {
                        errorEvents.incrementAndGet();
                    }
                });
        try {
            AtomicReference<Runnable> abortTask = new AtomicReference<>();
            Connection handle = pooled.getConnection();
            handle.abort(abortTask::set);
            assertThat(handle.isClosed()).isTrue();
            assertThat(errorEvents).hasValue(1);
            assertThatThrownBy(pooled::getConnection).isInstanceOf(SQLException.class);
            abortTask.get().run();
        } finally {
            pooled.close();
        }
    }

    @Test
    public void statementsAndMetadataDoNotExposeThePhysicalConnection() throws SQLException {
        ConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();
        PooledConnection pooled = ds.getPooledConnection();
        try {
            Connection handle = pooled.getConnection();
            assertThat(handle.unwrap(Connection.class)).isSameAs(handle);
            try (Statement statement = handle.createStatement()) {
                assertThat(statement.getConnection()).isSameAs(handle);
                assertThat(statement.unwrap(Statement.class)).isSameAs(statement);
                assertThat(statement.unwrap(Statement.class).getConnection()).isSameAs(handle);
            }
            var metadata = handle.getMetaData();
            assertThat(metadata.getConnection()).isSameAs(handle);
            assertThat(metadata.unwrap(java.sql.DatabaseMetaData.class)).isSameAs(metadata);
            assertThat(metadata.unwrap(java.sql.DatabaseMetaData.class).getConnection())
                    .isSameAs(handle);

            handle.createStatement().getConnection().close();
            assertThat(handle.isClosed()).isTrue();
            try (Connection next = pooled.getConnection();
                    Statement statement = next.createStatement()) {
                assertThat(statement.execute("select 1")).isTrue();
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    public void returningHandleRollsBackAndClosesItsStatements() throws SQLException {
        ConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();
        PooledConnection pooled = ds.getPooledConnection();
        try {
            Connection handle = pooled.getConnection();
            Statement statement = handle.createStatement();
            statement.execute("create table pooled_test(value)");
            handle.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            handle.setAutoCommit(false);
            statement.executeUpdate("insert into pooled_test values (1)");
            handle.close();

            assertThat(statement.isClosed()).isTrue();
            try (Connection next = pooled.getConnection();
                    Statement query = next.createStatement();
                    var result = query.executeQuery("select count(*) from pooled_test")) {
                assertThat(next.getAutoCommit()).isTrue();
                assertThat(next.getTransactionIsolation())
                        .isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    public void returningHandleRestoresReadOnlyState() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setExplicitReadOnly(true);
        PooledConnection pooled = new SQLiteConnectionPoolDataSource(config).getPooledConnection();
        try {
            Connection handle = pooled.getConnection();
            handle.setReadOnly(true);
            handle.close();

            try (Connection next = pooled.getConnection()) {
                assertThat(next.isReadOnly()).isFalse();
            }
        } finally {
            pooled.close();
        }
    }

    @Test
    public void preparedStatementCloseNotifiesListeners() throws SQLException {
        ConnectionPoolDataSource ds = new SQLiteConnectionPoolDataSource();
        PooledConnection pooled = ds.getPooledConnection();
        try {
            AtomicInteger closedStatements = new AtomicInteger();
            pooled.addStatementEventListener(
                    new StatementEventListener() {
                        @Override
                        public void statementClosed(StatementEvent event) {
                            closedStatements.incrementAndGet();
                        }

                        @Override
                        public void statementErrorOccurred(StatementEvent event) {}
                    });

            try (Connection handle = pooled.getConnection();
                    PreparedStatement statement = handle.prepareStatement("select 1")) {
                assertThat(statement.execute()).isTrue();
            }
            assertThat(closedStatements).hasValue(1);
        } finally {
            pooled.close();
        }
    }
}
