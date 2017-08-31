package fr.proline.repository;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.sql.DataSource;

/**
 * This interface is a wrapper of Proline database connections. Object implementing this interface provide
 * informations about the connected database (Database Nature (UDS, MSI, PS, PDI, etc), Driver Type, in memory
 * or not, etc). Thanks to this wrapper, the database connection can be exposed as a DataSource or an
 * EntityManagerFactory.
 * 
 * @author CB205360
 * 
 */
public interface IDatabaseConnector extends Closeable {
	
	public enum ConnectionPoolType {
		NO_POOL_MANAGEMENT,
		SIMPLE_POOL_MANAGEMENT,
		HIGH_PERF_POOL_MANAGEMENT		
	}

	public static ConnectionPoolType DEFAULT_POOL_TYPE = ConnectionPoolType.SIMPLE_POOL_MANAGEMENT;

	ProlineDatabaseType getProlineDatabaseType();

	DriverType getDriverType();

	boolean isMemory();

	void setAdditionalProperties(Map<Object, Object> params);

	/** Return the value of the key in the connection properties, null if none */
	Object getProperty(Object key);

	DataSource getDataSource();
	
	Connection createUnmanagedConnection() throws SQLException;
	
	EntityManager createEntityManager();
	
	int getOpenEntityManagerCount();
	
    int getOpenConnectionCount();

	void close();

	boolean isClosed();

}
