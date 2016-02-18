package fr.proline.context;

import java.sql.Connection;

import javax.persistence.EntityManager;

import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;

/**
 * UdsDbConnectionContext contains a JPA EntityManager and/or a SQL JDBC Connection.
 * <p>
 * WARNING : UdsDbConnectionContext objects must be confined inside a single Thread.
 * 
 * @author LMN
 * 
 */
public class UdsDbConnectionContext extends DatabaseConnectionContext {

	/**
	 * Creates a UdsDbConnectionContext instance for JPA driven Database access.
	 * 
	 * @param entityManager
	 *            JPA EntityManager, must not be <code>null</code>.
	 * @param driverType
	 *            Database DriverType (H2, PostgreSQL, SQLLite).
	 */
	public UdsDbConnectionContext(final EntityManager entityManager, final DriverType driverType) {
		super(entityManager, null, ProlineDatabaseType.UDS, driverType);
	}

	/**
	 * Creates a UdsDbConnectionContext instance from an Db connector for JPA
	 * EntityManager use.
	 * 
	 * @param dbConnector
	 *            Connector to target DataBase.
	 */
	public UdsDbConnectionContext(final IDatabaseConnector dbConnector) {
		this(dbConnector.getEntityManagerFactory().createEntityManager(), dbConnector.getDriverType());
		
		if( dbConnector.getProlineDatabaseType() != ProlineDatabaseType.UDS ) {
			throw new IllegalArgumentException("The ProlineDatabaseType of the connector must be LCMS and not " + dbConnector.getProlineDatabaseType());
		}
	}

	/**
	 * Creates a UdsDbConnectionContext instance for SQL JDBC driven Database access.
	 * 
	 * @param connection
	 *            SQL JDBC connection.
	 * @param driverType
	 *            Database DriverType (H2, PostgreSQL, SQLLite).
	 */
	public UdsDbConnectionContext(final Connection connection, final DriverType driverType) {
		super(null, connection, ProlineDatabaseType.UDS, driverType);
	}

}
