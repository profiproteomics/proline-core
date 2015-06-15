package fr.proline.context;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.SQLException;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.repository.DriverType;
import fr.proline.repository.IDatabaseConnector;
import fr.proline.repository.ProlineDatabaseType;
import fr.proline.repository.util.JDBCReturningWork;
import fr.proline.repository.util.JDBCWork;
import fr.proline.repository.util.JPAUtils;

/**
 * DatabaseContext contains a JPA EntityManager and/or a SQL JDBC Connection.
 * <p>
 * WARNING : DatabaseContext objects must be confined inside a single Thread.
 * 
 * @author LMN
 * 
 */
public class DatabaseConnectionContext implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConnectionContext.class);

    /*
     * TODO LMN Remove fakeException generation and finalize() implementation when all Db Connections are
     * closed correctly in Proline
     */
    private static final boolean DEBUG_LEAK = true;

    private final Exception m_fakeException;

    private final EntityManager m_entityManager;

    private final ProlineDatabaseType m_prolineDatabaseType;

    private final DriverType m_driverType;

    private final Object m_contextLock = new Object();

    /* All mutable fields are @GuardedBy("m_contextLock") */

    private Connection m_connection;
    
    private boolean m_closeConnection = false;

    private boolean m_closed;

    /**
     * Creates a DatabaseContext instance for JPA driven Database access.
     * 
     * @param entityManager
     *            JPA EntityManager, must not be <code>null</code>.
     * @param driverType
     *            Database DriverType (H2, PostgreSQL, SQLLite).
     */
    public DatabaseConnectionContext(final EntityManager entityManager,
	    final ProlineDatabaseType prolineDatabaseType, final DriverType driverType) {
	this(entityManager, null, prolineDatabaseType, driverType);
    }

    /**
     * Creates a DatabaseContext instance from an Db connector for JPA EntityManager use.
     * 
     * @param dbConnector
     *            Connector to target DataBase.
     */
    public DatabaseConnectionContext(final IDatabaseConnector dbConnector) {
	this(dbConnector.getEntityManagerFactory().createEntityManager(), dbConnector
		.getProlineDatabaseType(), dbConnector.getDriverType());
    }

    /**
     * Creates a DatabaseContext instance for SQL JDBC driven Database access.
     * 
     * @param connection
     *            SQL JDBC connection.
     * @param driverType
     *            Database DriverType (H2, PostgreSQL, SQLLite).
     */
    public DatabaseConnectionContext(final Connection connection,
	    final ProlineDatabaseType prolineDatabaseType, final DriverType driverType) {
	this(null, connection, prolineDatabaseType, driverType);
    }

    /**
     * Full constructor for sub-classes.
     * 
     * @param entityManager
     *            the entity manager
     * @param connection
     *            the connection
     * @param driverType
     *            the driver type
     */
    protected DatabaseConnectionContext(final EntityManager entityManager, final Connection connection,
	    final ProlineDatabaseType prolineDatabaseType, final DriverType driverType) {

	if ((entityManager == null) && (connection == null)) {
	    throw new IllegalArgumentException("EntityManager and Connection are both null");
	}

	if (DEBUG_LEAK) {
	    m_fakeException = new RuntimeException(
		    "_FakeException_ DatabaseConnectionContext instance creation");
	} else {
	    m_fakeException = null;
	}

	m_entityManager = entityManager;

	m_prolineDatabaseType = prolineDatabaseType;

	m_driverType = driverType;

	synchronized (m_contextLock) {
	    m_connection = connection;
	} // End of synchronized block on m_contextLock

    }

    /**
     * Retrieves current EntityManager.
     * 
     * @return current EntityManager ; if <code>null</code>, Database is SQL JDBC driven.
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
	return (getEntityManager() != null);
    }

    /**
     * Retrieves current ProlineDatabaseType (UDS, PDI, PS, MSI...).
     * 
     * @return current ProlineDatabaseType or <code>null</code> if not set.
     */
    public ProlineDatabaseType getProlineDatabaseType() {
	return m_prolineDatabaseType;
    }

    /**
     * Retrieves current Database DriverType (PostgreSQL, h2, SQLite...).
     * 
     * @return current Database DriverType or <code>null</code> if not set.
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
	} // End of synchronized block on m_contextLock

	return result;
    }
    
    /**
     * Retrieves current Database DriverType (PostgreSQL, h2, SQLite...).
     * 
     * @return current Database DriverType or <code>null</code> if not set.
     */
    public DatabaseConnectionContext setCloseConnection(boolean closeConnection) {
    	this.m_closeConnection = closeConnection;
    	return this;
    }

    /**
     * Checks if is in transaction.
     * 
     * @return true, if is in transaction
     * @throws SQLException
     *             the sQL exception
     */
    public boolean isInTransaction() throws SQLException {
	boolean result = false;

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    if (isJPA()) {
		final EntityTransaction currentTransaction = getEntityManager().getTransaction();

		result = ((currentTransaction != null) && currentTransaction.isActive());
	    } else {
		final Connection contextConnection = getConnection();

		if ((contextConnection != null) && !contextConnection.isClosed()) {
		    result = !contextConnection.getAutoCommit();
		}

	    }

	} // End of synchronized block on m_contextLock

	if (LOG.isTraceEnabled()) {
	    LOG.trace("{} isInTransaction : {}", getProlineDatabaseTypeString(), result);
	}

	return result;
    }

    /**
     * Begin transaction.
     * 
     * @throws SQLException
     *             the sQL exception
     */
    public void beginTransaction() throws SQLException {

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    if (LOG.isDebugEnabled()) {
		LOG.debug("{} Begin Transaction from DatabaseConnectionContext",
			getProlineDatabaseTypeString());
	    }

	    if (isJPA()) {
		getEntityManager().getTransaction().begin();
	    } else {
		// TODO handle transaction isolation levels
		// getConnection().setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE)
		getConnection().setAutoCommit(false);
	    }

	    if (LOG.isDebugEnabled()) {
		final Connection contextConnection = getConnection();

		if (contextConnection != null) {
		    LOG.debug("{} SQL TransactionIsolation \"{}\"", getProlineDatabaseTypeString(),
			    formatTransactionIsolationLevel(contextConnection.getTransactionIsolation()));
		}

	    }

	} // End of synchronized block on m_contextLock

    }

    /**
     * Commit transaction.
     * 
     * @throws SQLException
     *             the sQL exception
     */
    public void commitTransaction() throws SQLException {

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    if (LOG.isDebugEnabled()) {
		LOG.debug("{} Commit Transaction from DatabaseConnectionContext",
			getProlineDatabaseTypeString());
	    }

	    if (isJPA()) {
		getEntityManager().getTransaction().commit();
	    } else {
		final Connection contextConnection = getConnection();

		contextConnection.commit();
		contextConnection.setAutoCommit(true);
	    }

	} // End of synchronized block on m_contextLock

    }

    /**
     * Rollback transaction.
     * 
     * @throws SQLException
     *             the sQL exception
     */
    public void rollbackTransaction() throws SQLException {

	synchronized (m_contextLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Context ALREADY closed");
	    }

	    if (LOG.isDebugEnabled()) {
		LOG.debug("{} Rollback Transaction from DatabaseConnectionContext", getProlineDatabaseTypeString());
	    }

	    if (getDriverType() == DriverType.SQLITE) {
		    // FIXME Rollback is not useful for SQLite and has locking issue
		    // http://www.sqlite.org/lang_transaction.html
		    LOG.warn("Rollbacking Transaction with SQLITE Database does NOTHING");
	    } else {

		if (isJPA()) {
			getEntityManager().getTransaction().rollback();
		} else {
		    getConnection().rollback();
		}

	    }

	} // End of synchronized block on m_contextLock

    }

    /**
     * Closes wrapped SQL JDBC Connection and/or JPA EntityManager.
     */
    public void close() {
	/* Regular way to close a Db Connection Context */
	doClose(false);
    }

    // TODO LMN Remove finalize() implementation when all Db Connections are closed correctly in Proline
    @Override
    protected void finalize() throws Throwable {

	try {

	    if (DEBUG_LEAK) {
		try {
		    /* It is not the regular way to close a Db Connection Context ! Just the last security */
		    doClose(true);
		} catch (Exception ex) {
		    LOG.error("Error closing " + getProlineDatabaseTypeString() + " Context", ex);
		}
	    }

	} finally {
	    super.finalize();
	}

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
     * @param work
     *            JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     *            <code>EntityTransaction</code>.
     * @param flushEntityManager
     *            If <code>true</code> and there is no current SQL JDBC Connection, flush JPA EntityManager
     *            before calling doWork.
     * @throws SQLException
     *             the sQL exception
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

		if (LOG.isTraceEnabled()) {
		    LOG.trace("{} Executing JDBCWork on JPA EntityManager, flushEntityManager: {}",
			    getProlineDatabaseTypeString(), flushEntityManager);
		}

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

		if (LOG.isTraceEnabled()) {
		    LOG.trace("{} Executing JDBCWork on raw JDBC Connection", getProlineDatabaseTypeString());
		}

		work.execute(contextConnection);
	    }

	} // End of synchronized block on m_contextLock

    }

    /**
     * Executes an SQL JDBC work (returning a result) on raw current SQL Connection or current
     * <code>EntityManager</code>.
     * 
     * @param <T>
     *            Generic type of the result of the SQL JDBC work.
     * @param returningWork
     *            JDBC task to be executed by given <code>EntityManager</code> instance, eventually within its
     *            <code>EntityTransaction</code>.
     * @param flushEntityManager
     *            If <code>true</code> and there is no current SQL JDBC Connection, flush JPA EntityManager
     *            before calling doWork.
     * @return Result of the executed JDBC task.
     * @throws SQLException
     *             the sQL exception
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

		if (LOG.isTraceEnabled()) {
		    LOG.trace("{} Executing JDBCReturningWork on JPA EntityManager, flushEntityManager: {}",
			    getProlineDatabaseTypeString(), flushEntityManager);
		}

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

		if (LOG.isTraceEnabled()) {
		    LOG.trace("{} Executing JDBCReturningWork on raw JDBC Connection",
			    getProlineDatabaseTypeString());
		}

		result = returningWork.execute(contextConnection);
	    }

	} // End of synchronized block on m_contextLock

	return result;
    }

    private String getProlineDatabaseTypeString() {
	String result = null;

	final ProlineDatabaseType dbType = getProlineDatabaseType();

	if (dbType == null) {
	    result = "Unknown Db";
	} else {
	    result = dbType + " Db";
	}

	return result;
    }

    private void doClose(final boolean fromFinalize) {

	synchronized (m_contextLock) {

	    if (!m_closed) { // Close only once
		m_closed = true;

		final String prolineDbType = getProlineDatabaseTypeString();

		if (m_connection != null && m_closeConnection) {
			
			if (fromFinalize) {
			    LOG.warn("Trying to close ORPHAN {} Context from finalize", prolineDbType, m_fakeException);
			}
			
		    try {
		    	m_connection.close();
		    } catch (SQLException exClose) {
		    	LOG.error("Error closing DatabaseConnectionContext SQL Connection for "+ prolineDbType, exClose);
		    }
		} // End if (m_connection is not null)

		else if (m_entityManager != null) {
			
			if (fromFinalize) {
			    LOG.warn("Trying to close ORPHAN {} Context from finalize", prolineDbType, m_fakeException);
			}

		    /* Paranoiac rollback then close */

		    try {
			final EntityTransaction currentTransaction = m_entityManager.getTransaction();

			if ((currentTransaction != null) && currentTransaction.isActive()) {
			    LOG.info(
				    "{} Rollback EntityTransaction from DatabaseConnectionContext.doClose()",
				    prolineDbType
				);

			    try {
				currentTransaction.rollback();
			    } catch (Exception ex) {
				LOG.error(
					"Error rollbacking DatabaseConnectionContext EntityTransaction for " + prolineDbType, ex
				);
			    }

			}

		    } finally {

			try {

			    /* Check EM open state only on finalize (not on explicit close call) */
			    if (!fromFinalize || m_entityManager.isOpen()) {
				m_entityManager.close();
			    }

			} catch (Exception exClose) {
			    LOG.error("Error closing DatabaseConnectionContext EntityManager for "
				    + prolineDbType, exClose);
			}

		    }

		} // End if (m_entityManager is not null)

	    } // End if (context is not already closed)

	} // End of synchronized block on m_contextLock

    }

    private static String formatTransactionIsolationLevel(final int transactionIsolationLevel) {
	String result = null;

	switch (transactionIsolationLevel) {
	case Connection.TRANSACTION_NONE:
	    result = "none";
	    break;

	case Connection.TRANSACTION_READ_COMMITTED:
	    result = "read committed";
	    break;

	case Connection.TRANSACTION_READ_UNCOMMITTED:
	    result = "read uncommitted";
	    break;

	case Connection.TRANSACTION_REPEATABLE_READ:
	    result = "repeatable read";
	    break;

	case Connection.TRANSACTION_SERIALIZABLE:
	    result = "serializable";
	    break;

	default:
	    result = "?";

	}

	return result;
    }

}
