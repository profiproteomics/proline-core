package fr.proline.core.dal.helper

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase
import com.typesafe.scalalogging.slf4j.Logging

@Test
class MsiDbHelperTest extends DatabaseTestCase with Logging {

  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  var executionContext: IExecutionContext = null

  @Before
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing MSIs")

    initDatabase()

      loadCompositeDataSet(Array("/dbunit/datasets/msi-db_init_dataset.xml","/dbunit_samples/ResultSetRelation.xml")) //Load Data


  }

  @After
  override def tearDown() {
    super.tearDown()
  }

  @Test
  def testGetRSMsiSearchIds2Level() = {
    val msiDbCtxt = new DatabaseConnectionContext(getConnector)
    val helper = new MsiDbHelper(msiDbCtxt)

    val rsIdsB = Seq.newBuilder[Long]
    rsIdsB += 4l
    val msiIds = helper.getResultSetsMsiSearchIds(rsIdsB.result)

    assertEquals(2, msiIds.length)

  }

  @Test
  def testGetRSMsiSearchIds1Level() = {
    val msiDbCtxt = new DatabaseConnectionContext(getConnector)
    val helper = new MsiDbHelper(msiDbCtxt)

    val rsIdsB = Seq.newBuilder[Long]
    rsIdsB += 3l
    val msiIds = helper.getResultSetsMsiSearchIds(rsIdsB.result)

    assertEquals(2, msiIds.length)

  }
}