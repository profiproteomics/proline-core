package fr.proline.core.om.provider.msi.impl

import java.sql.Connection

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.STR_F063442_F122817_MergedRSMs
import fr.proline.repository.DriverType
import fr.proline.repository.util.JDBCWork
import org.junit.{Assert, Test}

@Test
object ORMResultSetProviderTest extends AbstractDatastoreTestCase with StrictLogging {

  override val driverType = DriverType.H2
  override val dbUnitResultFile = STR_F063442_F122817_MergedRSMs
  override val useJPA = true

  val targetRSId = 33L
}

class ORMResultSetProviderTest extends StrictLogging {

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
    ORMResultSetProviderTest.executionContext.getMSIDbConnectionContext().doWork(jdbcWork, false)

    allRSIds.result
  }
   
  @Test
  def getReadResultSet() = {
    val leavesRsIds: Seq[Long] = getLeafChildsID(ORMResultSetProviderTest.targetRSId)
    Assert.assertEquals(2,leavesRsIds.length)
      
	  val rsProvider=  new ORMResultSetProvider(
      ORMResultSetProviderTest.executionContext.getMSIDbConnectionContext()
	  )
      
	  val rsID = leavesRsIds(0)
	  val resultRS = rsProvider.getResultSet(rsID)
	  Assert.assertNotNull(resultRS)
	  Assert.assertTrue(resultRS.isDefined)

  }
   

}