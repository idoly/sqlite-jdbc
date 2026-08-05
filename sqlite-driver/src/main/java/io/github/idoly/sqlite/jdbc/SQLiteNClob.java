package io.github.idoly.sqlite.jdbc;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;
import javax.sql.rowset.serial.SerialClob;

final class SQLiteNClob implements NClob {
    private final Clob delegate;

    SQLiteNClob() throws SQLException {
        delegate = new SerialClob(new char[0]);
    }

    SQLiteNClob(String value) throws SQLException {
        delegate = new SerialClob(value.toCharArray());
    }

    @Override public long length() throws SQLException { return delegate.length(); }
    @Override public String getSubString(long pos, int length) throws SQLException { return delegate.getSubString(pos, length); }
    @Override public Reader getCharacterStream() throws SQLException { return delegate.getCharacterStream(); }
    @Override public InputStream getAsciiStream() throws SQLException { return delegate.getAsciiStream(); }
    @Override public long position(String search, long start) throws SQLException { return delegate.position(search, start); }
    @Override public long position(Clob search, long start) throws SQLException { return delegate.position(search, start); }
    @Override public int setString(long pos, String value) throws SQLException { return delegate.setString(pos, value); }
    @Override public int setString(long pos, String value, int offset, int length) throws SQLException {
        return delegate.setString(pos, value, offset, length);
    }
    @Override public OutputStream setAsciiStream(long pos) throws SQLException { return delegate.setAsciiStream(pos); }
    @Override public Writer setCharacterStream(long pos) throws SQLException { return delegate.setCharacterStream(pos); }
    @Override public void truncate(long length) throws SQLException { delegate.truncate(length); }
    @Override public void free() throws SQLException { delegate.free(); }
    @Override public Reader getCharacterStream(long pos, long length) throws SQLException {
        return delegate.getCharacterStream(pos, length);
    }
}
