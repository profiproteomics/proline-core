package fr.proline.repository;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

public interface IDatabaseConnector {

    Database getDatabase();

    DataSource getDataSource();

    EntityManagerFactory getEntityManagerFactory();

    void close();

}
