package fr.proline.core.om.provider.msi.impl

import java.sql.Connection

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import com.typesafe.scalalogging.StrictLogging

import fr.proline.context.IExecutionContext
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.dal.BuildLazyExecutionContext
import fr.proline.repository.DriverType
import fr.proline.repository.util.JDBCWork

@Test
class ORMResultSetProviderTest extends AbstractMultipleDBTestCase with StrictLogging  {
  
  val driverType = DriverType.H2
  val fileName = "STR_F063442_F122817_MergedRSMs"
  val targetRSMId: Long = 33


  var executionContext: IExecutionContext = null
  
  @Before
  @throws(classOf[Exception])
  def setUp() = {
    
    logger.info("Initializing Dbs")

    super.initDBsDBManagement(DriverType.H2)
    
       //Load Data
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/ps-db.xml")
//    msiDBTestCase.loadCompositeDataSet(Array("/dbunit_samples/" + fileName + "/msi-db.xml", "/dbunit_samples/STR_F063442_F122817_MergedRSMs_Relation.xml"))
    msiDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/msi-db.xml")
    udsDBTestCase.loadDataSet("/dbunit_samples/" + fileName + "/uds-db.xml")
    logger.info("PDI, PS, MSI and UDS dbs succesfully initialized !")

    executionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true) // Full JPA    
    
  }
  
  
  @After
  override def tearDown() {
    if (executionContext != null) executionContext.closeAll()
    super.tearDown()
  }
  
    private def getLeafChildsID(rsId: Long): Seq[Long] = {
    var allRSIds = Seq.newBuilder[Long]

    val jdbcWork = new JDBCWork() {

      override def execute(con: Connection) {

        val stmt = con.prepareStatement("select child_result_set_id from result_set_relation where result_set_relation.parent_result_set_id = ?")
        stmt.setLong(1, rsId)
        val sqlResultSet = stmt.executeQuery()
        var childDefined = false
        while (sqlResultSet.next) {
          childDefined = true
          val nextChildId = sqlResultSet.getInt(1)
          allRSIds ++= getLeafChildsID(nextChildId)
        }
        if (!childDefined)
          allRSIds += rsId
        stmt.close()
      } // End of jdbcWork anonymous inner class
    }
    executionContext.getMSIDbConnectionContext().doWork(jdbcWork, false)

    allRSIds.result
  }
   
   @Test
  def getReadResultSet() = {
      var leavesRsIds: Seq[Long] = getLeafChildsID(targetRSMId)
      Assert.assertEquals(2,leavesRsIds.length)
      
	  val rsProvider=  new ORMResultSetProvider(executionContext.getMSIDbConnectionContext(),executionContext.getPSDbConnectionContext(), executionContext.getPDIDbConnectionContext)
      
	  val rsID= leavesRsIds(0)
	  val resultRS = rsProvider.getResultSet(rsID)
	  Assert.assertNotNull(resultRS)
	  Assert.assertTrue(resultRS.isDefined)

  }
   

}