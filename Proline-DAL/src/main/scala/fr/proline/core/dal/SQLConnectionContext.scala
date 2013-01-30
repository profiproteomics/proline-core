package fr.proline.core.dal

import java.sql.Connection

import fr.profi.jdbc.easy.EasyDBC
import fr.proline.context.DatabaseConnectionContext
import fr.proline.repository.DriverType
import javax.persistence.EntityManager

class SQLConnectionContext private (entityManager: EntityManager, connection: Connection, driverType: DriverType) extends DatabaseConnectionContext(entityManager, connection, driverType) {

  val m_sqlContextLock = new AnyRef()

  /* All mutable fields are @GuardedBy("m_sqlContextLock") */

  private var m_ezDBC: EasyDBC = null

  /* SQL Constructor */
  def this(connection: Connection, driverType: DriverType) = this(null, connection, driverType)

  /* Copy constructor from another dbContext */
  def this(dbContext: DatabaseConnectionContext) = this(dbContext.getEntityManager, dbContext.getConnection, dbContext.getDriverType)

  def ezDBC(): EasyDBC = {

    m_sqlContextLock.synchronized {

      if (m_ezDBC == null) {
        val sqlConnection = getConnection

        if (sqlConnection == null) {
          throw new IllegalArgumentException("SQLConnectionContext has no a valid SQL Connection")
        }

        m_ezDBC = ProlineEzDBC(sqlConnection, getDriverType)
      }

      m_ezDBC
    } // End of synchronized block on m_sqlContextLock

  }

}