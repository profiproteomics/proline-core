package fr.proline.core.algo.msi

import com.typesafe.scalalogging.StrictLogging
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msi.filtering.pepmatch.ScorePSMFilter
import fr.proline.core.algo.msi.validation.{BasicTDAnalyzer, TargetDecoyModes}
import fr.proline.core.dal.AbstractDatastoreTestCase
import fr.proline.core.dbunit.{DbUnitResultFileLocation, STR_F122817_Mascot_v2_3}
import fr.proline.core.om.model.msi.{ResultSet, SequenceMatch}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.ResultSetFilter
import fr.proline.core.om.provider.msi.impl.{SQLResultSetProvider, SQLResultSummaryProvider}
import fr.proline.core.service.msi.ResultSetValidator
import fr.proline.repository.DriverType
import org.junit.Assert._
import org.junit.Test


object RsAdderFromResultFileTest extends AbstractDatastoreTestCase {

  override val driverType: DriverType = DriverType.H2
  override val useJPA: Boolean = true
  override val dbUnitResultFile: DbUnitResultFileLocation = STR_F122817_Mascot_v2_3

  val targetRSId = 1L

}

/**
  * VDS FIXME ===> Suite au Merge, le probleme a disparu... si ca se confirme, Commentaire A SUPPRIMER.
  * While init STR_F122817_Mascot_v2_3_TEST_CASE => Error getting PtmDef for used_PTM :
  *  java.util.NoSuchElementException: key not found: when searching PTM_Classification for used_PTM : classification = ""
  *  JPAPtmDefinitionWriter.convertPtmDefinitionToMsiPtmSpecificity(JPAPtmDefinitionStorer.scala:196)
  */
@Test
class RsAdderFromResultFileTest extends StrictLogging with RsAdderFromResultFileTesting {

  override val executionContext: IExecutionContext = RsAdderFromResultFileTest.executionContext
  override val readRS: ResultSet = RsAdderFromResultFileTest.getRS(RsAdderFromResultFileTest.targetRSId)
  
  @Test
  def addOneRS() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def addOneRSTwice() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS)
    rsAddAlgo.addResultSet(readRS)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def addRankFilteredRS() {

    // Load the result set with a pretty rank filter, as IdentificationTreeValidator does:
    // only the peptide matches are filtered, the protein and sequence matches are all kept
    val rsProvider = new SQLResultSetProvider(PeptideCacheExecutionContext(executionContext))
    val rsFilter = ResultSetFilter(maxPeptideMatchPrettyRank = Some(1))
    val filteredRS = rsProvider.getResultSets(Seq(RsAdderFromResultFileTest.targetRSId), Some(rsFilter)).head

    val keptPepIds = filteredRS.peptideMatches.map(_.peptide.id).toSet
    val isKeptSeqMatch = (seqMatch: SequenceMatch) => keptPepIds.contains(seqMatch.getPeptideId)
    assertTrue(
      "some sequence matches of the filtered result set should reference a filtered out peptide",
      filteredRS.proteinMatches.flatMap(_.sequenceMatches).exists(!isKeptSeqMatch(_))
    )

    val rsAddAlgo = new ResultSetAdder(resultSetId = ResultSet.generateNewId())
    rsAddAlgo.addResultSet(filteredRS)

    val builtRS = rsAddAlgo.toResultSet()

    // Sequence matches of filtered out peptides are removed, and the other ones reference a merged peptide match
    val builtPepMatchIds = builtRS.peptideMatches.map(_.id).toSet
    val builtSeqMatches = builtRS.proteinMatches.flatMap(_.sequenceMatches)
    assertTrue("sequence matches of filtered out peptides should be removed", builtSeqMatches.forall(isKeptSeqMatch))
    assertTrue("sequence matches should reference a merged peptide match", builtSeqMatches.forall(sm => builtPepMatchIds.contains(sm.bestPeptideMatchId)))

    // Protein matches having only sequence matches of filtered out peptides are removed
    val expectedProtMatchesCount = filteredRS.proteinMatches.count { protMatch =>
      protMatch.sequenceMatches.isEmpty || protMatch.sequenceMatches.exists(isKeptSeqMatch)
    }
    assertEquals("merged protein matches count", expectedProtMatchesCount, builtRS.proteinMatches.length)

    storeBuiltResultSet(builtRS)
  }

  @Test
  def mergeTwiceRSAndValidate() {
    
    val rsId = ResultSet.generateNewId()
    val rsAddAlgo = new ResultSetAdder(resultSetId = rsId)
    rsAddAlgo.addResultSet(readRS)
    rsAddAlgo.addResultSet(readRS)
    
    val builtRS = rsAddAlgo.toResultSet()
    
    checkBuiltResultSet(builtRS)

    storeBuiltResultSet(builtRS)

    val rsValidation = new ResultSetValidator(
      execContext = executionContext,
      targetRs = builtRS,
      tdAnalyzer = Some(new BasicTDAnalyzer(TargetDecoyModes.CONCATENATED, useTdCompetition = true)),
      pepMatchPreFilters = Some(Seq(new ScorePSMFilter(scoreThreshold = 22.0f))),
      pepMatchValidator = None,
      protSetFilters = None,
      storeResultSummary = true
    )

    val result = rsValidation.runService()
    assertTrue("ResultSet validation result", result)
    logger.info(" End Run ResultSetValidator Service with Score Filter, in Test ")

    val tRSM = rsValidation.validatedTargetRsm
    logger.info(" rsValidation.validatedTargetRsm " + tRSM.id)
    assertNotNull(tRSM)
    
    val provider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val readRSM = provider.getResultSummary(tRSM.id, loadResultSet = false)
    assertNotNull(readRSM)

  }

}

