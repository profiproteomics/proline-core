package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.pepmatch.{SinglePSMPerPrettyRankFilter, SinglePSMPerQueryFilter}
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.GRE_F068213_M2_4_TD_EColi
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.impl.{SQLPeptideMatchProvider, SQLPeptideProvider}
import fr.proline.repository.DriverType
import org.junit.{Assert, Test}

object SinglePSMPerXXTest extends AbstractDatastoreTestCase with StrictLogging {
  
  override val driverType = DriverType.H2
  override val dbUnitResultFile = GRE_F068213_M2_4_TD_EColi
  override val useJPA: Boolean = true

  val targetRSId: Long = 2L

}

class SinglePSMPerXXTest extends StrictLogging {
  
 val executionContext: IExecutionContext = SinglePSMPerXXTest.executionContext

  
  @Test
  def singlePsmPerRankFilter(){
     val pepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(executionContext))
     val psmProvider = new SQLPeptideMatchProvider(executionContext.getMSIDbConnectionContext, pepProvider)
     
     val filter= new SinglePSMPerPrettyRankFilter(SinglePSMPerXXTest.getRS(SinglePSMPerXXTest.targetRSId))
     val psms = psmProvider.getResultSetPeptideMatches(SinglePSMPerXXTest.targetRSId,None)
     
     Assert.assertEquals(1525, psms.count(_.isValidated))
     filter.filterPeptideMatches(psms, incrementalValidation = false,traceability = false)
     Assert.assertEquals(1513, psms.count(_.isValidated))
          
 }
  
   @Test
  def singlePsmPerQueryFilter(){
     val pepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(executionContext))
     val psmProvider = new SQLPeptideMatchProvider(executionContext.getMSIDbConnectionContext, pepProvider)
     
     val filter= new SinglePSMPerQueryFilter(SinglePSMPerXXTest.getRS(SinglePSMPerXXTest.targetRSId))
     val psms = psmProvider.getResultSetPeptideMatches(SinglePSMPerXXTest.targetRSId,None)
     
     Assert.assertEquals(1525, psms.count(_.isValidated))
     filter.filterPeptideMatches(psms, incrementalValidation = false,traceability = false)
     Assert.assertEquals(1436, psms.count(_.isValidated)) //valeur 21/10, pas vraiment verifie.... mais test fonctionnel
          
 }
  
}
