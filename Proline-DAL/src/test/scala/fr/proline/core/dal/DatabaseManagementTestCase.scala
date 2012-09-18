package fr.proline.core.dal

import com.weiglewilczek.slf4s.Logging

import fr.proline.repository.DatabaseConnector

class DatabaseManagementTestCase(override val udsDBConnector : DatabaseConnector, val testPdiDBConnector: DatabaseConnector, val testPsDBConnector: DatabaseConnector,  val msiDBConnector: DatabaseConnector ) extends DatabaseManagement(udsDBConnector )  with Logging {

   override lazy val pdiDBConnector: DatabaseConnector = testPdiDBConnector
   
   override lazy val psDBConnector: DatabaseConnector = testPsDBConnector
   
   def getCurrentMsiConnector( ): DatabaseConnector = {
     msiDBConnector
   }
}