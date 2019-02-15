package fr.proline.context;

import java.sql.Connection;

import javax.persistence.EntityManager;

import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;

/**
 * LcMsDbConnectionContext contains a JPA EntityManager and/or a SQL JDBC Connection.
 * <p>
 * WARNING : LcMsDbConnectionContext objects must be confined inside a single Thread.
 * 
 * @author LMN
 * 
 */
public class LcMsDbConnectionContext extends DatabaseConnectionContext {

	/**
	 * Creates a LcMsDbConnectionContext instance for JPA driven Database access.
	 * 
	 * @param entityManager
	 *            JPA EntityManager, must not be <code>null</code>.
	 * @param driverType
	 *            Database DriverType (H2, PostgreSQL, SQLLite).
	 */
	public LcMsDbConnectionContext(final EntityManager entityManager, final DriverType driverType) {
		super(entityManager, null, ProlineDatabaseType.LCMS, driverType);
	}

	/**
	 * Creates a LcMsDbConnectionContext instance from an Db connector for JPA
	 * EntityManager use.
	 * 
	 * @param dbConnector
	 *            Connector to target DataBase.
	 */
	public LcMsDbConnectionContext(final IDatabaseConnector dbConnector) {
		this(dbConnector.createEntityManager(), dbConnector.getDriverType());
		
		if( dbConnector.getProlineDatabaseType() != ProlineDatabaseType.LCMS ) {
			throw new IllegalArgumentException("The ProlineDatabaseType of the connector must be LCMS and not " + dbConnector.getProlineDatabaseType());
		}
	}

	/**
	 * Creates a LcMsDbConnectionContext instance for SQL JDBC driven Database access.
	 * 
	 * @param connection
	 *            SQL JDBC connection.
	 * @param driverType
	 *            Database DriverType (H2, PostgreSQL, SQLLite).
	 */
	public LcMsDbConnectionContext(final Connection connection, final DriverType driverType) {
		super(null, connection, ProlineDatabaseType.LCMS, driverType);
	}

}
