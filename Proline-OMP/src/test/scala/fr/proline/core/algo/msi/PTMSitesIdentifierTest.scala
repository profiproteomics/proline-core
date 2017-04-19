package fr.proline.core.algo.msi

import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.dal._
import fr.proline.repository.DriverType
import fr.proline.core.dbunit.DbUnitSampleDataset
import fr.proline.core.dbunit.GRE_F068213_M2_4_TD_EColi
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.BasicTDAnalyzer
import fr.proline.core.algo.msi.validation.TargetDecoyModes
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dbunit.DbUnitResultFileUtils
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.model.msi.ResultSummary

object PTMSitesIdentifierTest extends StrictLogging {
  
  lazy val proteinSetInferer = new ParsimoniousProteinSetInferer()
  var readRS: ResultSet = null
  var rsm: ResultSummary = null
  
  @BeforeClass
  def init() {
    readRS = GRE_F068213_M2_4_TD_EColi_TEST_CASE.getRS
    //rsm = proteinSetInferer.computeResultSummary( resultSet = readRS )
  }

}

class PTMSitesIdentifierTest extends StrictLogging {

  val sqlExecutionContext = GRE_F068213_M2_4_TD_EColi_TEST_CASE.executionContext

  @Test
  def testPTMSitesIdentifier() {

//    val ptmSites = new PTMSitesIdentifier().identifyPTMSites(rsm,readRS.proteinMatches)
//    println("serialized" + ProfiJson.serialize(ptmSites))
    
    
  }

  

}
