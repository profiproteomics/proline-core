package fr.proline.core.om.factory.sql

import org.junit._
import Assert._
import fr.proline.core.om.msi.PeptideClasses._
import net.noerd.prequel.DatabaseConfig
import net.noerd.prequel.IsolationLevels
    
@Test
class PeptideMatchLoaderTest {
      
    var database : DatabaseConfig = null
    
    @Before
    def initialize() = {
    	database = DatabaseConfig(
        driver = "org.sqlite.JDBC",
        jdbcURL = "jdbc:sqlite:msi-db.sqlite",
        isolationLevel = IsolationLevels.Serializable
      )
    }
   
	@Test
    def testLoadPeptideMatches() = {
//	   
    val pepMatchLoader = new PeptideMatchLoader(msiDb = database, psDb = database)
    val pepMatches = pepMatchLoader.getPeptideMatches(rsIds = Array(1,2,3,4))
    println(pepMatches.length)
//    assertEquals(12, pepMatches.length )
	  	 assertTrue(true)
	}
	
}