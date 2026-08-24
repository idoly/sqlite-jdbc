module io.github.idoly.sqlitejdbc {
    requires static org.slf4j;
    requires transitive java.sql;
    requires transitive java.sql.rowset;
    requires static org.graalvm.nativeimage;

    exports io.github.idoly.sqlite;
    exports io.github.idoly.sqlite.javax;

    provides java.sql.Driver with
            io.github.idoly.sqlite.JDBC;
}
