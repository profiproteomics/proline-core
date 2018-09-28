package fr.proline.core.dbunit

import org.junit.Assert._
import org.junit.Test

/**
 * @author David Bouyssie
 *
 */
class DbUnitResultFileTest {
  
  @Test
  def testFileParsing() {
    
    // Load the DbUnit result file
    val dbUnitRF = DbUnitResultFileUtils.loadDbUnitResultFile(TLS_F027737_MTD_no_varmod)

    assertTrue("has decoy result set check", dbUnitRF.hasDecoyResultSet)
    assertTrue("has peaklist check", dbUnitRF.hasMs2Peaklist)
    assertEquals("MS level check", 2, dbUnitRF.msLevel )
    
    val targetRS = dbUnitRF.getResultSet(wantDecoy = false)
    assertEquals("PSM count check", 2160, targetRS.peptideMatches.length )
    assertEquals("Protein Match count check", 3697, targetRS.proteinMatches.length )
    
  }

}