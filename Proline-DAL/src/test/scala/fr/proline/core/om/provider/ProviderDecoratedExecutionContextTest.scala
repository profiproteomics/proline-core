package fr.proline.core.om.provider

import org.junit.Assert._
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideMatchProvider
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import fr.proline.core.dal.BuildLazyExecutionContext

@Test
class ProviderDecoratedExecutionContextTest extends AbstractMultipleDBTestCase with StrictLogging {

  @Before
  def initTests() = {
    logger.info("Initializing Dbs")

    super.initDBsDBManagement(DriverType.H2)
  }

  @Test
  def test() {
    val executionContext = BuildLazyExecutionContext(dsConnectorFactoryForTest, 1, true)

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
  }

  @After
  override def tearDown() = {
    super.tearDown()
  }

}
