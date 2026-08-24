module io.github.idoly.sqlitejdbc {
    requires static org.slf4j;
    requires transitive java.sql;
    requires transitive java.sql.rowset;
    requires static org.graalvm.nativeimage;

    exports io.github.idoly.sqlite;
    exports io.github.idoly.sqlite.core;
    exports io.github.idoly.sqlite.date;
    exports io.github.idoly.sqlite.javax;
    exports io.github.idoly.sqlite.jdbc4;
    exports io.github.idoly.sqlite.util;

    provides java.sql.Driver with
            io.github.idoly.sqlite.JDBC;
}
