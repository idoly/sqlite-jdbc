module io.github.idoly.sqlitejdbc {
    requires transitive java.sql;
    requires static java.sql.rowset;
    requires static org.graalvm.nativeimage;

    exports io.github.idoly.sqlite;
    exports io.github.idoly.sqlite.datasource;

    provides java.sql.Driver with
            io.github.idoly.sqlite.JDBC;
}
