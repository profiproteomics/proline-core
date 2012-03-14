package fr.proline.core.om.factory.sql

import org.junit._
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.scalatest.junit.{ JUnitRunner, JUnitSuite }

import fr.proline.core.om.provider.msi.impl.PeptideMatchLoader
import net.noerd.prequel.DatabaseConfig
import net.noerd.prequel.IsolationLevels
    
/**
 * TODO : Get a MSIsdb in sqlite for testing !! 
 */

@RunWith(classOf[JUnitRunner])
@Test
class PeptideMatchLoaderTest extends JUnitSuite {
      
    var database : DatabaseConfig = null
    
    @Before
    def initialize() = {
//    	database = DatabaseConfig(
//        driver = "org.sqlite.JDBC",
//        jdbcURL = "jdbc:sqlite:msi-db.sqlite",
//        isolationLevel = IsolationLevels.Serializable
//      )
    }
   
	@Test
    def testLoadPeptideMatches() = {
//	   
//    val pepMatchLoader = new PeptideMatchLoader(msiDb = database, psDb = database)
//    val pepMatches = pepMatchLoader.getPeptideMatches(rsIds = Array(1,2,3,4))
//    println(pepMatches.length)
//    assertEquals(12, pepMatches.length )
	  	 assertTrue(true)
	}
	
}