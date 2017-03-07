package fr.proline.core.om.storer.msi.impl
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.IRsmDuplicator
import fr.proline.core.orm.msi.{ Peptide => MsiPeptide }
import fr.proline.core.orm.msi.{ PeptideInstance => MsiPeptideInstance }
import fr.proline.core.orm.msi.{ PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap }
import fr.proline.core.orm.msi.{ PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK }
import fr.proline.core.orm.msi.{ PeptideMatch => MsiPeptideMatch }
import fr.proline.core.orm.msi.{ PeptideMatchRelation => MsiPeptideMatchRelation }
import fr.proline.core.orm.msi.{ PeptideMatchRelationPK => MsiPeptideMatchRelationPK }
import fr.proline.core.orm.msi.{ PeptideReadablePtmString => MsiPeptideReadablePtmString }
import fr.proline.core.orm.msi.{ PeptideReadablePtmStringPK => MsiPeptideReadablePtmStringPK }
import fr.proline.core.orm.msi.{ PeptideSet => MsiPeptideSet }
import fr.proline.core.orm.msi.{ PeptideSetPeptideInstanceItem => MsiPeptideSetItem }
import fr.proline.core.orm.msi.{ PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK }
import fr.proline.core.orm.msi.{ PeptideSetProteinMatchMap => MsiPepSetProtMatchMap }
import fr.proline.core.orm.msi.{ PeptideSetProteinMatchMapPK => MsiPepSetProtMatchMapPK }
import fr.proline.core.orm.msi.{ ProteinMatch => MsiProteinMatch }
import fr.proline.core.orm.msi.{ ProteinSet => MsiProteinSet }
import fr.proline.core.orm.msi.{ ProteinSetProteinMatchItem => MsiProtSetProtMatchItem }
import fr.proline.core.orm.msi.{ ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK }
import fr.proline.core.orm.msi.{ ResultSet => MsiResultSet }
import fr.proline.core.orm.msi.{ ResultSummary => MsiResultSummary }
import fr.proline.core.orm.msi.{ Scoring => MsiScoring }
import fr.proline.core.orm.msi.{ SequenceMatch => MsiSequenceMatch }
import fr.proline.core.util.ResidueUtils.scalaCharToCharacter
import javax.persistence.EntityManager
import fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMapPK
import fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMap


/**
 * Should not be used any more : RsmDuplicator with  eraseSourceIds: Boolean = true should be the same !
 */
object ResetIdsRsmDuplicator extends IRsmDuplicator with LazyLogging {
  
 
   override def cloneAndStoreRSM(sourceRSM: ResultSummary, emptyRSM: MsiResultSummary, emptyRS: MsiResultSet, eraseSourceIds: Boolean,  msiEm: EntityManager): ResultSummary = {

    val msiMasterPepInstById = new HashMap[Long, MsiPeptideInstance]

    // Retrieve result summary and result set ids
    val quantRsmId = emptyRSM.getId
    val quantRsId = emptyRS.getId

    // Retrieve peptide instances of the merged result summary
    val masterPepInstances = sourceRSM.peptideInstances
    val masterPepMatchById = sourceRSM.resultSet.get.getPeptideMatchById

    // TODO: load scoring from MSIdb
    val msiScoring = new MsiScoring()
    msiScoring.setId(4)

    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info("storing master quant peptide instances... (" + masterPepInstances.size + ")")

    // Define some vars
    val masterQuantPepMatchIdByTmpPepMatchId = new HashMap[Long, Long]

    for (masterPepInstance <- masterPepInstances) {

      val peptide = masterPepInstance.peptide
      val peptideId = peptide.id
      val masterPepInstPepMatchIds = masterPepInstance.getPeptideMatchIds
      assert(masterPepInstPepMatchIds.length == 1, "peptide matches have not been correctly merged")

      // TODO: Retrieve the best peptide match
      //val identParentPepMatches = masterPepInstPepMatchIds.map { masterPepMatchById(_) }
      //val bestParentPepMatch = identParentPepMatches.reduce { (a,b) => if( a.score > b.score ) a else b } 
      val bestParentPepMatch = masterPepMatchById(masterPepInstPepMatchIds(0))

      // Create a quant peptide match which correspond to the best peptide match of this peptide instance
      val msiMasterPepMatch = new MsiPeptideMatch()
      msiMasterPepMatch.setCharge(bestParentPepMatch.msQuery.charge)
      msiMasterPepMatch.setExperimentalMoz(bestParentPepMatch.msQuery.moz)
      msiMasterPepMatch.setScore(bestParentPepMatch.score)
      msiMasterPepMatch.setRank(bestParentPepMatch.rank)
      msiMasterPepMatch.setCDPrettyRank(bestParentPepMatch.cdPrettyRank)
      msiMasterPepMatch.setSDPrettyRank(bestParentPepMatch.sdPrettyRank)
      msiMasterPepMatch.setDeltaMoz(bestParentPepMatch.deltaMoz)
      msiMasterPepMatch.setMissedCleavage(bestParentPepMatch.missedCleavage)
      msiMasterPepMatch.setFragmentMatchCount(bestParentPepMatch.fragmentMatchesCount)
      msiMasterPepMatch.setIsDecoy(false)
      msiMasterPepMatch.setPeptideId(peptideId)

      // FIXME: retrieve the right scoring_id
      msiMasterPepMatch.setScoringId(1)

      // FIXME: change the ORM to allow these mappings
      //msiMasterPepMatch.setBestPeptideMatchId(bestPepMatch.id) 
      //msiMasterPepMatch.setMsQueryId(bestPepMatch.msQueryId)

      // FIXME: remove this mapping when the ORM is updated
      val msiMSQFake = new fr.proline.core.orm.msi.MsQuery
      msiMSQFake.setId(bestParentPepMatch.msQuery.id)
      msiMasterPepMatch.setMsQuery(msiMSQFake)

      msiMasterPepMatch.setResultSet(emptyRS)
      bestParentPepMatch.properties.map(props => msiMasterPepMatch.setSerializedProperties(ProfiJson.serialize(props)))

      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)

      val masterPepMatchId = msiMasterPepMatch.getId

      // Map master peptide match id by in memory merged peptide match id
      masterQuantPepMatchIdByTmpPepMatchId(bestParentPepMatch.id) = masterPepMatchId

      // Update the best parent peptide match id
      bestParentPepMatch.id = masterPepMatchId //VDS FIXME !!! CHANGE MERGE RSM !!!! 

      // Retrieve ORM Peptide
      // TODO: DBO => avoid this because this is particularly slow
      val msiPep = msiEm.find(classOf[MsiPeptide], peptideId)

      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(masterPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(masterPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(masterPepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(masterPepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(masterPepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptide(msiPep)
      msiMasterPepInstance.setBestPeptideMatchId(masterPepMatchId)
      msiMasterPepInstance.setResultSummary(emptyRSM)
      msiEm.persist(msiMasterPepInstance)

      val masterPepInstanceId = msiMasterPepInstance.getId

      //VDS FIXME !!! CHANGE MERGE RSM !!!! 
      // Update the peptide instance id and some FKs
      masterPepInstance.id = masterPepInstanceId
      masterPepInstance.peptideMatchIds = Array(masterPepMatchId)
      masterPepInstance.bestPeptideMatchId = masterPepMatchId

      // TODO: remove this mapping when ORM is updated
      msiMasterPepInstById(masterPepInstanceId) = msiMasterPepInstance

      // Link the best master peptide match to the quant peptide instance
      val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
      msiPepInstMatchPK.setPeptideInstanceId(masterPepInstanceId)
      msiPepInstMatchPK.setPeptideMatchId(masterPepMatchId)

      val msiPepInstMatch = new MsiPepInstPepMatchMap()
      msiPepInstMatch.setId(msiPepInstMatchPK)
      msiPepInstMatch.setPeptideInstance(msiMasterPepInstance)
      msiPepInstMatch.setPeptideMatch(msiMasterPepMatch)
      msiPepInstMatch.setResultSummary(emptyRSM)

      //msiMasterPepInstance.setPeptidesMatches(Set(msiMasterPepMatch))
      msiEm.persist(msiPepInstMatch)

      // PeptideReadablePTMString
      if (peptide.readablePtmString != null && !peptide.readablePtmString.isEmpty()) {
        val msiPeptideReadablePtmStringPK = new MsiPeptideReadablePtmStringPK()
        msiPeptideReadablePtmStringPK.setPeptideId(peptideId)
        msiPeptideReadablePtmStringPK.setResultSetId(emptyRS.getId())

        val msiPeptideReadablePtmString = new MsiPeptideReadablePtmString()
        msiPeptideReadablePtmString.setId(msiPeptideReadablePtmStringPK)
        msiPeptideReadablePtmString.setResultSet(emptyRS)
        msiPeptideReadablePtmString.setPeptide(msiPep)
        msiPeptideReadablePtmString.setReadablePtmString(peptide.readablePtmString)

        // Save PeptideReadablePTMString
        msiEm.persist(msiPeptideReadablePtmString)
      }

      // Map this quant peptide match to identified child peptide matches    
      if (bestParentPepMatch.getChildrenIds != null) {
        for (childPepMatchId <- bestParentPepMatch.getChildrenIds) {
          val msiPepMatchRelationPK = new MsiPeptideMatchRelationPK()
          msiPepMatchRelationPK.setChildPeptideMatchId(childPepMatchId)
          msiPepMatchRelationPK.setParentPeptideMatchId(masterPepMatchId)

          val childPM = msiEm.find(classOf[MsiPeptideMatch], childPepMatchId)
          val msiPepMatchRelation = new MsiPeptideMatchRelation()
          msiPepMatchRelation.setId(msiPepMatchRelationPK)
          msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
          msiPepMatchRelation.setChildPeptideMatch(childPM)
          msiPepMatchRelation.setParentResultSetId(emptyRS)
          msiEm.persist(msiPepMatchRelation)
        }
      }
    }

    // Retrieve some vars
    val masterPeptideSets = sourceRSM.peptideSets
    this.logger.info("number of grouped peptide sets: " + masterPeptideSets.length)
    val masterProteinSets = sourceRSM.proteinSets
    this.logger.info("number of grouped protein sets: " + masterProteinSets.length)
    val masterProtSetByTmpId = sourceRSM.getProteinSetById
    val masterProtMatchByTmpId = sourceRSM.resultSet.get.getProteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets and protein sets...")
    for (masterPeptideSet <- masterPeptideSets) {

      val masterProtMatchIdByTmpId = new HashMap[Long, Long]
      val masterProtMatchTmpIdById = new HashMap[Long, Long]

      // Store master protein matches
      val msiMasterProtMatches = masterPeptideSet.proteinMatchIds.map { protMatchId =>

        val masterProtMatch = masterProtMatchByTmpId(protMatchId)

        val msiMasterProtMatch = new MsiProteinMatch()
        msiMasterProtMatch.setAccession(masterProtMatch.accession)
        msiMasterProtMatch.setDescription(masterProtMatch.description)
        msiMasterProtMatch.setGeneName(masterProtMatch.geneName)
        msiMasterProtMatch.setScore(masterProtMatch.score)
        msiMasterProtMatch.setCoverage(masterProtMatch.coverage)
        msiMasterProtMatch.setPeptideCount(masterProtMatch.sequenceMatches.length)
        msiMasterProtMatch.setPeptideMatchCount(masterProtMatch.peptideMatchesCount)
        msiMasterProtMatch.setIsDecoy(masterProtMatch.isDecoy)
        msiMasterProtMatch.setIsLastBioSequence(masterProtMatch.isLastBioSequence)
        msiMasterProtMatch.setTaxonId(masterProtMatch.taxonId)
        if (masterProtMatch.getProteinId > 0) msiMasterProtMatch.setBioSequenceId(masterProtMatch.getProteinId)
        // FIXME: retrieve the right scoring id
        msiMasterProtMatch.setScoringId(3)
        msiMasterProtMatch.setResultSet(emptyRS)
        msiEm.persist(msiMasterProtMatch)

        val masterProtMatchId = msiMasterProtMatch.getId

        // Map new protein match id by TMP id
        masterProtMatchIdByTmpId += masterProtMatch.id -> masterProtMatchId
        // Map protein match TMP id by the new id
        masterProtMatchTmpIdById += masterProtMatchId -> masterProtMatch.id

        //VDS FIXME !!! CHANGE MERGE RSM !!!! 
        // Update master protein match id
        masterProtMatch.id = masterProtMatchId

        // map protein_match to seq_databases
        val seqDBsIds = masterProtMatch.seqDatabaseIds
        for( seqDbId <- seqDBsIds) {
          
          // Link master protein match to master peptide set
          val msiProteinMatchSeqDatabaseMapPK = new ProteinMatchSeqDatabaseMapPK()
          msiProteinMatchSeqDatabaseMapPK.setProteinMatchId(masterProtMatchId)
          msiProteinMatchSeqDatabaseMapPK.setSeqDatabaseId(seqDbId)

          val msiProteinMatchSeqDatabaseMap = new ProteinMatchSeqDatabaseMap()
          msiProteinMatchSeqDatabaseMap.setId(msiProteinMatchSeqDatabaseMapPK)
          msiProteinMatchSeqDatabaseMap.setResultSetId(emptyRS)
          msiEm.persist(msiProteinMatchSeqDatabaseMap)
        }
        
        
        
        msiMasterProtMatch
      }

      // Map MSI protein matches by their id
      //val msiMasterProtMatchById = Map() ++ msiMasterProtMatches.map( pm => pm.getId -> pm )

      // Update master peptide set protein match ids
      //VDS FIXME !!! CHANGE MERGE RSM !!!! 
      masterPeptideSet.proteinMatchIds = masterPeptideSet.proteinMatchIds.map(masterProtMatchIdByTmpId(_))

      // TODO: find what to do with subsets
      if (masterPeptideSet.isSubset == false) {

        val masterProteinSetOpt = masterProtSetByTmpId.get(masterPeptideSet.getProteinSetId)
        assert(masterProteinSetOpt.isDefined, "missing protein set with id=" + masterPeptideSet.getProteinSetId)

        //////// Check if the protein set has at least a peptide instance with a relevant quantitation
        //val isProteinSetQuantitationRelevant = 0
        //for( tmpPepInstance <- samesetPeptideInstances ) {
        //  val rdbQuantPepInstance = quantPepByIdentPepId( tmpPepInstance.id )
        //  if( rdbQuantPepInstance.isQuantitationRelevant ) {
        //    isProteinSetQuantitationRelevant = 1
        //    last
        //  }
        //}

        // Determine the typical protein match id using the sequence coverage
        val masterProteinSet = masterProteinSetOpt.get
        var reprProtMatchId = masterProteinSet.getRepresentativeProteinMatchId

        if (reprProtMatchId <= 0) {
          val reprProtMatchTmpId = masterProteinSet.samesetProteinMatchIds.reduce { (a, b) =>
            if (masterProtMatchByTmpId(a).coverage > masterProtMatchByTmpId(b).coverage) a else b
          }
          reprProtMatchId = masterProtMatchIdByTmpId(reprProtMatchTmpId)
        }

        // Update master protein set protein match ids        
        masterProteinSet.samesetProteinMatchIds = masterProteinSet.peptideSet.proteinMatchIds

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(masterProteinSet.isValidated)
        msiMasterProteinSet.setSelectionLevel(2)
        msiMasterProteinSet.setProteinMatchId(reprProtMatchId)
        msiMasterProteinSet.setResultSummary(emptyRSM)
        msiEm.persist(msiMasterProteinSet)

        val masterProteinSetId = msiMasterProteinSet.getId
        //   msiMasterProtSetById(masterProteinSetId) = msiMasterProteinSet => FIXME MIGRATION SUPP

        // Update the master protein set id
        //VDS FIXME !!! CHANGE MERGE RSM !!!! 
        masterProteinSet.id = masterProteinSetId

        // Retrieve peptide set items
        val samesetItems = masterPeptideSet.items

        // Store master peptide set
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset(false)
        msiMasterPeptideSet.setPeptideCount(samesetItems.length)
        msiMasterPeptideSet.setPeptideMatchCount(masterPeptideSet.peptideMatchesCount)
        msiMasterPeptideSet.setSequenceCount(masterPeptideSet.sequencesCount)
        msiMasterPeptideSet.setProteinSet(msiMasterProteinSet)
        // FIXME: retrieve the right scoring id
        msiMasterPeptideSet.setScore(masterPeptideSet.score)
        msiMasterPeptideSet.setScoring(msiScoring)
        msiMasterPeptideSet.setResultSummaryId(quantRsmId)
        msiEm.persist(msiMasterPeptideSet)

        val masterPeptideSetId = msiMasterPeptideSet.getId

        //VDS FIXME !!! CHANGE MERGE RSM !!!! 
        // Update the master peptide set id
        masterPeptideSet.id = masterPeptideSetId

        // Link master peptide set to master peptide instances
        for (samesetItem <- samesetItems) {
          val tmpPepInstance = samesetItem.peptideInstance
          val msiMasterPepInst = msiMasterPepInstById(tmpPepInstance.id)

          val msiPepSetItemPK = new MsiPeptideSetItemPK()
          msiPepSetItemPK.setPeptideSetId(msiMasterPeptideSet.getId)
          msiPepSetItemPK.setPeptideInstanceId(msiMasterPepInst.getId)

          // TODO: change JPA definition to skip this double mapping
          val msiPepSetItem = new MsiPeptideSetItem()
          msiPepSetItem.setId(msiPepSetItemPK)
          msiPepSetItem.setPeptideSet(msiMasterPeptideSet)
          msiPepSetItem.setPeptideInstance(msiMasterPepInst)
          msiPepSetItem.setSelectionLevel(2)
          msiPepSetItem.setResultSummary(emptyRSM)

          msiEm.persist(msiPepSetItem)
        }

        for (msiMasterProtMatch <- msiMasterProtMatches) {

          val masterProtMatchId = msiMasterProtMatch.getId

          // TODO: Map master protein match to master peptide set => ORM has to be fixed
          /*val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          new Pairs::Msi::RDBO::PeptideSetProteinMatchMap(
                                  peptide_set_id = quantPeptideSetId,
                                  protein_match_id = quantProtMatchId,
                                  result_summary_id = quantRsmId,
                                  db = msiRdb
                                ).save*/

          // Link master protein match to master protein set
          val msiProtSetProtMatchItemPK = new MsiProtSetProtMatchItemPK()
          msiProtSetProtMatchItemPK.setProteinSetId(msiMasterProteinSet.getId)
          msiProtSetProtMatchItemPK.setProteinMatchId(msiMasterProtMatch.getId)

          // TODO: change JPA definition
          val msiProtSetProtMatchItem = new MsiProtSetProtMatchItem()
          msiProtSetProtMatchItem.setId(msiProtSetProtMatchItemPK)
          msiProtSetProtMatchItem.setProteinSet(msiMasterProteinSet)
          msiProtSetProtMatchItem.setProteinMatch(msiMasterProtMatch)
          msiProtSetProtMatchItem.setResultSummary(emptyRSM)
          msiEm.persist(msiProtSetProtMatchItem)

          // Link master protein match to master peptide set
          val msiPepSetProtMatchMapPK = new MsiPepSetProtMatchMapPK()
          msiPepSetProtMatchMapPK.setPeptideSetId(msiMasterPeptideSet.getId)
          msiPepSetProtMatchMapPK.setProteinMatchId(masterProtMatchId)

          val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          msiPepSetProtMatchMap.setId(msiPepSetProtMatchMapPK)
          msiPepSetProtMatchMap.setPeptideSet(msiMasterPeptideSet)
          msiPepSetProtMatchMap.setProteinMatch(msiMasterProtMatch)
          msiPepSetProtMatchMap.setResultSummary(emptyRSM)
          msiEm.persist(msiPepSetProtMatchMap)

          // Link master protein match to master peptide matches using master sequence matches
          val masterProtMatch = masterProtMatchByTmpId(masterProtMatchTmpIdById(masterProtMatchId))
          val seqMatches = masterProtMatch.sequenceMatches
          val mappedMasterPepMatchesIdSet = new HashSet[Long]

          for (seqMatch <- seqMatches) {

            val bestPepMatchId = seqMatch.getBestPeptideMatchId
            if (masterQuantPepMatchIdByTmpPepMatchId.contains(bestPepMatchId)) {
              val masterPepMatchId = masterQuantPepMatchIdByTmpPepMatchId(bestPepMatchId)

              // Update seqMatch best peptide match id
              //VDS FIXME !!! CHANGE MERGE RSM !!!! 
              seqMatch.bestPeptideMatchId = masterPepMatchId

              if (mappedMasterPepMatchesIdSet.contains(masterPepMatchId) == false) {
                mappedMasterPepMatchesIdSet(masterPepMatchId) = true

                val msiMasterSeqMatchPK = new fr.proline.core.orm.msi.SequenceMatchPK()
                msiMasterSeqMatchPK.setProteinMatchId(masterProtMatchId)
                msiMasterSeqMatchPK.setPeptideId(seqMatch.getPeptideId)
                msiMasterSeqMatchPK.setStart(seqMatch.start)
                msiMasterSeqMatchPK.setStop(seqMatch.end)

                val msiMasterSeqMatch = new MsiSequenceMatch()
                msiMasterSeqMatch.setId(msiMasterSeqMatchPK)
                msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(seqMatch.residueBefore))
                msiMasterSeqMatch.setResidueAfter(scalaCharToCharacter(seqMatch.residueAfter))
                msiMasterSeqMatch.setIsDecoy(false)
                msiMasterSeqMatch.setBestPeptideMatchId(masterPepMatchId)
                msiMasterSeqMatch.setResultSetId(quantRsId)
                msiEm.persist(msiMasterSeqMatch)

              }
            }
          }
        }
      } //END if masterPeptideSet.isSubset == false
    } //END go through masterPeptideSets

    sourceRSM.id = emptyRSM.getId
    sourceRSM
  }

}