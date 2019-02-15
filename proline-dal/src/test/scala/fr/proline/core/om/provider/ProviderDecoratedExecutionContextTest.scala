package fr.proline.core.om.provider

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.dal.{AbstractDatastoreTestCase, BuildLazyExecutionContext}
import fr.proline.core.om.provider.msi.{IPTMProvider, IPeptideMatchProvider}
import fr.proline.repository.DriverType
import org.junit.Assert._
import org.junit.Test


object ProviderDecoratedExecutionContextTest extends AbstractDatastoreTestCase {

  override val driverType: DriverType = DriverType.H2
  override val useJPA: Boolean = true
}


class ProviderDecoratedExecutionContextTest extends StrictLogging {

  @Test
  def test() {
    val executionContext = BuildLazyExecutionContext(ProviderDecoratedExecutionContextTest.dsConnectorFactoryForTest, 1, true)

    val parserContext = ProviderDecoratedExecutionContext(executionContext)  // Use Object factory

    val ptmProvider = parserContext.getProvider(classOf[IPTMProvider])

    assertNotNull("Default IPTMProvider", ptmProvider)

    logger.debug("IPTMProvider [" + ptmProvider.getClass.getName + ']')

    val ptmDef = ptmProvider.getPtmDefinition(1)

    val ptmProvider2 = parserContext.getProvider(classOf[IPTMProvider])

    assertEquals("Second IPTMProvider", ptmProvider, ptmProvider2)

    val peptideMatchProvider = parserContext.getProvider(classOf[IPeptideMatchProvider])

    assertNotNull("Default IPeptideMatchProvider", peptideMatchProvider)

    logger.debug("IPeptideMatchProvider [" + peptideMatchProvider.getClass.getName + ']')

    val peptideMatchProvider2 = parserContext.getProvider(classOf[IPeptideMatchProvider])

    assertEquals("Second IPeptideMatchProvider", peptideMatchProvider, peptideMatchProvider2)
    
    executionContext.closeAll()
  }

}
