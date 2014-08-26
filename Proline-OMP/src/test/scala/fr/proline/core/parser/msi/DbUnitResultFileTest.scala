package fr.proline.core.parser.msi

import java.io.File
import org.junit.Assert._
import org.junit.Test
import fr.profi.util.resources.pathToStreamOrResourceToStream
import fr.proline.core.dbunit.TLS_F027737_MTD_no_varmod

/**
 * @author David Bouyssie
 *
 */
class DbUnitResultFileTest {
  
  @Test
  def testFileParsing() {
    val datasetLocation = TLS_F027737_MTD_no_varmod
    
    val classLoader = classOf[fr.proline.repository.util.DatabaseTestCase]
    
    // Open streams
    val msiStream = classLoader.getResourceAsStream( datasetLocation.msiDbDatasetPath )
    val udsStream = classLoader.getResourceAsStream( datasetLocation.udsDbDatasetPath )
    val psStream = classLoader.getResourceAsStream( datasetLocation.psDbDatasetPath )  
    
    // Load the DbUnit result file
    val dbUnitRF = new DbUnitResultFile(msiStream,udsStream,psStream)   

    assertTrue("has decoy result set check", dbUnitRF.hasDecoyResultSet)
    assertTrue("has peaklist check", dbUnitRF.hasMs2Peaklist)
    assertEquals("MS level check", 2, dbUnitRF.msLevel )
    
    val targetRS = dbUnitRF.getResultSet(wantDecoy = false)
    assertEquals("PSM count check", 2160, targetRS.peptideMatches.length )
    assertEquals("Protein Match count check", 3697, targetRS.proteinMatches.length )
    
    // Close input streams
    msiStream.close()
    udsStream.close()
    psStream.close()
  }

}