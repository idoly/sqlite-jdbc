#include "sqlite3.h"

#if defined(_WIN32)
#define SQLITEJDBC_API __declspec(dllexport)
#else
#define SQLITEJDBC_API __attribute__((visibility("default")))
#endif

SQLITEJDBC_API const char *sqlitejdbc_libversion(void) {
    return sqlite3_libversion();
}

SQLITEJDBC_API int sqlitejdbc_open_v2(
        const char *filename, sqlite3 **database, int flags, const char *vfs) {
    return sqlite3_open_v2(filename, database, flags, vfs);
}

SQLITEJDBC_API int sqlitejdbc_close_v2(sqlite3 *database) {
    return sqlite3_close_v2(database);
}

SQLITEJDBC_API int sqlitejdbc_exec(
        sqlite3 *database, const char *sql, sqlite3_callback callback, void *context, char **error_message) {
    return sqlite3_exec(database, sql, callback, context, error_message);
}

SQLITEJDBC_API int sqlitejdbc_get_autocommit(sqlite3 *database) {
    return sqlite3_get_autocommit(database);
}

SQLITEJDBC_API int sqlitejdbc_busy_timeout(sqlite3 *database, int milliseconds) {
    return sqlite3_busy_timeout(database, milliseconds);
}

SQLITEJDBC_API void sqlitejdbc_interrupt(sqlite3 *database) {
    sqlite3_interrupt(database);
}

SQLITEJDBC_API int sqlitejdbc_total_changes(sqlite3 *database) {
    return sqlite3_total_changes(database);
}

SQLITEJDBC_API int sqlitejdbc_prepare_v3(
        sqlite3 *database,
        const char *sql,
        int sql_length,
        unsigned int flags,
        sqlite3_stmt **statement,
        const char **tail) {
    return sqlite3_prepare_v3(database, sql, sql_length, flags, statement, tail);
}

SQLITEJDBC_API int sqlitejdbc_step(sqlite3_stmt *statement) {
    return sqlite3_step(statement);
}

SQLITEJDBC_API int sqlitejdbc_reset(sqlite3_stmt *statement) {
    return sqlite3_reset(statement);
}

SQLITEJDBC_API int sqlitejdbc_clear_bindings(sqlite3_stmt *statement) {
    return sqlite3_clear_bindings(statement);
}

SQLITEJDBC_API int sqlitejdbc_bind_parameter_count(sqlite3_stmt *statement) {
    return sqlite3_bind_parameter_count(statement);
}

SQLITEJDBC_API int sqlitejdbc_bind_null(sqlite3_stmt *statement, int index) {
    return sqlite3_bind_null(statement, index);
}

SQLITEJDBC_API int sqlitejdbc_bind_int64(sqlite3_stmt *statement, int index, sqlite3_int64 value) {
    return sqlite3_bind_int64(statement, index, value);
}

SQLITEJDBC_API int sqlitejdbc_bind_double(sqlite3_stmt *statement, int index, double value) {
    return sqlite3_bind_double(statement, index, value);
}

SQLITEJDBC_API int sqlitejdbc_bind_text(
        sqlite3_stmt *statement, int index, const char *value, int length, sqlite3_destructor_type destructor) {
    return sqlite3_bind_text(statement, index, value, length, destructor);
}

SQLITEJDBC_API int sqlitejdbc_bind_blob(
        sqlite3_stmt *statement, int index, const void *value, int length, sqlite3_destructor_type destructor) {
    return sqlite3_bind_blob(statement, index, value, length, destructor);
}

SQLITEJDBC_API int sqlitejdbc_column_count(sqlite3_stmt *statement) {
    return sqlite3_column_count(statement);
}

SQLITEJDBC_API int sqlitejdbc_column_type(sqlite3_stmt *statement, int index) {
    return sqlite3_column_type(statement, index);
}

SQLITEJDBC_API sqlite3_int64 sqlitejdbc_column_int64(sqlite3_stmt *statement, int index) {
    return sqlite3_column_int64(statement, index);
}

SQLITEJDBC_API double sqlitejdbc_column_double(sqlite3_stmt *statement, int index) {
    return sqlite3_column_double(statement, index);
}

SQLITEJDBC_API const unsigned char *sqlitejdbc_column_text(sqlite3_stmt *statement, int index) {
    return sqlite3_column_text(statement, index);
}

SQLITEJDBC_API const void *sqlitejdbc_column_blob(sqlite3_stmt *statement, int index) {
    return sqlite3_column_blob(statement, index);
}

SQLITEJDBC_API int sqlitejdbc_column_bytes(sqlite3_stmt *statement, int index) {
    return sqlite3_column_bytes(statement, index);
}

SQLITEJDBC_API const char *sqlitejdbc_column_name(sqlite3_stmt *statement, int index) {
    return sqlite3_column_name(statement, index);
}

SQLITEJDBC_API const char *sqlitejdbc_column_decltype(sqlite3_stmt *statement, int index) {
    return sqlite3_column_decltype(statement, index);
}

SQLITEJDBC_API int sqlitejdbc_finalize(sqlite3_stmt *statement) {
    return sqlite3_finalize(statement);
}

SQLITEJDBC_API const char *sqlitejdbc_errmsg(sqlite3 *database) {
    return sqlite3_errmsg(database);
}
