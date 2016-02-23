package fr.proline.core.service.msi

import org.junit.Assert._
import org.junit.Test

import com.typesafe.scalalogging.StrictLogging

import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.core.dbunit.DbUnitResultFileUtils
import fr.proline.core.dbunit.STR_F136482_CTD
import fr.proline.core.dbunit.TLS_F027737_MTD_no_varmod
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.repository.DriverType

object ResultSetsMergerTest extends AbstractEmptyDatastoreTestCase with StrictLogging {

  val driverType = DriverType.H2
  // For manual postgres test !! If use, should comment all loadDataSet from setUp and AbstractRFImporterTest_.setUp
  //   val driverType = DriverType.POSTGRESQL
  val useJPA = false
  
/*
  @Before
  @throws(classOf[Exception])
  override def setUp() = {
    super.setUp()

    // TODO: why not put this in AbstractDatasetImporterTest_ ???
    udsDBTestCase.loadDataSet("/dbunit/datasets/uds-db_init_dataset.xml")
    
    // Instantiate an ExecutionContext
    val execContext = buildExecContext()
    
    // TODO: add externalDbs programmatically
    
    try {
      execContext.closeAll()
    } catch {
      case exClose: Exception => logger.error("Error closing ExecutionContext", exClose)
    }

    logger.info("UDS db succesfully initialized")
  }

  @After
  override def tearDown() {
    super.tearDown()
  }
  */
  
}
  
class ResultSetsMergerTest extends StrictLogging {
  
  val sqlExecutionContext = ResultSetsMergerTest.executionContext
  val dsConnectorFactoryForTest = ResultSetsMergerTest.dsConnectorFactoryForTest
  
  val rs1 = DbUnitResultFileUtils.importDbUnitResultFile(STR_F136482_CTD, sqlExecutionContext)
  val rs2 = DbUnitResultFileUtils.importDbUnitResultFile(TLS_F027737_MTD_no_varmod, sqlExecutionContext)

  @Test
  def testMergeOneRS() {
    
    val rsProvider = new SQLResultSetProvider(
      sqlExecutionContext.getMSIDbConnectionContext,
      sqlExecutionContext.getPSDbConnectionContext,
      sqlExecutionContext.getUDSDbConnectionContext
    )

    var localJPAExecutionContext: IExecutionContext = null

    try {
      
      // TODO: allow to distinguish between the input data (ID VS RS) and the fact to store or not the RSM
      val rsMerger = new ResultSetMerger(sqlExecutionContext, Some(Seq(rs1.id,rs1.id)), None, None)

      val result = rsMerger.runService
      assertTrue("ResultSet merger result", result)
      logger.info("End Run ResultSetMerger Service, merge same RS twice, in Test")

      val tRSM = rsMerger.mergedResultSet
      assertNotNull("Merged TARGET ResultSet", tRSM)

      val mergedDecoyRS = tRSM.decoyResultSet
      assertTrue("Merged DECOY ResultSet is present", (mergedDecoyRS != null) && mergedDecoyRS.isDefined)

      /* Try to reload merged ResultSet with JPA */
      val mergedRSId = tRSM.id

      localJPAExecutionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true)

      val rsProvider = new ORMResultSetProvider(localJPAExecutionContext.getMSIDbConnectionContext, localJPAExecutionContext.getPSDbConnectionContext, localJPAExecutionContext.getPDIDbConnectionContext)

      val optionalMergedRS = rsProvider.getResultSet(mergedRSId)
      assertTrue("Reloaded Merged ResultSet", (optionalMergedRS != null) && optionalMergedRS.isDefined)

      val optionalMergedDecoyRS = optionalMergedRS.get.decoyResultSet
      assertTrue("Reloaded Merged DECOY ResultSet", (optionalMergedDecoyRS != null) && optionalMergedDecoyRS.isDefined)
    } finally {

      if (localJPAExecutionContext != null) {
        try {
          localJPAExecutionContext.closeAll()
        } catch {
          case exClose: Exception => logger.error("Error closing local JPA ExecutionContext", exClose)
        }
      }

    }

  }

  @Test
  def testMergeTwoRS() {

    var localJPAExecutionContext: IExecutionContext = null

    try {
      val rsMerger = new ResultSetMerger(sqlExecutionContext, Some( Seq(rs1.id,rs2.id) ), None, None )
      // val rsMerger = new ResultSetMerger(sqlExecutionContext, None, Some(loadResultSetsWithDecoy(rzProvider, rsIds)))

      val result = rsMerger.runService
      assertTrue("ResultSet merger result", result)
      logger.info("End Run ResultSetMerger Service, merge two different RS twice, in Test")

      val tRSM = rsMerger.mergedResultSet
      assertNotNull("Merged TARGET ResultSet", tRSM)

      val mergedDecoyRSId = tRSM.getDecoyResultSetId
      assertTrue("Merged DECOY ResultSet is present", mergedDecoyRSId > 0)

      /* Try to reload merged ResultSet with JPA */
      val mergedRSId = tRSM.id

      localJPAExecutionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true)
      val msiEM = localJPAExecutionContext.getMSIDbConnectionContext().getEntityManager()
      val msiRS  = msiEM.find(classOf[fr.proline.core.orm.msi.ResultSet], mergedRSId)
      
      assertTrue("Reloaded Merged ORM ResultSet", msiRS != null)

      val msiDecoyRS = msiRS.getDecoyResultSet()
      assertTrue("Reloaded Merged DECOY ORM ResultSet", msiDecoyRS != null)
      
      assertTrue("Merged ResultSet linked to child", msiRS.getChildren() != null && !msiRS.getChildren().isEmpty())
      
    } finally {

      if (localJPAExecutionContext != null) {
        try {
          localJPAExecutionContext.closeAll()
        } catch {
          case exClose: Exception => logger.error("Error closing local JPA ExecutionContext", exClose)
        }
      }

    }

  }

}