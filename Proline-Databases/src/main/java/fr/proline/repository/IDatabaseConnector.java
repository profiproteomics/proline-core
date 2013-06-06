package fr.proline.repository;

import java.util.Map;

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
public interface IDatabaseConnector {

    ProlineDatabaseType getProlineDatabaseType();

    DriverType getDriverType();

    boolean isMemory();

    DataSource getDataSource();

    EntityManagerFactory getEntityManagerFactory();

    void close();

    boolean isClosed();
    
    void setAdditionalProperties(Map<Object, Object> params);

}
