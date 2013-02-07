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
import fr.proline.util.StringUtils;

public abstract class AbstractDatabaseConnector implements IDatabaseConnector {

    /* Constants */
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

    private static final Map<String, Integer> CONNECTOR_INSTANCES = new HashMap<String, Integer>();

    private static final boolean DEFAULT_ORM_OPTIMIZATIONS = true;

    /* Instance variables */
    private final ProlineDatabaseType m_prolineDbType;

    private final Map<Object, Object> m_properties;

    private final String m_ident;

    private final Object m_connectorLock = new Object();

    /* All mutable fields are @GuardedBy("m_connectorLock") */

    private DataSource m_dataSource;

    private EntityManagerFactory m_entityManagerFactory;

    private boolean m_closed;

    /* Constructors */
    protected AbstractDatabaseConnector(final ProlineDatabaseType prolineDbType,
	    final Map<Object, Object> properties) {

	if (prolineDbType == null) {
	    throw new IllegalArgumentException("ProlineDbType is null");
	}

	m_prolineDbType = prolineDbType;

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	m_properties = new HashMap<Object, Object>(properties); // Protection copy

	final StringBuilder identBuffer = new StringBuilder(prolineDbType.getPersistenceUnitName());

	final String jdbcURL = PropertiesUtils.getProperty(m_properties, PERSISTENCE_JDBC_URL_KEY);

	if (!StringUtils.isEmpty(jdbcURL)) {
	    identBuffer.append('_').append(jdbcURL);
	}

	m_ident = identBuffer.toString();

	final int connectorInstancesCount = checkConnectorInstances(m_ident);

	if (connectorInstancesCount > 1) {
	    final Exception ex = new Exception("Multiple DatabaseConnector");

	    LOG.error("There are " + connectorInstancesCount + " DatabaseConnector instances for [" + m_ident
		    + ']', ex);
	}

    }

    /* Public methods */
    public final ProlineDatabaseType getProlineDatabaseType() {
	return m_prolineDbType;
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
		final ProlineDatabaseType prolineDbType = getProlineDatabaseType();
		/* Protection copy */
		final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

		try {
		    m_dataSource = createDataSource(m_ident, propertiesCopy);
		} catch (Exception ex) {
		    /* Log and re-throw */
		    final String message = "Error creating DataSource for " + prolineDbType;
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
		final ProlineDatabaseType prolineDbType = getProlineDatabaseType();
		/* Protection copy */
		final Map<Object, Object> propertiesCopy = new HashMap<Object, Object>(m_properties);

		/* Force JDBC Driver, default Hibernate dialect and default ORM optimizations */
		if (propertiesCopy.get(PERSISTENCE_JDBC_DRIVER_KEY) == null) {
		    propertiesCopy.put(PERSISTENCE_JDBC_DRIVER_KEY, getDriverType().getJdbcDriver());
		}

		try {
		    m_entityManagerFactory = createEntityManagerFactory(getProlineDatabaseType(),
			    propertiesCopy, DEFAULT_ORM_OPTIMIZATIONS);
		} catch (Exception ex) {
		    /* Log and re-throw */
		    final String message = "Error creating EntityManagerFactory for " + prolineDbType;
		    LOG.error(message, ex);
		    throw new RuntimeException(message, ex);
		}

	    }

	} // End of synchronized block on m_connectorLock

	return m_entityManagerFactory;
    }

    @Override
    public String toString() {
	return getClass().getSimpleName() + ' ' + m_ident;
    }

    public final void close() {

	synchronized (m_connectorLock) {

	    if (!m_closed) { // Close only once
		m_closed = true;

		LOG.warn("Closing DatabaseConnector does not close already retrieved EntityManager and SQL JDBC Connection resources");

		if (m_entityManagerFactory != null) {
		    LOG.debug("Closing EntityManagerFactory for {}", m_ident);

		    try {
			m_entityManagerFactory.close();
		    } catch (Exception exClose) {
			LOG.error("Error closing EntityManagerFactory for " + m_ident, exClose);
		    }

		}

		if (m_dataSource != null) {
		    doClose(m_ident, m_dataSource);
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
    protected abstract DataSource createDataSource(final String ident, final Map<Object, Object> properties);

    /**
     * This method is called holding <code>m_connectorLock</code> implicit object <strong>lock</strong> by
     * <code>getEntityManagerFactory</code>.
     * 
     * @param database
     * @param properties
     * @param ormOptimizations
     * @return
     */
    protected EntityManagerFactory createEntityManagerFactory(final ProlineDatabaseType prolineDbType,
	    final Map<Object, Object> properties, final boolean ormOptimizations) {

	if (prolineDbType == null) {
	    throw new IllegalArgumentException("Database is null");
	}

	if (properties == null) {
	    throw new IllegalArgumentException("Properties Map is null");
	}

	if (ormOptimizations) {
	    optimize(properties);
	}

	if (LOG.isDebugEnabled()) {
	    LOG.debug("Effective EntityManagerFactory settings for " + prolineDbType + " :" + LINE_SEPARATOR
		    + PropertiesUtils.formatProperties(properties));
	}

	return Persistence.createEntityManagerFactory(prolineDbType.getPersistenceUnitName(), properties);
    }

    /**
     * This method is called holding <code>m_connectorLock</code> implicit object <strong>lock</strong> by
     * <code>close</code>.
     * 
     * @param source
     */
    protected void doClose(final String ident, final DataSource source) {
    }

    private static int checkConnectorInstances(final String ident) {
	assert (!StringUtils.isEmpty(ident)) : "checkConnectorInstance() invalid ident";

	int result = 0;

	synchronized (CONNECTOR_INSTANCES) {
	    final Integer oldCount = CONNECTOR_INSTANCES.get(ident);

	    if (oldCount == null) {
		result = 1;
	    } else {
		result = oldCount.intValue() + 1;
	    }

	    CONNECTOR_INSTANCES.put(ident, Integer.valueOf(result));
	} // End of synchronized block on CONNECTOR_INSTANCES

	return result;
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
