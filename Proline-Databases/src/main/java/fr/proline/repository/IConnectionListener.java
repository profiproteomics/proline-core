package fr.proline.repository;

import java.sql.Connection;

public interface IConnectionListener {

	void connectionClosed(ConnectionWrapper connectionWrapper);

	void connectionCreated(Connection connection);

}
