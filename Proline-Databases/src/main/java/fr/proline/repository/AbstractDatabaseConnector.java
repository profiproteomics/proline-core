package fr.proline.repository;

import static fr.proline.util.StringUtils.LINE_SEPARATOR;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.proline.util.PropertiesUtils;

public abstract class AbstractDatabaseConnector implements IDatabaseConnector {

    public static final String PERSISTENCE_JDBC_DRIVER_KEY = "javax.persistence.jdbc.driver";
    public static final String PERSISTENCE_JDBC_URL_KEY = "javax.persistence.jdbc.url";
    public static final String PERSISTENCE_JDBC_USER_KEY = "javax.persistence.jdbc.user";
    public static final String PERSISTENCE_JDBC_PASSWORD_KEY = "javax.persistence.jdbc.password";

    public static final String HIBERNATE_DIALECT_KEY = "hibernate.dialect";

    public static final String PERSISTENCE_VALIDATION_MODE_KEY = "javax.persistence.validation.mode";
    public static final String HIBERNATE_FETCH_SIZE_KEY = "hibernate.jdbc.fetch_size";
    public static final String HIBERNATE_BATCH_SIZE_KEY = "hibernate.jdbc.batch_size";
    public static final String HIBERNATE_BATCH_VERSIONED_DATA_KEY = "hibernate.jdbc.batch_versioned_data";
    public static final String HIBERNATE_CONNECTION_RELEASE_MODE_KEY = "hibernate.connection.release_mode";
    public static final String HIBERNATE_BYTECODE_OPTIMIZER_KEY = "hibernate.bytecode.use_reflection_optimizer";

    public static final int DEFAULT_MAX_POOL_CONNECTIONS = 20;

    public static final String JDBC_SCHEME = "jdbc";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDatabaseConnector.class);

    private static final boolean DEFAULT_ORM_OPTIMIZATIONS = true;

    private final Database m_database;

    private final Map<Object, Object> m_properties;

    private final Object m_connectorLock = new Object();

    /* @GuardedBy("m_connectorLock") */
    private DataSource m_dataSource;

    /* @GuardedBy("m_connectorLock") */
    private EntityManagerFactory m_entityManagerFactory;

    /* @GuardedBy("m_connectorLock") */
    private boolean m_closed;

    /* Constructors */
    protected AbstractDatabaseConnector(final Database database, final Map<Object, Object> properties) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	m_database = database;

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	m_properties = new HashMap<Object, Object>(properties); // Protection copy
    }

    /* Public methods */
    public final Database getDatabase() {
	return m_database;
    }

    public final boolean isMemory() {
	/* Protection copy */
	final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

	return isMemory(propertiesCopy);
    }

    public final DataSource getDataSource() {

	synchronized (m_connectorLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Connector is ALREADY closed");
	    }

	    if (m_dataSource == null) {
		final Database database = getDatabase();
		/* Protection copy */
		final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

		try {
		    m_dataSource = createDataSource(database, propertiesCopy);
		} catch (Exception ex) {
		    /* Log and rethrow */
		    final String message = "Error creating DataSource for " + database;
		    LOG.error(message, ex);
		    throw new RuntimeException(message, ex);
		}

	    }

	} // End of synchronized block on m_connectorLock

	return m_dataSource;
    }

    public final EntityManagerFactory getEntityManagerFactory() {

	synchronized (m_connectorLock) {

	    if (isClosed()) {
		throw new IllegalStateException("Connector is ALREADY closed");
	    }

	    if (m_entityManagerFactory == null) {
		final Database database = getDatabase();
		/* Protection copy */
		final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

		/* Force JDBC Driver, default Hibernate dialect and default ORM optimizations */
		if (propertiesCopy.get(PERSISTENCE_JDBC_DRIVER_KEY) == null) {
		    propertiesCopy.put(PERSISTENCE_JDBC_DRIVER_KEY, getDriverType().getJdbcDriver());
		}

		try {
		    m_entityManagerFactory = createEntityManagerFactory(database, propertiesCopy,
			    DEFAULT_ORM_OPTIMIZATIONS);
		} catch (Exception ex) {
		    /* Log and rethrow */
		    final String message = "Error creating EntityManagerFactory for " + database;
		    LOG.error(message, ex);
		    throw new RuntimeException(message, ex);
		}

	    }

	} // End of synchronized block on m_connectorLock

	return m_entityManagerFactory;
    }

    public final void close() {

	synchronized (m_connectorLock) {

	    if (!m_closed) { // Close only once
		m_closed = true;

		final Database database = getDatabase();

		if (m_entityManagerFactory != null) {
		    LOG.debug("Closing EntityManagerFactory for {}", database);

		    try {
			m_entityManagerFactory.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing EntityManagerFactory for " + database, exClose);
		    }

		}

		if (m_dataSource != null) {
		    doClose(database, m_dataSource);
		}

	    } // End if (connector is not already closed)

	} // End of synchronized block on m_connectorLock

    }

    public final boolean isClosed() {
	boolean result;

	synchronized (m_connectorLock) {
	    result = m_closed;
	} // End of synchronized block on m_connectorLock

	return result;
    }

    protected boolean isMemory(final Map<Object, Object> properties) {
	return false;
    }

    /**
     * This method is called holding <code>m_connectorLock</code> implicit object <strong>lock</strong> by
     * <code>getDataSource</code>.
     * 
     * @param database
     * @param properties
     * @return
     */
    protected abstract DataSource createDataSource(final Database database,
	    final Map<Object, Object> properties);

    /**
     * This method is called holding <code>m_connectorLock</code> implicit object <strong>lock</strong> by
     * <code>getEntityManagerFactory</code>.
     * 
     * @param database
     * @param properties
     * @param ormOptimizations
     * @return
     */
    protected EntityManagerFactory createEntityManagerFactory(final Database database,
	    final Map<Object, Object> properties, final boolean ormOptimizations) {

	if (database == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	if (ormOptimizations) {
	    optimize(properties);
	}

	if (LOG.isDebugEnabled()) {
	    LOG.debug("Effective EntityManagerFactory settings for " + database + " :" + LINE_SEPARATOR
		    + PropertiesUtils.formatProperties(properties));
	}

	return Persistence.createEntityManagerFactory(database.getPersistenceUnitName(), properties);
    }

    /**
     * This method is called holding <code>m_connectorLock</code> implicit object <strong>lock</strong> by
     * <code>close</code>.
     * 
     * @param source
     */
    protected void doClose(final Database database, final DataSource source) {
    }

    private static void optimize(final Map<Object, Object> properties) {
	assert (properties != null) : "optimize() properties is null";

	if (properties.get(PERSISTENCE_VALIDATION_MODE_KEY) == null) {
	    properties.put(PERSISTENCE_VALIDATION_MODE_KEY, "none");
	}

	if (properties.get(HIBERNATE_FETCH_SIZE_KEY) == null) {
	    properties.put(HIBERNATE_FETCH_SIZE_KEY, "100");
	}

	if (properties.get(HIBERNATE_BATCH_SIZE_KEY) == null) {
	    properties.put(HIBERNATE_BATCH_SIZE_KEY, "30");
	}

	if (properties.get(HIBERNATE_BATCH_VERSIONED_DATA_KEY) == null) {
	    properties.put(HIBERNATE_BATCH_VERSIONED_DATA_KEY, "true");
	}

	if (properties.get(HIBERNATE_CONNECTION_RELEASE_MODE_KEY) == null) {
	    properties.put(HIBERNATE_CONNECTION_RELEASE_MODE_KEY, "on_close");
	}

	if (properties.get(HIBERNATE_BYTECODE_OPTIMIZER_KEY) == null) {
	    properties.put(HIBERNATE_BYTECODE_OPTIMIZER_KEY, "true");
	}

    }

}
