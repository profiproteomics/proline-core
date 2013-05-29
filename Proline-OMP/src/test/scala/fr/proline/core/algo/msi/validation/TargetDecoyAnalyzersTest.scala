package fr.proline.core.algo.msi.validation

import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.filtering.IPeptideMatchSorter
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.utils.generator.ResultSetFakeBuilder
import fr.proline.core.om.model.msi.SearchSettingsProperties
import org.junit.Before

@Test
class TargetDecoyAnalyzersTest extends JUnitSuite with Logging {

  var rs: ResultSet = null
  var rsDecoy: ResultSet = null

  @Before
  def setUp() = {
    rs = new ResultSetFakeBuilder(pepNb = 4, proNb = 2).toResultSet()
    rsDecoy = new ResultSetFakeBuilder(pepNb = 4, proNb = 2).toResultSet()
    rs.decoyResultSet = Some(rsDecoy)
  }

  @Test
  def getCorrectBasicTDAnalyzer() = {
    val prop = new SearchSettingsProperties()
    prop.targetDecoyMode = Some("CONCATENATED")
    rs.msiSearch.get.searchSettings.properties = Some(prop)

    val tdAnalyzer = BuildTDAnalyzer(false, rs, None)
    assertNotNull(tdAnalyzer)
    assertTrue(tdAnalyzer.isDefined)
    assertTrue(tdAnalyzer.get.isInstanceOf[BasicTDAnalyzer])
  }

  @Test(expected = classOf[IllegalArgumentException])
  def getBasicTDAnalyzerWoSSProperties() = {

    val tdAnalyzer = BuildTDAnalyzer(false, rs, None)
  }

  @Test
  def getCorrectBasicTDAnalyzerWSorter() = {
    val prop = new SearchSettingsProperties()
    prop.targetDecoyMode = Some("CONCATENATED")
    rs.msiSearch.get.searchSettings.properties = Some(prop)

    val sorter: IPeptideMatchSorter = new ScorePSMFilter()
    val tdAnalyzer = BuildTDAnalyzer(false, rs, Some(sorter))
    assertNotNull(tdAnalyzer)
    assertTrue(tdAnalyzer.isDefined)
    assertTrue(tdAnalyzer.get.isInstanceOf[BasicTDAnalyzer])
  }
  
  // FIXME: re-enable test when  ComputedTDAnalyzer is fixed
  @Ignore
  def getCorrectComputedTDAnalyzerWSorter() = {

    val sorter: IPeptideMatchSorter = new ScorePSMFilter()
    val tdAnalyzer = BuildTDAnalyzer(true, rs, Some(sorter))
    assertNotNull(tdAnalyzer)
    assertTrue(tdAnalyzer.isDefined)
    // FIXME: remove this comment when ComputedTDAnalyzer is fixed
    //assertTrue(tdAnalyzer.get.isInstanceOf[CompetitionBasedTDAnalyzer])
  }
}

