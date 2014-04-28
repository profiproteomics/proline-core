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

class AuthenticationUser(
  udsConnectionCtxt: DatabaseConnectionContext,
  name: String,
  hashPassword: String
) extends IService {

  private var _errorMsg : String = null;
  
  def runService(): Boolean = {
     var password: String = null

     val jdbcWork = new JDBCWork() {
      override def execute(con: Connection) {

        val getUserQuery = "Select password_hash FROM user_account where login =? 	"
        val pStmt = con.prepareStatement(getUserQuery)
        pStmt.setString(1, name)
        val sqlResultSet = pStmt.executeQuery()
        if (sqlResultSet.next)
          password = sqlResultSet.getString(1)
      	else {
           _errorMsg = "Specified user is unknown";  
      	}
        pStmt.close()
      }

    } // End of jdbcWork anonymous inner class    	 

    udsConnectionCtxt.doWork(jdbcWork, false)
    if(_errorMsg != null)
      return false

    if(password == null) {
      _errorMsg = "Invalid password found for user ";
      return false
    }
    
    if(!password.equals(hashPassword)){
      _errorMsg = "Invalid password entered for user ";
      return false      
    }
    
    true
  }
  
  def getErrorMessage() : String = {
    if(_errorMsg == null)
      return "Successful authentication"
     return _errorMsg
  }

  
}
