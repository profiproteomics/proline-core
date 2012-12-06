package fr.proline.core.dal


import com.weiglewilczek.slf4s.Logging
import fr.proline.repository.IDatabaseConnector
import fr.proline.core.orm.util.DatabaseManager

class DatabaseManagementTestCase(val udsDBConnector: IDatabaseConnector,/*
                                 val testPdiDBConnector: IDatabaseConnector,
                                 val testPsDBConnector: IDatabaseConnector, */
                                 val msiDBConnector: IDatabaseConnector ) extends Logging {

  val dbManager = DatabaseManager.getInstance()
  dbManager.initialize( udsDBConnector )

  //lazy val pdiDBConnector: IDatabaseConnector = testPdiDBConnector
   
  //lazy val psDBConnector: IDatabaseConnector = testPsDBConnector
   
  def getCurrentMsiConnector( ): IDatabaseConnector = {
    msiDBConnector
  }
}