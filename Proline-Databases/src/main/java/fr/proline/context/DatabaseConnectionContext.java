package fr.proline.context;

import java.sql.Connection;
import java.sql.SQLException;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.util.JDBCReturningWork;
import fr.proline.repository.util.JDBCWork;
import fr.proline.repository.util.JPAUtils;

// TODO: Auto-generated Javadoc
/**
 * DatabaseContext contains a JPA EntityManager and/or a SQL JDBC Connection.
 * <p>
 * WARNING : DatabaseContext objects must be confined inside a single Thread.
 * 
 * @author LMN
 * 
 */
public class DatabaseConnectionContext {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnectionContext.class);

    /** The m_entity manager. */
    private final EntityManager m_entityManager;

    /** The m_driver type. */
    private final DriverType m_driverType;

    /** The m_context lock. */
    private final Object m_contextLock = new Object();

    /* All mutable fields are @GuardedBy("m_contextLock") */

    /** The m_connection. */
    private Connection m_connection;

    /** The m_closed. */
    private boolean m_closed;

    /**
     * Creates a DatabaseContext instance for JPA driven Database access.
     * 
     * @param entityManager
     *            JPA EntityManager, must not be <code>null</code>.
     * @param driverType
     *            Database DriverType (H2, PostgreSQL, SQLLite).
     */
    public DatabaseConnectionContext(final EntityManager entityManager, final DriverType driverType) {
	this(entityManager, null, driverType);
    }

    /**
     * Creates a DatabaseContext instance from an Db connector for JPA EntityManager use.
     * 
     * @param dbConnector
     *            Connector to target DataBase.
     */
    public DatabaseConnectionContext(final IDatabaseConnector dbConnector) {
	this(dbConnector.getEntityManagerFactory().createEntityManager(), dbConnector.getDriverType());
    }

    /**
     * Creates a DatabaseContext instance for SQL JDBC driven Database access.
     * 
     * @param connection
     *            SQL JDBC connection.
     * @param driverType
     *            Database DriverType (H2, PostgreSQL, SQLLite).
     */
    public DatabaseConnectionContext(final Connection connection, final DriverType driverType) {
	this(null, connection, driverType);
    }

    /**
     * Full constructor for sub-classes.
     *
     * @param entityManager the entity manager
     * @param connection the connection
     * @param driverType the driver type
     */
    protected DatabaseConnectionContext(final EntityManager entityManager, final Connection connection,
	    final DriverType driverType) {

	if ((entityManager == null) && (connection == null)) {
	    throw new IllegalArgumentException("EntityManager and Connection are both null");
	}

	m_entityManager = entityManager;

	m_driverType = driverType;

	synchronized (m_contextLock) {
	    m_connection = connection;
	} // End of synchronized block on m_contextLock

    }

    /**
     * Retrieves current Database DriverType.
     * 
     * @return current Database DriverType or <code>null</code> if not set.
     */
    public EntityManager getEntityManager() {

	if (isClosed()) {
	    throw new IllegalStateException("Context ALREADY closed");
	}

	return m_entityManager;
    }

    /**
     * Retrieves the type (JPA or SQL) of this DatabaseContext.
     * 
     * @return <code>true</code> if this context is JPA or mixed JPA / SQL JDBC. Returns <code>false</code> if
     *         this context is pure SQL.
     */
    public boolean isJPA() {
	return (m_entityManager != null);
    }

    /**
     * Retrieves current EntityManager.
     * 
     * @return current EntityManager ; if <code>null</code>, Database is SQL JDBC driven.
     */
    public DriverType getDriverType() {
	return m_driverType;
    }

    /**
     * Retrieves current SQL JDBC connection.
     * 
     * @return current SQL JDBC connection ; if <code>null</code>, Database is JPA driven or there is no
     *         associated current SQL Connection.
     */
    public Connection getConnection() {
	Connection result;

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    result = m_connection;
	}

	return result;
    }
    
    /**
     * Checks if is in transaction.
     *
     * @return true, if is in transaction
     * @throws SQLException the sQL exception
     */
    public boolean isInTransaction() throws SQLException {
    
    if( this.isJPA() ) {
    	return this.getEntityManager().getTransaction().isActive();
    } else {
    	return !this.getConnection().getAutoCommit();
    }
    
    }
    
    /**
     * Begin transaction.
     *
     * @throws SQLException the sQL exception
     */
    public void beginTransaction() throws SQLException {
    
    if( this.isJPA() ) {
    	this.getEntityManager().getTransaction().begin();
    } else {
    	// TODO: handle transaction isolation levels
    	//this.getConnection().setTransactionIsolation( this.txIsolationLevel.id )
    	this.getConnection().setAutoCommit(false);
    }
    
    }
    
    /**
     * Commit transaction.
     *
     * @throws SQLException the sQL exception
     */
    public void commitTransaction() throws SQLException {
    
    if( this.isJPA() ) {
    	this.getEntityManager().getTransaction().commit();
    } else {
        this.getConnection().commit();
        this.getConnection().setAutoCommit(true);
    }
    
    }

    /**
     * Rollback transaction.
     *
     * @throws SQLException the sQL exception
     */
    public void rollbackTransaction() throws SQLException {
    
    if( this.isJPA() ) {
    	this.getEntityManager().getTransaction().rollback();
    } else {
    	this.getConnection().rollback();
    }
    
    }
    
    /**
     * Closes wrapped SQL JDBC Connection and/or JPA EntityManager.
     */
    public void close() {

	synchronized (m_contextLock) {

	    if (!m_closed) { // Close only once
		m_closed = true;

		if (m_connection != null) {
		    try {
			m_connection.close();
		    } catch (SQLException exClose) {
			LOG.error("Error closing DatabaseContext SQL Connection", exClose);
		    }
		}

		if (m_entityManager != null) {
		    try {
			m_entityManager.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing EntityManager Connection", exClose);
		    }
		}

	    } // End if (context is not already closed)

	} // End of synchronized block on m_contextLock

    }

    /**
     * Checks if is closed.
     *
     * @return true, if is closed
     */
    public boolean isClosed() {
	boolean result;

	synchronized (m_contextLock) {
	    result = m_closed;
	} // End of synchronized block on m_contextLock

	return result;
    }

    /**
     * Executes an SQL JDBC work on raw current SQL Connection or current <code>EntityManager</code>.
     *
     * @param work JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     * <code>EntityTransaction</code>.
     * @param flushEntityManager If <code>true</code> and there is no current SQL JDBC Connection, flush JPA EntityManager
     * before calling doWork.
     * @throws SQLException the sQL exception
     */
    public void doWork(final JDBCWork work, final boolean flushEntityManager) throws SQLException {

	if (work == null) {
	    throw new IllegalArgumentException("Work is null");
	}

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    final Connection contextConnection = getConnection();

	    if (contextConnection == null) {
		final EntityManager contextEntityMananger = getEntityManager();

		JPAUtils.checkEntityManager(contextEntityMananger);

		LOG.debug("Executing JDBCWork on JPA EntityManager");

		if (flushEntityManager) {
		    contextEntityMananger.flush();
		}

		final JDBCWork contextWork = new JDBCWork() {

		    public void execute(final Connection con) throws SQLException {
			m_connection = con; // Set local SQL JDBC connection in this context

			try {
			    work.execute(con);
			} finally {
			    m_connection = null;
			}

		    }

		}; // End of contextWork anonymous inner class

		JPAUtils.doWork(contextEntityMananger, contextWork);
	    } else {
		LOG.debug("Executing JDBCWork on raw JDBC Connection");

		work.execute(contextConnection);
	    }

	} // End of synchronized block on m_contextLock

    }

    /**
     * Executes an SQL JDBC work (returning a result) on raw current SQL Connection or current
     * <code>EntityManager</code>.
     *
     * @param <T> Generic type of the result of the SQL JDBC work.
     * @param returningWork JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     * <code>EntityTransaction</code>.
     * @param flushEntityManager If <code>true</code> and there is no current SQL JDBC Connection, flush JPA EntityManager
     * before calling doWork.
     * @return Result of the executed JDBC task.
     * @throws SQLException the sQL exception
     */
    public <T> T doReturningWork(final JDBCReturningWork<T> returningWork, final boolean flushEntityManager)
	    throws SQLException {

	if (returningWork == null) {
	    throw new IllegalArgumentException("ReturningWork is null");
	}

	T result = null;

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    final Connection contextConnection = getConnection();

	    if (contextConnection == null) {
		final EntityManager contextEntityMananger = getEntityManager();

		JPAUtils.checkEntityManager(contextEntityMananger);

		LOG.debug("Executing JDBCReturningWork on JPA EntityManager");

		if (flushEntityManager) {
		    contextEntityMananger.flush();
		}

		final JDBCReturningWork<T> contextReturningWork = new JDBCReturningWork<T>() {

		    public T execute(final Connection con) throws SQLException {
			T innerResult;

			m_connection = con; // Set local SQL JDBC connection in this context

			try {
			    innerResult = returningWork.execute(con);
			} finally {
			    m_connection = null;
			}

			return innerResult;
		    }

		}; // End of contextReturningWork anonymous inner class

		result = JPAUtils.doReturningWork(contextEntityMananger, contextReturningWork);
	    } else {
		LOG.debug("Executing JDBCReturningWork on raw JDBC Connection");

		result = returningWork.execute(contextConnection);
	    }

	} // End of synchronized block on m_contextLock

	return result;
    }

}
