package fr.proline.core.om.provider

import org.junit.Assert._
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideMatchProvider
import fr.proline.core.om.utils.AbstractMultipleDBTestCase
import fr.proline.repository.DriverType
import fr.proline.core.dal.ContextFactory

@Test
class ProviderDecoratedExecutionContextTest extends AbstractMultipleDBTestCase with Logging {

  @Before
  def initTests() = {
    logger.info("Initializing Dbs")

    super.initDBsDBManagement(DriverType.H2)
  }

  @Test
  def test() {
    val executionContext = ContextFactory.buildExecutionContext(dsConnectorFactoryForTest, 1, true)

    val parserContext = new ProviderDecoratedExecutionContext(executionContext)

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
  def tearDown() = {
    closeDbs()

    logger.info("Dbs succesfully closed")
  }

}
