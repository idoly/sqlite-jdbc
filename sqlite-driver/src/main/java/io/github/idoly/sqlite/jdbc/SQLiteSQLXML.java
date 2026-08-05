package io.github.idoly.sqlite.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

final class SQLiteSQLXML implements SQLXML {
    private String value = "";
    private boolean freed;

    @Override
    public void free() {
        value = null;
        freed = true;
    }

    @Override
    public InputStream getBinaryStream() throws SQLException {
        ensureOpen();
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream setBinaryStream() throws SQLException {
        ensureOpen();
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                value = toString(StandardCharsets.UTF_8);
            }
        };
    }

    @Override
    public Reader getCharacterStream() throws SQLException {
        ensureOpen();
        return new StringReader(value);
    }

    @Override
    public Writer setCharacterStream() throws SQLException {
        ensureOpen();
        return new StringWriter() {
            @Override
            public void close() {
                value = toString();
            }
        };
    }

    @Override
    public String getString() throws SQLException {
        ensureOpen();
        return value;
    }

    @Override
    public void setString(String value) throws SQLException {
        ensureOpen();
        if (value == null) throw new SQLException("SQLXML value cannot be null", "HY009");
        this.value = value;
    }

    @Override
    public <T extends Source> T getSource(Class<T> sourceClass) throws SQLException {
        ensureOpen();
        if (sourceClass == null || sourceClass == StreamSource.class) {
            return cast(sourceClass, new StreamSource(new StringReader(value)));
        }
        throw unsupported(sourceClass);
    }

    @Override
    public <T extends Result> T setResult(Class<T> resultClass) throws SQLException {
        ensureOpen();
        if (resultClass == null || resultClass == StreamResult.class) {
            return cast(resultClass, new StreamResult(setCharacterStream()));
        }
        throw unsupported(resultClass);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Class<T> type, Object value) {
        return type == null ? (T) value : type.cast(value);
    }

    private void ensureOpen() throws SQLException {
        if (freed) throw new SQLException("SQLXML value has been freed", "HY010");
    }

    private static SQLFeatureNotSupportedException unsupported(Class<?> type) {
        return new SQLFeatureNotSupportedException("XML source/result type is not supported: " + type, "0A000");
    }
}
