package fr.proline.core.algo.msi

import org.junit.BeforeClass
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.dbunit.DbUnitSampleDataset
import fr.proline.core.dbunit.GRE_F068213_M2_4_TD_EColi
import fr.proline.repository.DriverType
import fr.proline.core.om.provider.msi.impl.SQLPeptideMatchProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.algo.msi.filtering.pepmatch.SinglePSMPerPrettyRankFilter
import org.junit.Assert

object SinglePSMPerRankTest extends AbstractResultSetTestCase with StrictLogging {
  
  // Define some vars
  val driverType = DriverType.H2
  val dbUnitResultFile = GRE_F068213_M2_4_TD_EColi
  val targetRSId: Long = 2L
  val decoyRSId = None
  val useJPA = true
  
 
  
}

class SinglePSMPerRankTest extends StrictLogging {
  
 val targetRSId = SinglePSMPerRankTest.targetRSId
 val executionContext = SinglePSMPerRankTest.executionContext

  
  @Test
  def singlePsmPerRankFilter(){
     val pepProvider = new SQLPeptideProvider(executionContext.getPSDbConnectionContext)
     val psmProvider = new SQLPeptideMatchProvider(executionContext.getMSIDbConnectionContext(), pepProvider)
     
     val filter= new SinglePSMPerPrettyRankFilter(SinglePSMPerRankTest.getRS())
     val psms = psmProvider.getResultSetPeptideMatches(targetRSId,None)
     
     Assert.assertEquals(1525, psms.filter(_.isValidated).length)       
     filter.filterPeptideMatches(psms, false,false)
     Assert.assertEquals(1513, psms.filter(_.isValidated).length)        
          
 }
  
  
}
