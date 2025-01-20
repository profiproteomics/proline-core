package fr.proline.core.service.uds

import java.util.HashSet
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.HashMap
import fr.profi.api.service.IService
import fr.proline.core.om.model.msq._
import fr.proline.core.orm.uds.{
  BiologicalGroup => UdsBiologicalGroup,
  BiologicalSample => UdsBiologicalSample,
  Dataset => UdsDataset,
  GroupSetup => UdsGroupSetup,
  MasterQuantitationChannel => UdsMasterQuantitationChannel,
  Project => UdsProject,
  QuantitationChannel => UdsQuantChannel,
  QuantitationLabel => UdsQuantLabel,
  QuantitationMethod => UdsQuantMethod,
  RatioDefinition => UdsRatioDefinition,
  Run => UdsRun,
  SampleAnalysis => UdsSampleAnalysis
}
import fr.proline.core.orm.uds.Dataset.DatasetType
import fr.proline.repository.IDataStoreConnectorFactory
import fr.profi.util.sql.getTimeAsSQLTimestamp
import fr.proline.context.IExecutionContext
import fr.proline.core.orm.uds.UserAccount
import java.sql.Connection
import fr.proline.repository.util.JDBCWork
import fr.proline.context.DatabaseConnectionContext

class UserUpdator(
  udsConnectionCtxt: DatabaseConnectionContext,
  name: String,
  newHashPassword: String,
  oldHashPassword: String
) extends UserAuthenticator(udsConnectionCtxt, name, oldHashPassword ) {

  private var _updatorErrorMsg : String = null;
  private var _updatorResult : Boolean = false;
  
 override def runService(): Boolean = {
	 _updatorResult = true
	 _updatorResult =  super.runService
	 if(!_updatorResult){
	   _updatorErrorMsg = super.getAuthenticateResultMessage
	   return _updatorResult
	 }


    var localUDSTransaction: Boolean = false
    var transacOk: Boolean = false

   try {

     // Check if a transaction is already initiated
     if (!udsConnectionCtxt.isInTransaction) {
       udsConnectionCtxt.beginTransaction()
       localUDSTransaction = true
       transacOk = false
     }

     //Old password OK, change with new password
     val jdbcUpdateWork = new JDBCWork() {
       override def execute(con: Connection) {

         val updateUserQuery = "Update user_account set password_hash = ? where login = ? "
         val pStmt = con.prepareStatement(updateUserQuery)
         pStmt.setString(1, newHashPassword)
         pStmt.setString(2, name)
         val sqlResult = pStmt.executeUpdate()
         if (sqlResult != 1) {
           _updatorErrorMsg = "Invalid number updated row (" + sqlResult + ") !";
           _updatorResult = false
         }
         pStmt.close()
       }

     } // End of jdbcWork anonymous inner class

     udsConnectionCtxt.doWork(jdbcUpdateWork, false)
     // Commit transaction if it was initiated locally
     if (localUDSTransaction) {
       udsConnectionCtxt.commitTransaction()
     }
     transacOk = true
   } finally {
     if (localUDSTransaction && !transacOk) {
       logger.info("Rollbacking UDS Db Transaction")

       try {
         udsConnectionCtxt.rollbackTransaction()
       } catch {
         case ex: Exception => logger.error("Error rollbacking MSI Db Transaction", ex)
       }

     }
   }
   return _updatorResult
  }
  
  def getUpdatorResultMessage() : String = {
    if(_updatorErrorMsg == null)
      return "Successful authentication"
     return _updatorErrorMsg
  }
  
  def getUpdateResult() : Boolean = {
      return _updatorResult
  }
  
}
