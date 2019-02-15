package fr.proline.repository;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

public class DataSourceWrapper implements DataSource {

	private DataSource m_datasource;
	private IConnectionListener m_listener;

	
	public DataSourceWrapper(DataSource m_datasource, IConnectionListener m_listener) {
		super();
		this.m_datasource = m_datasource;
		this.m_listener = m_listener;
	}

	public PrintWriter getLogWriter() throws SQLException {
		return m_datasource.getLogWriter();
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return m_datasource.unwrap(iface);
	}

	public void setLogWriter(PrintWriter out) throws SQLException {
		m_datasource.setLogWriter(out);
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return m_datasource.isWrapperFor(iface);
	}

	public Connection getConnection() throws SQLException {
		Connection connection = new ConnectionWrapper(m_datasource.getConnection(), m_listener);
		m_listener.connectionCreated(connection);
		return connection;
	}

	public void setLoginTimeout(int seconds) throws SQLException {
		m_datasource.setLoginTimeout(seconds);
	}

	public Connection getConnection(String username, String password) throws SQLException {
		return m_datasource.getConnection(username, password);
	}

	public int getLoginTimeout() throws SQLException {
		return m_datasource.getLoginTimeout();
	}

	public Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return m_datasource.getParentLogger();
	}
	
	
	
}
