package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging

import org.junit.Assert
import org.junit.Test

import fr.proline.core.algo.msi.filtering.pepmatch.SinglePSMPerPrettyRankFilter
import fr.proline.core.algo.msi.filtering.pepmatch.SinglePSMPerQueryFilter
import fr.proline.core.dal.AbstractResultSetTestCase
import fr.proline.core.dbunit.GRE_F068213_M2_4_TD_EColi
import fr.proline.core.om.provider.msi.impl.SQLPeptideMatchProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.repository.DriverType

object SinglePSMPerXXTest extends AbstractResultSetTestCase with StrictLogging {
  
  // Define some vars
  val driverType = DriverType.H2
  val dbUnitResultFile = GRE_F068213_M2_4_TD_EColi
  val targetRSId: Long = 2L
  val decoyRSId = None
  val useJPA = true
  
 
  
}

class SinglePSMPerXXTest extends StrictLogging {
  
 val targetRSId = SinglePSMPerXXTest.targetRSId
 val executionContext = SinglePSMPerXXTest.executionContext

  
  @Test
  def singlePsmPerRankFilter(){
     val pepProvider = new SQLPeptideProvider(executionContext.getPSDbConnectionContext)
     val psmProvider = new SQLPeptideMatchProvider(executionContext.getMSIDbConnectionContext(), pepProvider)
     
     val filter= new SinglePSMPerPrettyRankFilter(SinglePSMPerXXTest.getRS())
     val psms = psmProvider.getResultSetPeptideMatches(targetRSId,None)
     
     Assert.assertEquals(1525, psms.filter(_.isValidated).length)       
     filter.filterPeptideMatches(psms, false,false)
     Assert.assertEquals(1513, psms.filter(_.isValidated).length)        
          
 }
  
   @Test
  def singlePsmPerQueryFilter(){
     val pepProvider = new SQLPeptideProvider(executionContext.getPSDbConnectionContext)
     val psmProvider = new SQLPeptideMatchProvider(executionContext.getMSIDbConnectionContext(), pepProvider)
     
     val filter= new SinglePSMPerQueryFilter(SinglePSMPerXXTest.getRS())
     val psms = psmProvider.getResultSetPeptideMatches(targetRSId,None)
     
     Assert.assertEquals(1525, psms.filter(_.isValidated).length)       
     filter.filterPeptideMatches(psms, false,false)
     Assert.assertEquals(1436, psms.filter(_.isValidated).length) //valeur 21/10, pas vraiment verifie.... mais test fonctionnel        
          
 }
  
}
