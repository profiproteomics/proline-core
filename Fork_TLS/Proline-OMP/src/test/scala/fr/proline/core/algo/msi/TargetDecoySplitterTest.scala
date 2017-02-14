package fr.proline.core.algo.msi

import scala.collection.mutable.HashMap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore

import com.typesafe.scalalogging.StrictLogging

import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ResultSet

/*object TargetDecoySplitterTest extends AbstractDatasetImporterTestCase with StrictLogging {

  val driverType = DriverType.H2
  val useJPA = false

  private var _datFileName: String = "/dat_samples/STR_F136482_CTD.dat"
  private var absoluteDatFileNameSet = false
  def datFileName_=(value: String): Unit = {
    _datFileName = value
    absoluteDatFileNameSet = true
  }

  def datFileName = _datFileName

  private def buildFakeParserContext: ProviderDecoratedExecutionContext = {
    val executionContext = new BasicExecutionContext(null, null, null, null, null)

    val parserContext = ProviderDecoratedExecutionContext(executionContext) // Use Object factory

    parserContext.putProvider(classOf[IPeptideProvider], PeptideFakeProvider)
    parserContext.putProvider(classOf[IPTMProvider], PTMFakeProvider)
    parserContext.putProvider(classOf[IProteinProvider], ProteinFakeProvider)
    parserContext.putProvider(classOf[ISeqDatabaseProvider], SeqDbFakeProvider)

    parserContext
  }
  
}*/

// TODO: move into fr.proline.core.algo.msi
class TargetDecoySplitterTest extends StrictLogging {
  
  val resultFile = DbUnitResultFileUtils.loadDbUnitResultFile(STR_F136482_CTD)

  // THIS TEST THROWS A NULL POINTER EXCEPTION
  
  @Ignore
  def testSplit() {

    val targetRS = resultFile.getResultSet(false)
    val decoyRS = resultFile.getResultSet(true)
    
    val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId(), additionMode = AdditionMode.UNION)
    rsAddAlgo.addResultSet(targetRS)
    rsAddAlgo.addResultSet(decoyRS)
    val jointRS = rsAddAlgo.toResultSet()
    
    // Build regex matching decoy accession numbers
    val acDecoyRegex = """sp\|REV_\S+""".r

    val (tRs, dRs) = TargetDecoyResultSetSplitter.split(jointRS, acDecoyRegex)

    assertEquals(tRs.decoyResultSet.get, dRs)
    assertEquals(jointRS.proteinMatches.length, (tRs.proteinMatches.length + dRs.proteinMatches.length))

    for (pepMatch <- dRs.peptideMatches) {
      assertTrue(pepMatch.isDecoy)
    }

    for (seqMatch <- dRs.proteinMatches.flatMap(_.sequenceMatches)) {
      assertTrue(seqMatch.isDecoy)
      assertEquals(true, seqMatch.bestPeptideMatch.get.isDecoy)
    }

    logger.debug("number of target protein matches = " + tRs.proteinMatches.length)
    logger.debug("number of decoy protein matches  = " + dRs.proteinMatches.length)
    logger.debug("number of target peptide matches = " + tRs.peptideMatches.length)
    logger.debug("number of decoy peptide matches  = " + dRs.peptideMatches.length)
    logger.debug("number of target peptides        = " + tRs.peptides.length)
    logger.debug("number of decoy peptides         = " + dRs.peptides.length)

    val peptideMatches = tRs.peptideMatches ++ dRs.peptideMatches
    assertEquals(peptideMatches.size, (tRs.peptideMatches.length + dRs.peptideMatches.length))
    val pepMatchesByMsQueryInitialId = new HashMap() ++ peptideMatches.groupBy(_.msQuery.initialId)
    val nbrQueries = pepMatchesByMsQueryInitialId.size
    assertEquals(nbrQueries, 782) //Vu avec IRMa: 216 unassigned queries sur les 998...

    // Build peptide match joint table
    for ((msQueryInitialId, pepMatches) <- pepMatchesByMsQueryInitialId) {

      // Group peptide matches by result set id
      val sortedPepMatches: Array[PeptideMatch] = pepMatches.sortBy(_.rank)
      pepMatchesByMsQueryInitialId += msQueryInitialId -> sortedPepMatches
    }
    
    assertEquals(nbrQueries, pepMatchesByMsQueryInitialId.size)
    assertEquals(19, pepMatchesByMsQueryInitialId.get(145).get.length)
    
    val pepMatchQ145 = pepMatchesByMsQueryInitialId.get(145).get.filter(_.peptide.sequence == "VAIPK")
    assertEquals(2, pepMatchQ145.size)
    assertEquals(pepMatchQ145(0).peptideId, pepMatchQ145(1).peptideId)

  }

}