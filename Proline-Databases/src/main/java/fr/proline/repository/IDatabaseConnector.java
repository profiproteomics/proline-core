package fr.proline.repository;

import java.io.Closeable;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
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

	ProlineDatabaseType getProlineDatabaseType();

	DriverType getDriverType();

	boolean isMemory();

	void setAdditionalProperties(Map<Object, Object> params);

	/** Return the value of the key in the connection properties, null if none */
	Object getProperty(Object key);

	DataSource getDataSource();

	EntityManagerFactory getEntityManagerFactory();
	
	EntityManager createEntityManager();
	
	int getOpenEntityManagerCount();
	
	//int incrementOpenEntityManagerCount();
	
	int decrementOpenEntityManagerCount();

	void close();

	boolean isClosed();

}
