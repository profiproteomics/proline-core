package fr.proline.core.service.uds

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.IPeptideMatchFilter
import fr.proline.core.algo.msi.filtering.pepmatch.PrettyRankPSMFilter
import fr.proline.core.dal._
import fr.proline.core.dal.context._
import fr.proline.core.dbunit._
import fr.proline.core.orm.msi.{ResultSet => MsiResultSet, ResultSummary => MsiResultSummary}
import fr.proline.core.orm.uds.{Dataset => UdsDataset, IdentificationDataset, Project, UserAccount}
import fr.proline.repository.DriverType
import javax.persistence.EntityManager
import org.junit.Assert._
import org.junit.Test

object IdentificationTreeValidatorTest extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val useJPA = true

}

class IdentificationTreeValidatorTest extends StrictLogging {

  val executionContext = IdentificationTreeValidatorTest.executionContext
  val dsConnectorFactoryForTest = IdentificationTreeValidatorTest.dsConnectorFactoryForTest

  // Both leaves are imported from the same separated target/decoy search (TLS_F027737_MTD_no_varmod):
  // merging concatenated and separated searches gives a MIXED target/decoy mode, not supported by ResultSetValidator

  /**
   * root
   * ├── TLS leaf 1
   * └── TLS leaf 2
   */
  @Test
  def testBalancedTree() {
    validateTree(unbalanced = false, mergeResultSets = false)
    validateTree(unbalanced = false, mergeResultSets = true)
  }

  /**
   * root
   * ├── TLS leaf 1
   * └── aggregate A
   *     └── TLS leaf 2
   */
  @Test
  def testUnbalancedTree() {
    validateTree(unbalanced = true, mergeResultSets = false)
    validateTree(unbalanced = true, mergeResultSets = true)
  }

  private def validateTree(unbalanced: Boolean, mergeResultSets: Boolean) {

    val label = s"${if (unbalanced) "unbalanced" else "balanced"} tree, ${if (mergeResultSets) "RS" else "RSM"} merge"
    logger.info(s"Validating $label ...")

    // Import two fresh copies of the result file (target and decoy result sets)
    val leafRsList = Seq.fill(2)(DbUnitResultFileUtils.importDbUnitResultFile(TLS_F027737_MTD_no_varmod, executionContext))

    val udsDbCtx = executionContext.getUDSDbConnectionContext()
    val udsEM = udsDbCtx.getEntityManager

    // Build the dataset tree in the UDSdb
    var rootDs: UdsDataset = null
    var aggregateDsOpt: Option[UdsDataset] = None
    var leafDsList: Seq[UdsDataset] = null

    udsDbCtx.tryInTransaction {
      val udsProject = createProject(udsEM, label)

      rootDs = newAggregate(udsProject, "root")
      udsEM.persist(rootDs)

      val leaf1 = newLeaf(udsProject, "TLS leaf 1", leafRsList(0).id)
      val leaf2 = newLeaf(udsProject, "TLS leaf 2", leafRsList(1).id)
      rootDs.addChild(leaf1)

      if (unbalanced) {
        val aggregateDs = newAggregate(udsProject, "aggregate A")
        rootDs.addChild(aggregateDs)
        udsEM.persist(aggregateDs)
        aggregateDs.addChild(leaf2)
        aggregateDsOpt = Some(aggregateDs)
      } else {
        rootDs.addChild(leaf2)
      }

      udsEM.persist(leaf1)
      udsEM.persist(leaf2)
      leafDsList = Seq(leaf1, leaf2)
    }

    // Force the validator to reload the tree from the UDSdb, as in production
    // (getIdentificationDatasets fails on in-memory leaves, which have no children list)
    udsEM.clear()

    // Validate the tree
    // Note: IdentificationTreeValidator.validateIdentificationTrees can't be used here,
    // because it also opens an LCMSdb transaction and the test datastore has no LCMSdb
    val pepMatchFilters: Seq[IPeptideMatchFilter] = Seq(new PrettyRankPSMFilter(2)) // Only 1, 2 ranks

    val treeValidator = new IdentificationTreeValidator(
      execContext = executionContext,
      identTreeId = rootDs.getId,
      mergeResultSets = mergeResultSets,
      useTdCompetition = false,
      pepMatchFilters = Some(pepMatchFilters)
    )

    udsDbCtx.tryInTransaction {
      executionContext.getMSIDbConnectionContext().tryInTransaction {
        assertTrue(s"$label: tree validation", treeValidator.runService())
      }
    }

    // Reload the datasets from the UDSdb
    udsEM.clear()

    // Leaves: still pointing to the imported result sets, with a validated result summary
    for ((leafDs, rs) <- leafDsList.zip(leafRsList)) {
      val reloadedLeafDs = udsEM.find(classOf[UdsDataset], leafDs.getId)
      assertEquals(s"$label: result set of leaf ${leafDs.getName}", rs.id, reloadedLeafDs.getResultSetId.longValue)
      assertNotNull(s"$label: result summary of leaf ${leafDs.getName}", reloadedLeafDs.getResultSummaryId)
    }

    // Merged nodes: stored target RS/RSM, linked to a stored decoy RS/RSM
    val mergedDsIds = aggregateDsOpt.map(_.getId).toSeq :+ rootDs.getId

    var localJPAExecutionContext: IExecutionContext = null
    try {
      localJPAExecutionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true)
      val msiEM = localJPAExecutionContext.getMSIDbConnectionContext().getEntityManager()

      for (mergedDsId <- mergedDsIds) {
        val mergedDs = udsEM.find(classOf[UdsDataset], mergedDsId)
        val dsLabel = s"$label: dataset ${mergedDs.getName}"

        assertNotNull(s"$dsLabel: result set id", mergedDs.getResultSetId)
        assertNotNull(s"$dsLabel: result summary id", mergedDs.getResultSummaryId)

        val msiRsm = msiEM.find(classOf[MsiResultSummary], mergedDs.getResultSummaryId)
        assertNotNull(s"$dsLabel: stored result summary", msiRsm)
        assertEquals(s"$dsLabel: result summary linked to the dataset result set", mergedDs.getResultSetId.longValue, msiRsm.getResultSet.getId)

        val msiRs = msiEM.find(classOf[MsiResultSet], mergedDs.getResultSetId)
        assertNotNull(s"$dsLabel: stored result set", msiRs)

        val msiDecoyRsm = msiRsm.getDecoyResultSummary
        assertNotNull(s"$dsLabel: decoy result summary linked to the target one", msiDecoyRsm)
        assertNotNull(s"$dsLabel: decoy result set linked to the target one", msiRs.getDecoyResultSet)
        assertEquals(s"$dsLabel: decoy result summary linked to the decoy result set", msiRs.getDecoyResultSet.getId, msiDecoyRsm.getResultSet.getId)
      }

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

  private def createProject(udsEM: EntityManager, name: String): Project = {
    val udsUser = new UserAccount()
    udsUser.setLogin("tree_validator_" + System.nanoTime())
    udsUser.setPasswordHash("993bd037c1ae6b87ea5adb936e71fca01a3b5c0505855444afdfa2f86405db1d")
    udsUser.setCreationMode("MANUAL")
    udsEM.persist(udsUser)

    val udsProject = new Project(udsUser)
    udsProject.setName(name)
    udsProject.setDescription("Project created for the IdentificationTreeValidatorTest")
    udsProject.setCreationTimestamp(now())
    udsEM.persist(udsProject)

    udsProject
  }

  private def newAggregate(udsProject: Project, name: String): UdsDataset = {
    val udsDataset = new UdsDataset(udsProject)
    udsDataset.setType(UdsDataset.DatasetType.AGGREGATE)
    udsDataset.setName(name)
    udsDataset.setCreationTimestamp(now())
    udsDataset
  }

  private def newLeaf(udsProject: Project, name: String, rsId: Long): UdsDataset = {
    val udsDataset = new IdentificationDataset()
    udsDataset.setProject(udsProject)
    udsDataset.setName(name)
    udsDataset.setCreationTimestamp(now())
    udsDataset.setResultSetId(rsId)
    udsDataset
  }

  private def now() = new java.sql.Timestamp(new java.util.Date().getTime)

}
