package fr.proline.context;

import java.sql.Connection;

import javax.persistence.EntityManager;

import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;

/**
 * MsiDbConnectionContext contains a JPA EntityManager and/or a SQL JDBC Connection.
 * <p>
 * WARNING : MsiDbConnectionContext objects must be confined inside a single Thread.
 * 
 * @author LMN
 * 
 */
public class MsiDbConnectionContext extends DatabaseConnectionContext {

	/**
	 * Creates a MsiDbConnectionContext instance for JPA driven Database access.
	 * 
	 * @param entityManager
	 *            JPA EntityManager, must not be <code>null</code>.
	 * @param driverType
	 *            Database DriverType (H2, PostgreSQL, SQLLite).
	 */
	public MsiDbConnectionContext(final EntityManager entityManager, final DriverType driverType) {
		super(entityManager, null, ProlineDatabaseType.MSI, driverType);
	}

	/**
	 * Creates a MsiDbConnectionContext instance from an Db connector for JPA
	 * EntityManager use.
	 * 
	 * @param dbConnector
	 *            Connector to target DataBase.
	 */
	public MsiDbConnectionContext(final IDatabaseConnector dbConnector) {
		this(dbConnector.createEntityManager(), dbConnector.getDriverType());
		
		if( dbConnector.getProlineDatabaseType() != ProlineDatabaseType.MSI ) {
			throw new IllegalArgumentException("The ProlineDatabaseType of the connector must be MSI and not " + dbConnector.getProlineDatabaseType());
		}
	}

	/**
	 * Creates a MsiDbConnectionContext instance for SQL JDBC driven Database access.
	 * 
	 * @param connection
	 *            SQL JDBC connection.
	 * @param driverType
	 *            Database DriverType (H2, PostgreSQL, SQLLite).
	 */
	public MsiDbConnectionContext(final Connection connection, final DriverType driverType) {
		super(null, connection, ProlineDatabaseType.MSI, driverType);
	}

}
