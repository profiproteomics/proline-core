package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ResultSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import scala.collection.mutable.HashMap

// TODO: move into fr.proline.core.algo.msi
class TargetDecoySplitterTest extends StrictLogging {
  
  val resultFile = DbUnitResultFileUtils.loadDbUnitResultFile(STR_F136482_CTD)

  @Test
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
    assertEquals(37, pepMatchesByMsQueryInitialId.get(-145).get.length)
    
    val pepMatchQ145 = pepMatchesByMsQueryInitialId.get(-145).get.filter(_.peptide.sequence == "VAIPK")
    assertEquals(4, pepMatchQ145.size)
    assertEquals(pepMatchQ145(0).peptideId, pepMatchQ145(1).peptideId)

  }

}