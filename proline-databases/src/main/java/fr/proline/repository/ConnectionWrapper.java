package fr.proline.repository;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

public class ConnectionWrapper implements Connection {

	private Connection m_connection;
	private IConnectionListener m_listener;
	
	public ConnectionWrapper(Connection m_connection, IConnectionListener m_listener) {
		this.m_connection = m_connection;
		this.m_listener = m_listener;
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return m_connection.unwrap(iface);
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return m_connection.isWrapperFor(iface);
	}

	public Statement createStatement() throws SQLException {
		return m_connection.createStatement();
	}

	public PreparedStatement prepareStatement(String sql) throws SQLException {
		return m_connection.prepareStatement(sql);
	}

	public CallableStatement prepareCall(String sql) throws SQLException {
		return m_connection.prepareCall(sql);
	}

	public String nativeSQL(String sql) throws SQLException {
		return m_connection.nativeSQL(sql);
	}

	public void setAutoCommit(boolean autoCommit) throws SQLException {
		m_connection.setAutoCommit(autoCommit);
	}

	public boolean getAutoCommit() throws SQLException {
		return m_connection.getAutoCommit();
	}

	public void commit() throws SQLException {
		m_connection.commit();
	}

	public void rollback() throws SQLException {
		m_connection.rollback();
	}

	public void close() throws SQLException {
		m_connection.close();
		m_listener.connectionClosed(this);
	}

	public boolean isClosed() throws SQLException {
		return m_connection.isClosed();
	}

	public DatabaseMetaData getMetaData() throws SQLException {
		return m_connection.getMetaData();
	}

	public void setReadOnly(boolean readOnly) throws SQLException {
		m_connection.setReadOnly(readOnly);
	}

	public boolean isReadOnly() throws SQLException {
		return m_connection.isReadOnly();
	}

	public void setCatalog(String catalog) throws SQLException {
		m_connection.setCatalog(catalog);
	}

	public String getCatalog() throws SQLException {
		return m_connection.getCatalog();
	}

	public void setTransactionIsolation(int level) throws SQLException {
		m_connection.setTransactionIsolation(level);
	}

	public int getTransactionIsolation() throws SQLException {
		return m_connection.getTransactionIsolation();
	}

	public SQLWarning getWarnings() throws SQLException {
		return m_connection.getWarnings();
	}

	public void clearWarnings() throws SQLException {
		m_connection.clearWarnings();
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		return m_connection.createStatement(resultSetType, resultSetConcurrency);
	}

	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
		throws SQLException {
		return m_connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
	}

	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
		throws SQLException {
		return m_connection.prepareCall(sql, resultSetType, resultSetConcurrency);
	}

	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return m_connection.getTypeMap();
	}

	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		m_connection.setTypeMap(map);
	}

	public void setHoldability(int holdability) throws SQLException {
		m_connection.setHoldability(holdability);
	}

	public int getHoldability() throws SQLException {
		return m_connection.getHoldability();
	}

	public Savepoint setSavepoint() throws SQLException {
		return m_connection.setSavepoint();
	}

	public Savepoint setSavepoint(String name) throws SQLException {
		return m_connection.setSavepoint(name);
	}

	public void rollback(Savepoint savepoint) throws SQLException {
		m_connection.rollback(savepoint);
	}

	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		m_connection.releaseSavepoint(savepoint);
	}

	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
		throws SQLException {
		return m_connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public PreparedStatement prepareStatement(
		String sql,
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability) throws SQLException {
		return m_connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public CallableStatement prepareCall(
		String sql,
		int resultSetType,
		int resultSetConcurrency,
		int resultSetHoldability) throws SQLException {
		return m_connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
	}

	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		return m_connection.prepareStatement(sql, autoGeneratedKeys);
	}

	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		return m_connection.prepareStatement(sql, columnIndexes);
	}

	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		return m_connection.prepareStatement(sql, columnNames);
	}

	public Clob createClob() throws SQLException {
		return m_connection.createClob();
	}

	public Blob createBlob() throws SQLException {
		return m_connection.createBlob();
	}

	public NClob createNClob() throws SQLException {
		return m_connection.createNClob();
	}

	public SQLXML createSQLXML() throws SQLException {
		return m_connection.createSQLXML();
	}

	public boolean isValid(int timeout) throws SQLException {
		return m_connection.isValid(timeout);
	}

	public void setClientInfo(String name, String value) throws SQLClientInfoException {
		m_connection.setClientInfo(name, value);
	}

	public void setClientInfo(Properties properties) throws SQLClientInfoException {
		m_connection.setClientInfo(properties);
	}

	public String getClientInfo(String name) throws SQLException {
		return m_connection.getClientInfo(name);
	}

	public Properties getClientInfo() throws SQLException {
		return m_connection.getClientInfo();
	}

	public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
		return m_connection.createArrayOf(typeName, elements);
	}

	public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
		return m_connection.createStruct(typeName, attributes);
	}

	public void setSchema(String schema) throws SQLException {
		m_connection.setSchema(schema);
	}

	public String getSchema() throws SQLException {
		return m_connection.getSchema();
	}

	public void abort(Executor executor) throws SQLException {
		m_connection.abort(executor);
	}

	public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
		m_connection.setNetworkTimeout(executor, milliseconds);
	}

	public int getNetworkTimeout() throws SQLException {
		return m_connection.getNetworkTimeout();
	}

}
