package fr.proline.core.service.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.core.dbunit.{DbUnitResultFileUtils, STR_F136482_CTD, TLS_F027737_MTD_no_varmod}
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.repository.DriverType
import org.junit.Assert._
import org.junit.Test

object ResultSetsMergerTest extends AbstractDatastoreTestCase with StrictLogging {

  val driverType = DriverType.H2
  val useJPA = true

}

@Test
/**
  * VDS FIXME :===> Suite au Merge, le probleme a disparu... si ca se confirme, Commentaire A SUPPRIMER.
  * While init DBs  => Error getting PtmDef for used_PTM :
  *  java.util.NoSuchElementException: key not found: when searching PTM_Classification for used_PTM : classification = ""
  *  JPAPtmDefinitionWriter.convertPtmDefinitionToMsiPtmSpecificity(JPAPtmDefinitionStorer.scala:196)
  */
class ResultSetsMergerTest extends StrictLogging {
  
  val sqlExecutionContext: IExecutionContext = ResultSetsMergerTest.executionContext
  val dsConnectorFactoryForTest: DataStoreConnectorFactoryForTest = ResultSetsMergerTest.dsConnectorFactoryForTest
  
  val rs1: ResultSet = DbUnitResultFileUtils.importDbUnitResultFile(STR_F136482_CTD, sqlExecutionContext)
  val rs2: ResultSet = DbUnitResultFileUtils.importDbUnitResultFile(TLS_F027737_MTD_no_varmod, sqlExecutionContext)

  @Test
  def testMergeOneRS() {

    var localJPAExecutionContext: IExecutionContext = null

    try {
      
      // TODO: allow to distinguish between the input data (ID VS RS) and the fact to store or not the RSM
      val rsMerger = new ResultSetMerger(sqlExecutionContext, Some(Seq(rs1.id,rs1.id)), None, None, ResultSetsMergerTest.useJPA)

      val result = rsMerger.runService()
      assertTrue("ResultSet merger result", result)
      logger.info("End Run ResultSetMerger Service, merge same RS twice, in Test")

      val tRSM = rsMerger.mergedResultSet
      assertNotNull("Merged TARGET ResultSet", tRSM)

      val mergedDecoyRS = tRSM.decoyResultSet
      assertTrue("Merged DECOY ResultSet is present", (mergedDecoyRS != null) && mergedDecoyRS.isDefined)

      /* Try to reload merged ResultSet with JPA */
      val mergedRSId = tRSM.id

      localJPAExecutionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, useJPA = true)

      val rsProvider = new ORMResultSetProvider(localJPAExecutionContext.getMSIDbConnectionContext)

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
      val rsMerger = new ResultSetMerger(sqlExecutionContext, Some( Seq(rs1.id,rs2.id) ), None, None, ResultSetsMergerTest.useJPA )
      // val rsMerger = new ResultSetMerger(sqlExecutionContext, None, Some(loadResultSetsWithDecoy(rzProvider, rsIds)))

      val result = rsMerger.runService()
      assertTrue("ResultSet merger result", result)
      logger.info("End Run ResultSetMerger Service, merge two different RS twice, in Test")

      val tRSM = rsMerger.mergedResultSet
      assertNotNull("Merged TARGET ResultSet", tRSM)

      val mergedDecoyRSId = tRSM.getDecoyResultSetId
      assertTrue("Merged DECOY ResultSet is present", mergedDecoyRSId > 0)

      /* Try to reload merged ResultSet with JPA */
      val mergedRSId = tRSM.id

      localJPAExecutionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, useJPA = true)
      val msiEM = localJPAExecutionContext.getMSIDbConnectionContext.getEntityManager
      val msiRS  = msiEM.find(classOf[fr.proline.core.orm.msi.ResultSet], mergedRSId)
      
      assertTrue("Reloaded Merged ORM ResultSet", msiRS != null)

      val msiDecoyRS = msiRS.getDecoyResultSet
      assertTrue("Reloaded Merged DECOY ORM ResultSet", msiDecoyRS != null)
      
      assertTrue("Merged ResultSet linked to child", msiRS.getChildren != null && !msiRS.getChildren.isEmpty)
      
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