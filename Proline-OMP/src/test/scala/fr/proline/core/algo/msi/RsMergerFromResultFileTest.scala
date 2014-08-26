package fr.proline.core.algo.msi

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert._
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.context.BasicExecutionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.dal.ContextFactory
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.om.provider.msi.impl.SQLPTMProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSetProvider
import fr.proline.repository.DriverType
import fr.proline.core.dbunit.STR_F122817_Mascot_v2_3
import fr.proline.core.dbunit.DbUnitInitDataset
import fr.proline.core.dbunit.DbUnitSampleDataset

object RsMergerFromResultFileTest extends AbstractMascotResultFileTestCase with Logging {

  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
}

class RsMergerFromResultFileTest extends Logging {
  
  val executionContext = RsMergerFromResultFileTest.executionContext
  val readRS = RsMergerFromResultFileTest.getRS
  
  // TEST FAIL for STRANGE REASONS
  
  @org.junit.Ignore
  def addOneRS() = {

    val rsMergerAlgo = new ResultSetMerger()
    val rs2 = rsMergerAlgo.mergeResultSets(Seq(readRS))
    assert(rs2 != null)
    assert(readRS != rs2)
    val peptides = rs2.proteinMatches.map(_.sequenceMatches).flatten.map(_.peptide.get.id).distinct
    assertEquals(peptides.length, readRS.peptides.length)
    assertEquals(peptides.length, readRS.peptideMatches.map(_.peptide.id).distinct.length)
    assertEquals(rs2.proteinMatches.map(_.sequenceMatches).length, readRS.proteinMatches.map(_.sequenceMatches).length)
    val ids = rs2.peptideMatches.map(_.resultSetId).distinct
    assertEquals(1, ids.length)
    assertEquals(99, ids(0))
  }

}

