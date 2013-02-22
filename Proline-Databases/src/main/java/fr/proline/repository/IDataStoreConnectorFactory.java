package fr.proline.repository;

public interface IDataStoreConnectorFactory {

    boolean isInitialized();

    IDatabaseConnector getUdsDbConnector();

    IDatabaseConnector getPdiDbConnector();

    IDatabaseConnector getPsDbConnector();

    IDatabaseConnector getMsiDbConnector(int projectId);

    IDatabaseConnector getLcMsDbConnector(int projectId);

    void closeAll();

}