package fr.proline.core.service.uds

import java.util.HashSet
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.mutable.HashMap
import fr.proline.api.service.IService
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
import fr.proline.util.sql.getTimeAsSQLTimestamp
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
) extends IService {

  private var _errorMsg : String = null;
  private var _serviceResult : Boolean = false;
  
  def runService(): Boolean = {
     var oldPassword: String = null
	 _serviceResult = true
	 
     val jdbcWork = new JDBCWork() {
      override def execute(con: Connection) {

        val getUserQuery = "Select password_hash FROM user_account where login =? 	"
        val pStmt = con.prepareStatement(getUserQuery)
        pStmt.setString(1, name)
        val sqlResultSet = pStmt.executeQuery()
        if (sqlResultSet.next)
          oldPassword = sqlResultSet.getString(1)
      	else {
           _errorMsg = "Specified user is unknown";  
           _serviceResult = false
      	}
        pStmt.close()
      }

    } // End of jdbcWork anonymous inner class    	 

    udsConnectionCtxt.doWork(jdbcWork, false)
    if(_errorMsg != null)
      return _serviceResult

    if(oldPassword == null) {
      _errorMsg = "Invalid password found for user ";
      _serviceResult = false
      return _serviceResult
    }
    
    if(!oldPassword.equals(oldHashPassword)){
      _errorMsg = "Invalid password entered for user ";
      _serviceResult = false
      return _serviceResult      
    }
    
    //Old password OK, change with new password
     val jdbcUpdateWork = new JDBCWork() {
      override def execute(con: Connection) {

        val updateUserQuery = "Update user_account set password_hash = ? where login = ? "
        val pStmt = con.prepareStatement(updateUserQuery)
        pStmt.setString(1, newHashPassword)
        pStmt.setString(2, name)
        val sqlResult = pStmt.executeUpdate()
        if (sqlResult != 1 ){
           _errorMsg = "Invalid number updated row ("+sqlResult+") !";  
           _serviceResult = false
        }
        pStmt.close()
      }

    } // End of jdbcWork anonymous inner class
     
     udsConnectionCtxt.doWork(jdbcUpdateWork, false)
    if(! _serviceResult)
      return false
    true
  }
  
  def getErrorMessage() : String = {
    if(_errorMsg == null)
      return "Successful authentication"
     return _errorMsg
  }
  
  def getUpdateResult() : Boolean = {
      return _serviceResult
  }
  
}
