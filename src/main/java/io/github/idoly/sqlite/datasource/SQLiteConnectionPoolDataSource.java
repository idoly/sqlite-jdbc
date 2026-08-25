package io.github.idoly.sqlite.datasource;

import io.github.idoly.sqlite.SQLiteConfig;
import io.github.idoly.sqlite.SQLiteDataSource;
import java.sql.SQLException;
import javax.sql.PooledConnection;

public final class SQLiteConnectionPoolDataSource extends SQLiteDataSource
        implements javax.sql.ConnectionPoolDataSource {

    /** Default constructor. */
    public SQLiteConnectionPoolDataSource() {
        super();
    }

    /**
     * Creates a data source based on the provided configuration.
     *
     * @param config The configuration for the data source.
     */
    public SQLiteConnectionPoolDataSource(SQLiteConfig config) {
        super(config);
    }

    /**
     * @see javax.sql.ConnectionPoolDataSource#getPooledConnection()
     */
    public PooledConnection getPooledConnection() throws SQLException {
        return getPooledConnection(null, null);
    }

    /**
     * @see javax.sql.ConnectionPoolDataSource#getPooledConnection(java.lang.String,
     *     java.lang.String)
     */
    public PooledConnection getPooledConnection(String user, String password) throws SQLException {
        return new SQLitePooledConnection(getConnection(user, password));
    }
}
