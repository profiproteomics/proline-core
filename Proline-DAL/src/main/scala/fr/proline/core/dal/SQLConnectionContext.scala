package fr.proline.core.dal

import java.sql.Connection

import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.DriverType
import javax.persistence.EntityManager

class SQLConnectionContext private (entityManager: EntityManager, connection: Connection, driverType: DriverType) extends DatabaseConnectionContext(entityManager, connection, driverType) {

  /* SQL Constructor */
  def this(connection: Connection, driverType: DriverType) = this(null, connection, driverType)

  /* Copy constructor from another dbContext */
  def this(dbContext: DatabaseConnectionContext) = this(dbContext.getEntityManager, dbContext.getConnection, dbContext.getDriverType)

  lazy val ezDBC = {
    val sqlConnection = getConnection

    if (sqlConnection == null) {
      throw new IllegalArgumentException("SQLConnectionContext has no a valid SQL Connection")
    }

    ProlineEzDBC(sqlConnection, getDriverType)
  }

}
