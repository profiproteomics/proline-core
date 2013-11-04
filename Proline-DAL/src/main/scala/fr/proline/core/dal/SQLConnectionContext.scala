package fr.proline.core.dal

import java.sql.Connection
import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.DriverType
import javax.persistence.EntityManager
import fr.proline.repository.ProlineDatabaseType

@deprecated("try to use a plain DatabaseConnectionContext instead", "0.0.9")
class SQLConnectionContext private (entityManager: EntityManager, connection: Connection, prolineDatabaseType: ProlineDatabaseType, driverType: DriverType)
  extends DatabaseConnectionContext(entityManager, connection, prolineDatabaseType, driverType) {

  /* SQL Constructor */
  def this(connection: Connection, prolineDatabaseType: ProlineDatabaseType, driverType: DriverType) = this(null, connection, prolineDatabaseType, driverType)

  /* Copy constructor from another dbContext */
  def this(dbContext: DatabaseConnectionContext) = this(dbContext.getEntityManager, dbContext.getConnection, dbContext.getProlineDatabaseType, dbContext.getDriverType)

  lazy val ezDBC = {
    val sqlConnection = getConnection

    if (sqlConnection == null) {
      throw new IllegalArgumentException("SQLConnectionContext has no a valid SQL Connection")
    }

    ProlineEzDBC(sqlConnection, getDriverType)
  }

}
