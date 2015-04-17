package fr.proline.core.dal.helper

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.repository.ProlineDatabaseType
import fr.proline.repository.util.DatabaseTestCase

object MsiDbHelperTest extends DatabaseTestCase with Logging {

  override def getProlineDatabaseType() = ProlineDatabaseType.MSI

  override def getPropertiesFileName(): String = {
    return "db_settings/h2/db_msi.properties"
  }

  var executionContext: IExecutionContext = null

  @BeforeClass
  @throws(classOf[Exception])
  def setUp() = {

    logger.info("Initializing MSIs")

    initDatabase()

    loadCompositeDataSet(Array("/dbunit/datasets/msi-db_init_dataset.xml", "/dbunit/datasets/msi/Resultset_Dataset.xml")) //Load Data
  }

  @AfterClass
  override def tearDown() {
    super.tearDown()
  }
}

@Test
class MsiDbHelperTest {

  @Test
  def testGetRSMsiSearchIds1Level() = {
    val msiDbCtxt = new DatabaseConnectionContext(MsiDbHelperTest.getConnector)
    val helper = new MsiDbHelper(msiDbCtxt)

    val msiIds = helper.getResultSetsMsiSearchIds(Array(3L))

    assertEquals(2, msiIds.length)
  }
  
  @Test
  def testGetRSMsiSearchIds2Level() = {
    println("testGetRSMsiSearchIds2Level")
    val msiDbCtxt = new DatabaseConnectionContext(MsiDbHelperTest.getConnector)
    val helper = new MsiDbHelper(msiDbCtxt)

    val msiIds = helper.getResultSetsMsiSearchIds(Array(4L))

    assertEquals(2, msiIds.length)
  }
}