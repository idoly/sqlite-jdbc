package io.github.idoly.sqlite.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQLite database and driver capabilities exposed through JDBC metadata. */
final class SQLiteDatabaseMetaData extends DatabaseMetaDataAdapter {
    private final SQLiteConnection connection;
    private final String url;

    SQLiteDatabaseMetaData(SQLiteConnection connection, String url) {
        this.connection = connection;
        this.url = url;
    }

    @Override public boolean allProceduresAreCallable() { return false; }
    @Override public boolean allTablesAreSelectable() { return true; }
    @Override public String getURL() { return url; }
    @Override public String getUserName() { return ""; }
    @Override public boolean isReadOnly() throws SQLException { return connection.isReadOnly(); }
    @Override public boolean nullsAreSortedHigh() { return false; }
    @Override public boolean nullsAreSortedLow() { return true; }
    @Override public boolean nullsAreSortedAtStart() { return false; }
    @Override public boolean nullsAreSortedAtEnd() { return false; }
    @Override public String getDatabaseProductName() { return "SQLite"; }
    @Override public String getDatabaseProductVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT sqlite_version()")) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
    @Override public String getDriverName() { return "idoly sqlite-jdbc"; }
    @Override public String getDriverVersion() { return SQLiteDriver.driverVersion(); }
    @Override public int getDriverMajorVersion() { return 0; }
    @Override public int getDriverMinorVersion() { return 1; }
    @Override public int getJDBCMajorVersion() { return 4; }
    @Override public int getJDBCMinorVersion() { return 3; }
    @Override public int getDatabaseMajorVersion() throws SQLException { return versionPart(0); }
    @Override public int getDatabaseMinorVersion() throws SQLException { return versionPart(1); }

    @Override public boolean usesLocalFiles() { return true; }
    @Override public boolean usesLocalFilePerTable() { return false; }
    @Override public boolean supportsMixedCaseIdentifiers() { return true; }
    @Override public boolean storesUpperCaseIdentifiers() { return false; }
    @Override public boolean storesLowerCaseIdentifiers() { return false; }
    @Override public boolean storesMixedCaseIdentifiers() { return true; }
    @Override public boolean supportsMixedCaseQuotedIdentifiers() { return true; }
    @Override public boolean storesUpperCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesLowerCaseQuotedIdentifiers() { return false; }
    @Override public boolean storesMixedCaseQuotedIdentifiers() { return true; }
    @Override public String getIdentifierQuoteString() { return "\""; }
    @Override public String getSearchStringEscape() { return "\\"; }
    @Override public String getExtraNameCharacters() { return "$"; }
    @Override public String getSQLKeywords() {
        return "ABORT,ACTION,AFTER,ANALYZE,ATTACH,AUTOINCREMENT,BEFORE,CONFLICT,DATABASE,DETACH,"
                + "EXCLUSIVE,EXPLAIN,FAIL,GLOB,IGNORE,INDEXED,INSTEAD,PRAGMA,QUERY,RAISE,RECURSIVE,"
                + "REGEXP,REINDEX,RENAME,REPLACE,STRICT,VACUUM,VIRTUAL,WITHOUT";
    }
    @Override public String getNumericFunctions() { return "abs,max,min,random,round,sign,sqrt,pow,mod"; }
    @Override public String getStringFunctions() { return "char,concat,format,glob,hex,instr,length,like,lower,ltrim,printf,quote,replace,rtrim,substr,trim,unicode,upper"; }
    @Override public String getSystemFunctions() { return "changes,last_insert_rowid,sqlite_version,total_changes,typeof"; }
    @Override public String getTimeDateFunctions() { return "date,datetime,julianday,strftime,time,timediff,unixepoch"; }

    @Override public boolean supportsAlterTableWithAddColumn() { return true; }
    @Override public boolean supportsAlterTableWithDropColumn() { return true; }
    @Override public boolean supportsColumnAliasing() { return true; }
    @Override public boolean nullPlusNonNullIsNull() { return true; }
    @Override public boolean supportsConvert() { return true; }
    @Override public boolean supportsConvert(int fromType, int toType) { return true; }
    @Override public boolean supportsTableCorrelationNames() { return true; }
    @Override public boolean supportsDifferentTableCorrelationNames() { return false; }
    @Override public boolean supportsExpressionsInOrderBy() { return true; }
    @Override public boolean supportsOrderByUnrelated() { return true; }
    @Override public boolean supportsGroupBy() { return true; }
    @Override public boolean supportsGroupByUnrelated() { return true; }
    @Override public boolean supportsGroupByBeyondSelect() { return true; }
    @Override public boolean supportsLikeEscapeClause() { return true; }
    @Override public boolean supportsMultipleResultSets() { return false; }
    @Override public boolean supportsMultipleTransactions() { return true; }
    @Override public boolean supportsNonNullableColumns() { return true; }
    @Override public boolean supportsMinimumSQLGrammar() { return true; }
    @Override public boolean supportsCoreSQLGrammar() { return false; }
    @Override public boolean supportsExtendedSQLGrammar() { return false; }
    @Override public boolean supportsANSI92EntryLevelSQL() { return false; }
    @Override public boolean supportsANSI92IntermediateSQL() { return false; }
    @Override public boolean supportsANSI92FullSQL() { return false; }
    @Override public boolean supportsIntegrityEnhancementFacility() { return true; }
    @Override public boolean supportsOuterJoins() { return true; }
    @Override public boolean supportsFullOuterJoins() { return true; }
    @Override public boolean supportsLimitedOuterJoins() { return true; }
    @Override public String getSchemaTerm() { return ""; }
    @Override public String getProcedureTerm() { return ""; }
    @Override public String getCatalogTerm() { return "database"; }
    @Override public boolean isCatalogAtStart() { return true; }
    @Override public String getCatalogSeparator() { return "."; }

    @Override public boolean supportsSchemasInDataManipulation() { return false; }
    @Override public boolean supportsSchemasInProcedureCalls() { return false; }
    @Override public boolean supportsSchemasInTableDefinitions() { return false; }
    @Override public boolean supportsSchemasInIndexDefinitions() { return false; }
    @Override public boolean supportsSchemasInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsCatalogsInDataManipulation() { return true; }
    @Override public boolean supportsCatalogsInProcedureCalls() { return false; }
    @Override public boolean supportsCatalogsInTableDefinitions() { return true; }
    @Override public boolean supportsCatalogsInIndexDefinitions() { return true; }
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() { return false; }
    @Override public boolean supportsPositionedDelete() { return false; }
    @Override public boolean supportsPositionedUpdate() { return false; }
    @Override public boolean supportsSelectForUpdate() { return false; }
    @Override public boolean supportsStoredProcedures() { return false; }
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() { return false; }
    @Override public boolean supportsSubqueriesInComparisons() { return true; }
    @Override public boolean supportsSubqueriesInExists() { return true; }
    @Override public boolean supportsSubqueriesInIns() { return true; }
    @Override public boolean supportsSubqueriesInQuantifieds() { return false; }
    @Override public boolean supportsCorrelatedSubqueries() { return true; }
    @Override public boolean supportsUnion() { return true; }
    @Override public boolean supportsUnionAll() { return true; }
    @Override public boolean supportsOpenCursorsAcrossCommit() { return false; }
    @Override public boolean supportsOpenCursorsAcrossRollback() { return false; }
    @Override public boolean supportsOpenStatementsAcrossCommit() { return true; }
    @Override public boolean supportsOpenStatementsAcrossRollback() { return true; }

    @Override public int getMaxBinaryLiteralLength() { return 0; }
    @Override public int getMaxCharLiteralLength() { return 0; }
    @Override public int getMaxColumnNameLength() { return 0; }
    @Override public int getMaxColumnsInGroupBy() { return 2000; }
    @Override public int getMaxColumnsInIndex() { return 64; }
    @Override public int getMaxColumnsInOrderBy() { return 2000; }
    @Override public int getMaxColumnsInSelect() { return 2000; }
    @Override public int getMaxColumnsInTable() { return 2000; }
    @Override public int getMaxConnections() { return 0; }
    @Override public int getMaxCursorNameLength() { return 0; }
    @Override public int getMaxIndexLength() { return 0; }
    @Override public int getMaxSchemaNameLength() { return 0; }
    @Override public int getMaxProcedureNameLength() { return 0; }
    @Override public int getMaxCatalogNameLength() { return 0; }
    @Override public int getMaxRowSize() { return 0; }
    @Override public boolean doesMaxRowSizeIncludeBlobs() { return true; }
    @Override public int getMaxStatementLength() { return 1_000_000_000; }
    @Override public int getMaxStatements() { return 0; }
    @Override public int getMaxTableNameLength() { return 0; }
    @Override public int getMaxTablesInSelect() { return 64; }
    @Override public int getMaxUserNameLength() { return 0; }

    @Override public int getDefaultTransactionIsolation() { return Connection.TRANSACTION_SERIALIZABLE; }
    @Override public boolean supportsTransactions() { return true; }
    @Override public boolean supportsTransactionIsolationLevel(int isolationLevel) {
        return isolationLevel == Connection.TRANSACTION_SERIALIZABLE;
    }
    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() { return true; }
    @Override public boolean supportsDataManipulationTransactionsOnly() { return false; }
    @Override public boolean dataDefinitionCausesTransactionCommit() { return false; }
    @Override public boolean dataDefinitionIgnoredInTransactions() { return false; }
    @Override public boolean supportsSavepoints() { return true; }
    @Override public boolean supportsNamedParameters() { return true; }
    @Override public boolean supportsMultipleOpenResults() { return false; }
    @Override public boolean supportsGetGeneratedKeys() { return true; }
    @Override public boolean supportsBatchUpdates() { return true; }

    @Override
    public boolean supportsResultSetType(int resultSetType) {
        return resultSetType == ResultSet.TYPE_FORWARD_ONLY;
    }
    @Override
    public boolean supportsResultSetConcurrency(int resultSetType, int resultSetConcurrency) {
        return resultSetType == ResultSet.TYPE_FORWARD_ONLY
                && resultSetConcurrency == ResultSet.CONCUR_READ_ONLY;
    }
    @Override
    public boolean supportsResultSetHoldability(int resultSetHoldability) {
        return resultSetHoldability == ResultSet.CLOSE_CURSORS_AT_COMMIT;
    }
    @Override public int getResultSetHoldability() { return ResultSet.CLOSE_CURSORS_AT_COMMIT; }
    @Override public boolean ownUpdatesAreVisible(int resultSetType) { return false; }
    @Override public boolean ownDeletesAreVisible(int resultSetType) { return false; }
    @Override public boolean ownInsertsAreVisible(int resultSetType) { return false; }
    @Override public boolean othersUpdatesAreVisible(int resultSetType) { return false; }
    @Override public boolean othersDeletesAreVisible(int resultSetType) { return false; }
    @Override public boolean othersInsertsAreVisible(int resultSetType) { return false; }
    @Override public boolean updatesAreDetected(int resultSetType) { return false; }
    @Override public boolean deletesAreDetected(int resultSetType) { return false; }
    @Override public boolean insertsAreDetected(int resultSetType) { return false; }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        if (!matchesCatalogAndSchema(catalog, schemaPattern)) return emptyTables();
        List<String> requestedTypes = normalizeTableTypes(types);
        if (requestedTypes.isEmpty()) return emptyTables();
        String placeholders = String.join(",", java.util.Collections.nCopies(requestedTypes.size(), "?"));
        String sql = "SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, name AS TABLE_NAME, "
                + "CASE type WHEN 'view' THEN 'VIEW' ELSE 'TABLE' END AS TABLE_TYPE, "
                + "NULL AS REMARKS, NULL AS TYPE_CAT, NULL AS TYPE_SCHEM, NULL AS TYPE_NAME, "
                + "NULL AS SELF_REFERENCING_COL_NAME, NULL AS REF_GENERATION "
                + "FROM sqlite_schema WHERE type IN ('table','view') "
                + "AND name LIKE ? ESCAPE '\\' AND CASE type WHEN 'view' THEN 'VIEW' ELSE 'TABLE' END IN ("
                + placeholders + ") ORDER BY TABLE_TYPE, TABLE_NAME";
        List<Object> parameters = new ArrayList<>();
        parameters.add(pattern(tableNamePattern));
        parameters.addAll(requestedTypes);
        return query(sql, parameters.toArray());
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
            throws SQLException {
        if (!matchesCatalogAndSchema(catalog, schemaPattern)) return emptyColumns();
        String sql = "SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, s.name AS TABLE_NAME, p.name AS COLUMN_NAME, "
                + "CASE WHEN upper(p.type) LIKE '%INT%' THEN -5 "
                + "WHEN upper(p.type) LIKE '%REAL%' OR upper(p.type) LIKE '%FLOA%' OR upper(p.type) LIKE '%DOUB%' THEN 8 "
                + "WHEN upper(p.type) LIKE '%BLOB%' THEN 2004 ELSE 12 END AS DATA_TYPE, "
                + "p.type AS TYPE_NAME, 0 AS COLUMN_SIZE, NULL AS BUFFER_LENGTH, 0 AS DECIMAL_DIGITS, "
                + "10 AS NUM_PREC_RADIX, CASE p.\"notnull\" WHEN 1 THEN 0 ELSE 1 END AS NULLABLE, "
                + "NULL AS REMARKS, p.dflt_value AS COLUMN_DEF, 0 AS SQL_DATA_TYPE, 0 AS SQL_DATETIME_SUB, "
                + "0 AS CHAR_OCTET_LENGTH, p.cid + 1 AS ORDINAL_POSITION, "
                + "CASE p.\"notnull\" WHEN 1 THEN 'NO' ELSE 'YES' END AS IS_NULLABLE, "
                + "NULL AS SCOPE_CATALOG, NULL AS SCOPE_SCHEMA, NULL AS SCOPE_TABLE, NULL AS SOURCE_DATA_TYPE, "
                + "'NO' AS IS_AUTOINCREMENT, 'NO' AS IS_GENERATEDCOLUMN "
                + "FROM sqlite_schema s JOIN pragma_table_xinfo(s.name) p "
                + "WHERE s.type IN ('table','view') AND s.name LIKE ? ESCAPE '\\' "
                + "AND p.name LIKE ? ESCAPE '\\' ORDER BY s.name, p.cid";
        return query(sql, pattern(tableNamePattern), pattern(columnNamePattern));
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        if (!matchesCatalogAndSchema(catalog, schema) || table == null) return emptyPrimaryKeys();
        return query("SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, ? AS TABLE_NAME, name AS COLUMN_NAME, "
                + "pk AS KEY_SEQ, NULL AS PK_NAME FROM pragma_table_info(?) WHERE pk > 0 ORDER BY pk", table, table);
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return query("SELECT NULL AS TABLE_SCHEM, NULL AS TABLE_CATALOG WHERE 0");
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException { return getSchemas(); }

    @Override
    public ResultSet getCatalogs() throws SQLException {
        return query("SELECT name AS TABLE_CAT FROM pragma_database_list ORDER BY seq");
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        return query("SELECT 'TABLE' AS TABLE_TYPE UNION ALL SELECT 'VIEW' AS TABLE_TYPE ORDER BY TABLE_TYPE");
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        return query("SELECT 'INTEGER' AS TYPE_NAME, -5 AS DATA_TYPE, 19 AS PRECISION, NULL AS LITERAL_PREFIX, "
                + "NULL AS LITERAL_SUFFIX, NULL AS CREATE_PARAMS, 1 AS NULLABLE, 0 AS CASE_SENSITIVE, 3 AS SEARCHABLE, "
                + "0 AS UNSIGNED_ATTRIBUTE, 0 AS FIXED_PREC_SCALE, 1 AS AUTO_INCREMENT, 'INTEGER' AS LOCAL_TYPE_NAME, "
                + "0 AS MINIMUM_SCALE, 0 AS MAXIMUM_SCALE, 0 AS SQL_DATA_TYPE, 0 AS SQL_DATETIME_SUB, 10 AS NUM_PREC_RADIX "
                + "UNION ALL SELECT 'REAL',8,15,NULL,NULL,NULL,1,0,3,0,0,0,'REAL',0,0,0,0,10 "
                + "UNION ALL SELECT 'TEXT',12,0,'''','''',NULL,1,1,3,0,0,0,'TEXT',0,0,0,0,10 "
                + "UNION ALL SELECT 'BLOB',2004,0,'X''','''',NULL,1,0,3,0,0,0,'BLOB',0,0,0,0,10 "
                + "UNION ALL SELECT 'NULL',0,0,NULL,NULL,NULL,1,0,0,0,0,0,'NULL',0,0,0,0,10");
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
            throws SQLException {
        return empty("PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME");
    }

    @Override
    public ResultSet getProcedureColumns(
            String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern)
            throws SQLException {
        return empty("PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "COLUMN_NAME");
    }

    @Override
    public ResultSet getColumnPrivileges(
            String catalog, String schema, String table, String columnNamePattern) throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME");
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME");
    }
    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        if (!matchesCatalogAndSchema(catalog, schema) || table == null) return emptyForeignKeys();
        return query("SELECT NULL AS PKTABLE_CAT, NULL AS PKTABLE_SCHEM, f.\"table\" AS PKTABLE_NAME, "
                + "f.\"to\" AS PKCOLUMN_NAME, NULL AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, ? AS FKTABLE_NAME, "
                + "f.\"from\" AS FKCOLUMN_NAME, f.seq + 1 AS KEY_SEQ, "
                + foreignKeyRule("f.on_update") + " AS UPDATE_RULE, "
                + foreignKeyRule("f.on_delete") + " AS DELETE_RULE, NULL AS FK_NAME, NULL AS PK_NAME, "
                + importedKeyNotDeferrable + " AS DEFERRABILITY "
                + "FROM pragma_foreign_key_list(?) f ORDER BY f.id, f.seq", table, table);
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        if (!matchesCatalogAndSchema(catalog, schema) || table == null) return emptyForeignKeys();
        return query("SELECT NULL AS PKTABLE_CAT, NULL AS PKTABLE_SCHEM, f.\"table\" AS PKTABLE_NAME, "
                + "f.\"to\" AS PKCOLUMN_NAME, NULL AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, s.name AS FKTABLE_NAME, "
                + "f.\"from\" AS FKCOLUMN_NAME, f.seq + 1 AS KEY_SEQ, "
                + foreignKeyRule("f.on_update") + " AS UPDATE_RULE, "
                + foreignKeyRule("f.on_delete") + " AS DELETE_RULE, NULL AS FK_NAME, NULL AS PK_NAME, "
                + importedKeyNotDeferrable + " AS DEFERRABILITY "
                + "FROM sqlite_schema s JOIN pragma_foreign_key_list(s.name) f "
                + "WHERE s.type = 'table' AND f.\"table\" = ? ORDER BY s.name, f.id, f.seq", table);
    }

    @Override
    public ResultSet getCrossReference(
            String parentCatalog, String parentSchema, String parentTable,
            String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
        if (!matchesCatalogAndSchema(parentCatalog, parentSchema)
                || !matchesCatalogAndSchema(foreignCatalog, foreignSchema)
                || parentTable == null || foreignTable == null) return emptyForeignKeys();
        return query("SELECT NULL AS PKTABLE_CAT, NULL AS PKTABLE_SCHEM, f.\"table\" AS PKTABLE_NAME, "
                + "f.\"to\" AS PKCOLUMN_NAME, NULL AS FKTABLE_CAT, NULL AS FKTABLE_SCHEM, ? AS FKTABLE_NAME, "
                + "f.\"from\" AS FKCOLUMN_NAME, f.seq + 1 AS KEY_SEQ, "
                + foreignKeyRule("f.on_update") + " AS UPDATE_RULE, "
                + foreignKeyRule("f.on_delete") + " AS DELETE_RULE, NULL AS FK_NAME, NULL AS PK_NAME, "
                + importedKeyNotDeferrable + " AS DEFERRABILITY FROM pragma_foreign_key_list(?) f "
                + "WHERE f.\"table\" = ? ORDER BY f.id, f.seq", foreignTable, foreignTable, parentTable);
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate)
            throws SQLException {
        if (!matchesCatalogAndSchema(catalog, schema) || table == null) {
            return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE", "INDEX_QUALIFIER", "INDEX_NAME", "TYPE", "ORDINAL_POSITION", "COLUMN_NAME", "ASC_OR_DESC", "CARDINALITY", "PAGES", "FILTER_CONDITION");
        }
        String uniqueFilter = unique ? " AND il.\"unique\" = 1" : "";
        return query("SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, ? AS TABLE_NAME, "
                + "CASE il.\"unique\" WHEN 1 THEN 0 ELSE 1 END AS NON_UNIQUE, NULL AS INDEX_QUALIFIER, "
                + "il.name AS INDEX_NAME, " + tableIndexOther + " AS TYPE, ii.seqno + 1 AS ORDINAL_POSITION, "
                + "ii.name AS COLUMN_NAME, NULL AS ASC_OR_DESC, 0 AS CARDINALITY, 0 AS PAGES, "
                + "CASE il.partial WHEN 1 THEN 'partial' ELSE NULL END AS FILTER_CONDITION "
                + "FROM pragma_index_list(?) il JOIN pragma_index_info(il.name) ii WHERE 1=1"
                + uniqueFilter + " ORDER BY il.name, ii.seqno", table, table);
    }
    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
            throws SQLException {
        return empty("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME");
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
            throws SQLException {
        return empty("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME");
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME");
    }

    @Override
    public ResultSet getAttributes(
            String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern)
            throws SQLException {
        return empty("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "ATTR_NAME");
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
            throws SQLException {
        return empty("FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME");
    }

    @Override
    public ResultSet getFunctionColumns(
            String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern)
            throws SQLException {
        return empty("FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "COLUMN_NAME");
    }

    @Override
    public ResultSet getPseudoColumns(
            String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern)
            throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME");
    }

    @Override public Connection getConnection() { return connection; }
    @Override public int getSQLStateType() { return sqlStateSQL99; }
    @Override public boolean locatorsUpdateCopy() { return false; }
    @Override public boolean autoCommitFailureClosesAllResultSets() { return false; }
    @Override public RowIdLifetime getRowIdLifetime() { return RowIdLifetime.ROWID_UNSUPPORTED; }
    @Override public long getMaxLogicalLobSize() { return 1_000_000_000L; }
    @Override public boolean supportsRefCursors() { return false; }
    @Override public boolean supportsSharding() { return false; }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (type != null && type.isInstance(this)) return type.cast(this);
        throw new SQLException("DatabaseMetaData does not wrap " + type, "HY000");
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type != null && type.isInstance(this); }

    private ResultSet query(String sql, Object... parameterValues) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            for (int i = 0; i < parameterValues.length; i++) {
                statement.setObject(i + 1, parameterValues[i]);
            }
            statement.closeOnCompletion();
            return statement.executeQuery();
        } catch (SQLException | RuntimeException error) {
            statement.close();
            throw error;
        }
    }

    private ResultSet empty(String... columnNames) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < columnNames.length; i++) {
            if (i > 0) sql.append(", ");
            sql.append("NULL AS \"").append(columnNames[i].replace("\"", "\"\"")).append('"');
        }
        return query(sql.append(" WHERE 0").toString());
    }

    private ResultSet emptyTables() throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS", "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION");
    }

    private ResultSet emptyColumns() throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE", "REMARKS", "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG", "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT", "IS_GENERATEDCOLUMN");
    }

    private ResultSet emptyPrimaryKeys() throws SQLException {
        return empty("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME");
    }

    private ResultSet emptyForeignKeys() throws SQLException {
        return empty("PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME", "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE", "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY");
    }

    private boolean matchesCatalogAndSchema(String catalog, String schemaPattern) {
        boolean catalogMatches = catalog == null || catalog.isEmpty() || catalog.equalsIgnoreCase("main");
        boolean schemaMatches = schemaPattern == null || schemaPattern.isEmpty() || schemaPattern.equals("%");
        return catalogMatches && schemaMatches;
    }

    private static String pattern(String value) { return value == null ? "%" : value; }

    private static String foreignKeyRule(String columnExpression) {
        return "CASE upper(" + columnExpression + ") WHEN 'CASCADE' THEN " + importedKeyCascade
                + " WHEN 'RESTRICT' THEN " + importedKeyRestrict
                + " WHEN 'SET NULL' THEN " + importedKeySetNull
                + " WHEN 'SET DEFAULT' THEN " + importedKeySetDefault
                + " ELSE " + importedKeyNoAction + " END";
    }

    private static List<String> normalizeTableTypes(String[] requestedTypes) {
        if (requestedTypes == null) return List.of("TABLE", "VIEW");
        List<String> normalizedTypes = new ArrayList<>();
        for (String requestedType : requestedTypes) {
            if (requestedType == null) continue;
            if (requestedType.equalsIgnoreCase("TABLE")) normalizedTypes.add("TABLE");
            else if (requestedType.equalsIgnoreCase("VIEW")) normalizedTypes.add("VIEW");
        }
        return normalizedTypes;
    }

    private int versionPart(int componentIndex) throws SQLException {
        String[] parts = getDatabaseProductVersion().split("\\.");
        return componentIndex < parts.length ? Integer.parseInt(parts[componentIndex]) : 0;
    }
}
