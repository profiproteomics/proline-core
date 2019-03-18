package fr.proline.core.om.storer.msi.impl

import java.util

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet}
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.om.model.msi.{PeptideInstance, PeptideMatch, PeptideSet, PeptideSetItem, ProteinMatch, ResultSummary}
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.storer.msi.IRsmDuplicator
import fr.proline.core.orm.msi.Peptide
import fr.proline.core.orm.msi.MsQuery
import fr.proline.core.orm.msi.{PeptideInstance => MsiPeptideInstance}
import fr.proline.core.orm.msi.{PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap}
import fr.proline.core.orm.msi.{PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK}
import fr.proline.core.orm.msi.{PeptideMatch => MsiPeptideMatch}
import fr.proline.core.orm.msi.{PeptideMatchRelation => MsiPeptideMatchRelation}
import fr.proline.core.orm.msi.{PeptideMatchRelationPK => MsiPeptideMatchRelationPK}
import fr.proline.core.orm.msi.{PeptideReadablePtmString => MsiPeptideReadablePtmString}
import fr.proline.core.orm.msi.{PeptideReadablePtmStringPK => MsiPeptideReadablePtmStringPK}
import fr.proline.core.orm.msi.{PeptideSet => MsiPeptideSet}
import fr.proline.core.orm.msi.{PeptideSetPeptideInstanceItem => MsiPeptideSetItem}
import fr.proline.core.orm.msi.{PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK}
import fr.proline.core.orm.msi.{PeptideSetProteinMatchMap => MsiPepSetProtMatchMap}
import fr.proline.core.orm.msi.{PeptideSetProteinMatchMapPK => MsiPepSetProtMatchMapPK}
import fr.proline.core.orm.msi.{PeptideSetRelationPK => MsiPeptideSetRelationPK}
import fr.proline.core.orm.msi.{PeptideSetRelation => MsiPeptideSetRelation}
import fr.proline.core.orm.msi.{ProteinMatch => MsiProteinMatch}
import fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMap
import fr.proline.core.orm.msi.ProteinMatchSeqDatabaseMapPK
import fr.proline.core.orm.msi.{ProteinSet => MsiProteinSet}
import fr.proline.core.orm.msi.{ProteinSetProteinMatchItem => MsiProtSetProtMatchItem}
import fr.proline.core.orm.msi.{ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK}
import fr.proline.core.orm.msi.{ResultSet => MsiResultSet}
import fr.proline.core.orm.msi.{ResultSummary => MsiResultSummary}
import fr.proline.core.orm.msi.{Scoring => MsiScoring}
import fr.proline.core.orm.msi.{SequenceMatch => MsiSequenceMatch}
import fr.proline.core.orm.msi.SequenceMatchPK
import fr.proline.core.util.ResidueUtils.scalaCharToCharacter
import javax.persistence.{EntityManager, TypedQuery}
import fr.proline.core.orm.msi.repository.ScoringRepository

class RsmDuplicator(rsmProvider: IResultSummaryProvider) extends IRsmDuplicator with LazyLogging {

  override def cloneAndStoreRSM(sourceRSM: ResultSummary, emptyRSM: MsiResultSummary, emptyRS: MsiResultSet, eraseSourceIds: Boolean, msiEm: EntityManager): ResultSummary = {
    cloneAndStoreRSMWithSubset(sourceRSM, emptyRSM, emptyRS, eraseSourceIds, msiEm)
  }

  def cloneAndStoreRSMWithSubset(sourceRSM: ResultSummary, emptyRSM: MsiResultSummary, emptyRS: MsiResultSet, eraseSourceIds: Boolean, msiEm: EntityManager): ResultSummary = {
    var start = System.currentTimeMillis()

    // Retrieve result summary and result set ids
    val quantRsmId = emptyRSM.getId
    val quantRsId = emptyRS.getId

    // Retrieve peptide instances of the merged result summary
    val sourcePepInstances: Array[PeptideInstance] = sourceRSM.peptideInstances
    val sourcePepMatchById: Map[Long, PeptideMatch] = sourceRSM.resultSet.get.getPeptideMatchById()

    // Get Default Scoring : Mascot Standard
    //VDS FIXME which default ?!
    MsiScoring.Type.MASCOT_STANDARD_SCORE.toString
    val defaultScoringId = ScoringRepository.getScoringIdForType(msiEm, MsiScoring.Type.MASCOT_STANDARD_SCORE.toString)


    // Iterate over merged peptide instances to create quant peptide instances
    var end = System.currentTimeMillis()
    this.logger.info("cloning master quant peptide instances... (" + sourcePepInstances.length + "). From start method " + (end - start) + " ms")
    start = end

    // Define some vars
    val masterQuantPepMatchIdByMergedPepMatchId = new HashMap[Long, Long]
    val msiMasterPepInstByPepInstId = new HashMap[Long, MsiPeptideInstance] //could be by initial SourcePeptideInsID or by update SourcePeptideInsID (ResetID)


    //--- create Map of needed peptide and peptide match id in few queries instead of query in loop
    val peptideByIds = new mutable.HashMap[Long, Peptide]()
    val peptideMatchByIds = new mutable.HashMap[Long, MsiPeptideMatch]()

    val pepIds = new ArrayBuffer[Long]()
    val pepMatchIds = new ArrayBuffer[Long]()
    for (sourcePepInstance <- sourcePepInstances) {
      pepIds += sourcePepInstance.peptide.id
      val sourcePepInstPepMatchIds = sourcePepInstance.getPeptideMatchIds()
      for (mergedPepMatchId <- sourcePepInstPepMatchIds) {
        val childIds = sourcePepMatchById(mergedPepMatchId).getChildrenIds
        if (childIds != null && childIds.length > 0)
          pepMatchIds ++= childIds
      }
    }

    val pepQuery: TypedQuery[Peptide] = msiEm.createQuery("Select p FROM fr.proline.core.orm.msi.Peptide p WHERE id in ( " + pepIds.mkString(",") + " )", classOf[Peptide])
    val queryPeptideIt: util.Iterator[Peptide] = pepQuery.getResultList.iterator()
    while (queryPeptideIt.hasNext) {
      val qPep = queryPeptideIt.next()
      peptideByIds += (qPep.getId -> qPep)
    }

    if (!pepMatchIds.isEmpty) {
      val pepMatchQuery: TypedQuery[MsiPeptideMatch] = msiEm.createQuery("Select pm FROM PeptideMatch pm WHERE id in ( " + pepMatchIds.mkString(",") + " )", classOf[MsiPeptideMatch])
      val queryPeptideMatchIt: util.Iterator[MsiPeptideMatch] = pepMatchQuery.getResultList.iterator()
      while (queryPeptideMatchIt.hasNext) {
        val qPepMatch = queryPeptideMatchIt.next()
        peptideMatchByIds += (qPepMatch.getId -> qPepMatch)
      }
    }

    end  = System.currentTimeMillis()
    this.logger.debug("getting all peptides ("+ pepIds.length+") and child peptidesMatch in 2 queries... (" + pepMatchIds.length + "). duration: "+(end-start) + " ms")
    start = end

    var cumul1 = 0l

    for (sourcePepInstance <- sourcePepInstances) {
      val peptide = sourcePepInstance.peptide
      val peptideId = peptide.id
      val sourcePepInstPepMatchIds = sourcePepInstance.getPeptideMatchIds()
      val msiPepMatchesIds = new ArrayBuffer[Long]()
      val msiPepMatches = new ArrayBuffer[MsiPeptideMatch]()

      var bestPepMatchId: Long= -1
      //Get list of

      for (mergedPepMatchId <- sourcePepInstPepMatchIds) {
        val mergedPepMatch = sourcePepMatchById(mergedPepMatchId)

        // Create a quant peptide match which correspond to the current merged peptide match of this peptide instance
        val msiMasterPepMatch = new MsiPeptideMatch()
        msiMasterPepMatch.setCharge(mergedPepMatch.msQuery.charge)
        msiMasterPepMatch.setExperimentalMoz(mergedPepMatch.msQuery.moz)
        msiMasterPepMatch.setScore(mergedPepMatch.score)
        msiMasterPepMatch.setRank(mergedPepMatch.rank)
        msiMasterPepMatch.setCDPrettyRank(mergedPepMatch.cdPrettyRank)
        msiMasterPepMatch.setSDPrettyRank(mergedPepMatch.sdPrettyRank)
        msiMasterPepMatch.setDeltaMoz(mergedPepMatch.deltaMoz)
        msiMasterPepMatch.setMissedCleavage(mergedPepMatch.missedCleavage)
        msiMasterPepMatch.setFragmentMatchCount(mergedPepMatch.fragmentMatchesCount)
        msiMasterPepMatch.setIsDecoy(false)
        msiMasterPepMatch.setPeptideId(peptideId)

        // retrieve the right scoring_id
        val scoreType = mergedPepMatch.scoreType.toString
        var msiScoringId = ScoringRepository.getScoringIdForType(msiEm, scoreType)
        if (msiScoringId == null) {
          msiScoringId = defaultScoringId
        }
        msiMasterPepMatch.setScoringId(msiScoringId)

        // FIXME: change the ORM to allow these mappings
        //msiMasterPepMatch.setBestPeptideMatchId(bestPepMatch.id)
        //msiMasterPepMatch.setMsQueryId(bestPepMatch.msQueryId)

        // FIXME: remove this mapping when the ORM is updated
        val msiMSQFake = new MsQuery
        msiMSQFake.setId(mergedPepMatch.msQuery.id)
        msiMasterPepMatch.setMsQuery(msiMSQFake)

        msiMasterPepMatch.setResultSet(emptyRS)
        mergedPepMatch.properties.map(props => msiMasterPepMatch.setSerializedProperties(ProfiJson.serialize(props)))

        // Save master peptide match
        msiEm.persist(msiMasterPepMatch)

        val msiMasterPepMatchId = msiMasterPepMatch.getId
        msiPepMatchesIds += msiMasterPepMatchId
        msiPepMatches  += msiMasterPepMatch

        if (mergedPepMatchId.equals(sourcePepInstance.bestPeptideMatchId)) {
          bestPepMatchId = msiMasterPepMatchId //save bestPeptideMatchId
        }

        // Map master peptide match id by in memory merged peptide match id
        masterQuantPepMatchIdByMergedPepMatchId(mergedPepMatch.id) = msiMasterPepMatchId

        //VDS !!! CHANGE MERGE RSM !!!
        //ONLY FOR RESET IDS
        if (eraseSourceIds)
          mergedPepMatch.id = msiMasterPepMatchId

        // Map this quant peptide match to identified child peptide matches
        if (mergedPepMatch.getChildrenIds != null) {
          for (childPepMatchId <- mergedPepMatch.getChildrenIds) {

            val msiPepMatchRelationPK = new MsiPeptideMatchRelationPK()
            msiPepMatchRelationPK.setChildPeptideMatchId(childPepMatchId)
            msiPepMatchRelationPK.setParentPeptideMatchId(msiMasterPepMatchId)

            val msiPepMatchRelation = new MsiPeptideMatchRelation()
            msiPepMatchRelation.setId(msiPepMatchRelationPK)
            msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
//            val childPM: MsiPeptideMatch = msiEm.find(classOf[MsiPeptideMatch], childPepMatchId)
            val childPM: MsiPeptideMatch = peptideMatchByIds.getOrElse(childPepMatchId, null)
            msiPepMatchRelation.setChildPeptideMatch(childPM)
            msiPepMatchRelation.setParentResultSetId(emptyRS)

            msiEm.persist(msiPepMatchRelation)

          }
        }
      } //End go through peptideInstance's peptideMatch


      //Retrieve ORM Peptide and create PeptideInstance
      val msiPep = peptideByIds(peptideId)
      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(sourcePepInstPepMatchIds.length)
      msiMasterPepInstance.setProteinMatchCount(sourcePepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(sourcePepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(sourcePepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(sourcePepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptide(msiPep)
      msiMasterPepInstance.setBestPeptideMatchId(bestPepMatchId)
      msiMasterPepInstance.setResultSummary(emptyRSM)
      msiEm.persist(msiMasterPepInstance)

      val msiMasterPepInstanceId = msiMasterPepInstance.getId

      //VDS !!! CHANGE MERGE RSM !!!
      //ONLY FOR RESET IDS
      // Update the peptide instance id and some FKs
      if (eraseSourceIds) {
        sourcePepInstance.id = msiMasterPepInstanceId
        sourcePepInstance.peptideMatchIds = msiPepMatchesIds.toArray
        sourcePepInstance.bestPeptideMatchId = bestPepMatchId
        msiMasterPepInstByPepInstId += (sourcePepInstance.id -> msiMasterPepInstance)
      } else {
        // FOR READBACK ONLY this
        msiMasterPepInstByPepInstId += (sourcePepInstance.id -> msiMasterPepInstance)
      }

      // Link the peptide match to the peptide instance
      for(msiPepMatch <- msiPepMatches) {
        val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
        msiPepInstMatchPK.setPeptideInstanceId(msiMasterPepInstanceId)
        msiPepInstMatchPK.setPeptideMatchId(msiPepMatch.getId)

        val msiPepInstMatch = new MsiPepInstPepMatchMap()
        msiPepInstMatch.setId(msiPepInstMatchPK)
        msiPepInstMatch.setPeptideInstance(msiMasterPepInstance)
        msiPepInstMatch.setPeptideMatch(msiPepMatch)
        msiPepInstMatch.setResultSummary(emptyRSM)

        msiEm.persist(msiPepInstMatch)
      }

      // PeptideReadablePTMString
      if (peptide.readablePtmString != null && !peptide.readablePtmString.isEmpty) {
        val msiPeptideReadablePtmStringPK = new MsiPeptideReadablePtmStringPK()
        msiPeptideReadablePtmStringPK.setPeptideId(peptideId)
        msiPeptideReadablePtmStringPK.setResultSetId(emptyRS.getId)

        val msiPeptideReadablePtmString = new MsiPeptideReadablePtmString()
        msiPeptideReadablePtmString.setId(msiPeptideReadablePtmStringPK)
        msiPeptideReadablePtmString.setResultSet(emptyRS)
        msiPeptideReadablePtmString.setPeptide(msiPep)
        msiPeptideReadablePtmString.setReadablePtmString(peptide.readablePtmString)

        // Save PeptideReadablePTMString
        msiEm.persist(msiPeptideReadablePtmString)
      }

    } //--- End go through PepInstance

    end  = System.currentTimeMillis()
    this.logger.debug(" ---  End go through PepInstance; duration "+(end-start))
    start = end
    // Retrieve some vars
    val sourcePeptideSets = sourceRSM.peptideSets
    this.logger.debug("number of grouped peptide sets: " + sourcePeptideSets.length + " sameset " + sourcePeptideSets.filter(!_.isSubset).length)
    val sourceProteinSets = sourceRSM.proteinSets
    this.logger.debug("number of grouped protein sets: " + sourceProteinSets.length)
    val sourceProtSetById = sourceRSM.getProteinSetById
    val sourceProtMatchById = sourceRSM.resultSet.get.getProteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets and protein sets...")
    val (subSetSourcePeptideSets, sameSetSourcePeptideSets) = sourcePeptideSets.partition(_.isSubset)
    val masterPepSetsByMergedPepSetId = new HashMap[Long, MsiPeptideSet]
    val masterProtSetsByMergedSubPepSetId = new HashMap[Long, ArrayBuffer[MsiProteinSet]]

    //Start creating sameSet peptideSet data
    for (sourcePeptideSet <- sameSetSourcePeptideSets) {

      val msiMasterProtMatchIdBySourceId = new HashMap[Long, Long]
      val sourceProtMatchIdByMasterId = new HashMap[Long, Long]

      // Store master protein matches
      val msiMasterProtMatches: Array[MsiProteinMatch] = createProteinMatches(msiEm, emptyRS, sourcePeptideSet, msiMasterProtMatchIdBySourceId, sourceProtMatchIdByMasterId, sourceProtMatchById, defaultScoringId, eraseSourceIds)

      //VDS !!! CHANGE MERGE RSM !!!!
      // Update master peptide set protein match ids with quant ones
      //ONLY FOR RESET IDS
      if (eraseSourceIds)
        sourcePeptideSet.proteinMatchIds = sourcePeptideSet.proteinMatchIds.map(msiMasterProtMatchIdBySourceId(_))

      //--  Create ProteinSet data -----------
      val sourceProteinSetOpt = sourceProtSetById.get(sourcePeptideSet.getProteinSetId)
      assert(sourceProteinSetOpt.isDefined, "missing protein set with id=" + sourcePeptideSet.getProteinSetId)

      // Determine the typical protein match id using the sequence coverage
      val mergedProteinSet = sourceProteinSetOpt.get
      var mergedTypicalProtMatchId = mergedProteinSet.getRepresentativeProteinMatchId

      if (mergedTypicalProtMatchId <= 0) {
        // Choose Typical using arbitrary alphabetical order : Same as Merger algo !
        mergedTypicalProtMatchId = mergedProteinSet.samesetProteinMatchIds.reduce { (a, b) =>
          if (sourceProtMatchById(a).accession < sourceProtMatchById(b).accession) a else b
        }
      }

      //VDS !!! CHANGE MERGE RSM !!!!
      //   Update source master protein set protein match ids
      //ONLY FOR RESET IDS
      if (eraseSourceIds)
        mergedProteinSet.samesetProteinMatchIds = mergedProteinSet.peptideSet.proteinMatchIds

      // Get MSI ProtMatch ID from map: not the same as Source ProtMatch ID
      val typicalProtMatchId = if (eraseSourceIds) {
        //ONLY FOR RESET IDS
        mergedTypicalProtMatchId
      } else {
        //ONLY FOR READBACK
        msiMasterProtMatchIdBySourceId(mergedTypicalProtMatchId)
      }

      // Store master protein set
      val msiMasterProteinSet = new MsiProteinSet()
      msiMasterProteinSet.setIsValidated(mergedProteinSet.isValidated)
      msiMasterProteinSet.setIsDecoy(mergedProteinSet.isDecoy)
      msiMasterProteinSet.setSelectionLevel(2)
      msiMasterProteinSet.setProteinMatchId(typicalProtMatchId)
      msiMasterProteinSet.setResultSummary(emptyRSM)
      msiEm.persist(msiMasterProteinSet)

      val msiMasterProteinSetId = msiMasterProteinSet.getId

      //VDS CHANGE MERGE RSM !!!!
      // Update the master protein set id
      //ONLY FOR RESET IDS
      if (eraseSourceIds)
        mergedProteinSet.id = msiMasterProteinSetId


      // Retrieve peptide set items
      val peptideSetItems = sourcePeptideSet.items

      val scoreType = sourcePeptideSet.scoreType
      var msiPepSetScoringId = ScoringRepository.getScoringIdForType(msiEm, scoreType)
      //VDS FIXME which default ?!
      if (msiPepSetScoringId == null) {
        msiPepSetScoringId = defaultScoringId
      }

      // Store master peptide set
      val msiMasterPeptideSet = createMsiPepSetAndItems(msiEm , quantRsmId,emptyRSM, sourcePeptideSet, Some(msiMasterProteinSet),msiPepSetScoringId, peptideSetItems, msiMasterPepInstByPepInstId, masterPepSetsByMergedPepSetId, eraseSourceIds)

      //Save link between subset PeptideSet and MasterProtSets
      if(sourcePeptideSet.getStrictSubsetIds() != null){
        sourcePeptideSet.getStrictSubsetIds().foreach(subSetId => {
          masterProtSetsByMergedSubPepSetId.getOrElseUpdate(subSetId, new ArrayBuffer[MsiProteinSet]()) += msiMasterProteinSet
        })
      }

      // Link master peptide set to master protein matches
      val msiMasterProteinSets = ArrayBuffer[MsiProteinSet]()
      msiMasterProteinSets += msiMasterProteinSet
      createProteinMatchesLinks(msiEm, msiMasterProtMatches,msiMasterProteinSets, msiMasterPeptideSet,
                                sourceProtMatchIdByMasterId, sourceProtMatchById, masterQuantPepMatchIdByMergedPepMatchId,
                                  msiMasterPepInstByPepInstId, emptyRSM, quantRsId, eraseSourceIds);

    } //End go through sameSet PeptideSet
    end  = System.currentTimeMillis()
    this.logger.info("END storing quantified sameset peptide sets and protein sets... steps duration "+(end-start) + " ms")
    start = end

    //Start creating subSet peptideSet data
    for (sourcePeptideSet <- subSetSourcePeptideSets) {

      val msiMasterProtMatchIdBySourceId = new HashMap[Long, Long]
      val sourceProtMatchIdByMasterId = new HashMap[Long, Long]

      // Store master protein matches
      val msiMasterProtMatches: Array[MsiProteinMatch] = createProteinMatches(msiEm, emptyRS, sourcePeptideSet, msiMasterProtMatchIdBySourceId, sourceProtMatchIdByMasterId, sourceProtMatchById, defaultScoringId, eraseSourceIds)

      //VDS !!! CHANGE MERGE RSM !!!!
      // Update master peptide set protein match ids with quant ones
      //ONLY FOR RESET IDS
      if (eraseSourceIds)
        sourcePeptideSet.proteinMatchIds = sourcePeptideSet.proteinMatchIds.map(msiMasterProtMatchIdBySourceId(_))

      // Retrieve peptide set items
      val peptideSetItems = sourcePeptideSet.items

      val scoreType = sourcePeptideSet.scoreType
      var msiPepSetScoringId = ScoringRepository.getScoringIdForType(msiEm, scoreType)
      //VDS FIXME which default ?!
      if (msiPepSetScoringId == null) {
        msiPepSetScoringId = defaultScoringId
      }

      //Get ProteinSet to link this Peptide set to
      val protSetsToLinkTo: ArrayBuffer[MsiProteinSet] = masterProtSetsByMergedSubPepSetId.getOrElse(sourcePeptideSet.id, ArrayBuffer.empty[MsiProteinSet])
      // Store peptide set
      val msiMasterPeptideSet = createMsiPepSetAndItems(msiEm, quantRsmId, emptyRSM, sourcePeptideSet, None, msiPepSetScoringId, peptideSetItems, msiMasterPepInstByPepInstId, masterPepSetsByMergedPepSetId, eraseSourceIds)
      // Link master peptide set to master protein matches
      createProteinMatchesLinks(msiEm, msiMasterProtMatches, protSetsToLinkTo, msiMasterPeptideSet,
        sourceProtMatchIdByMasterId, sourceProtMatchById, masterQuantPepMatchIdByMergedPepMatchId,
        msiMasterPepInstByPepInstId, emptyRSM, quantRsId, eraseSourceIds);

    }  //End go through subSets PeptideSet

    end  = System.currentTimeMillis()
    this.logger.info("END storing quantified subset peptide sets and protein sets... steps duration "+(end-start) + " ms")
    start = end
    //Create PeptideSet Relations
    for(sourcePepSet <- sourcePeptideSets){
      val msiPepSet : MsiPeptideSet = if(eraseSourceIds) masterPepSetsByMergedPepSetId.values.filter(_.getId.equals(sourcePepSet.id)).head else masterPepSetsByMergedPepSetId(sourcePepSet.id)

      val subSetIds = if(sourcePepSet.strictSubsetIds != null) sourcePepSet.strictSubsetIds else Array.empty[Long]

      for (subSetId <- subSetIds){
        val msiPepSetSubSet = masterPepSetsByMergedPepSetId(subSetId)
        val msiPeptideSetRelationPK = new MsiPeptideSetRelationPK()
        msiPeptideSetRelationPK.setPeptideOversetId(msiPepSet.getId)
        msiPeptideSetRelationPK.setPeptideSubsetId(msiPepSetSubSet.getId)

        val msiPeptideSetRelation = new MsiPeptideSetRelation()
        msiPeptideSetRelation.setId(msiPeptideSetRelationPK)
        msiPeptideSetRelation.setIsStrictSubset(true)
        msiPeptideSetRelation.setOverset(msiPepSet)
        msiPeptideSetRelation.setSubset(msiPepSetSubSet)
        msiPeptideSetRelation.setResultSummaryId(emptyRSM)

        msiEm.persist(msiPeptideSetRelation)
      }

      val subsummableSetIds = if(sourcePepSet.subsumableSubsetIds != null) sourcePepSet.subsumableSubsetIds else Array.empty[Long]
      for (subSetId <- subsummableSetIds){
        val msiPepSetSubSet = masterPepSetsByMergedPepSetId(subSetId)
        val msiPeptideSetRelationPK = new MsiPeptideSetRelationPK()
        msiPeptideSetRelationPK.setPeptideOversetId(msiPepSet.getId)
        msiPeptideSetRelationPK.setPeptideSubsetId(msiPepSetSubSet.getId)

        val msiPeptideSetRelation = new MsiPeptideSetRelation()
        msiPeptideSetRelation.setId(msiPeptideSetRelationPK)
        msiPeptideSetRelation.setIsStrictSubset(false)
        msiPeptideSetRelation.setOverset(msiPepSet)
        msiPeptideSetRelation.setSubset(msiPepSetSubSet)
        msiPeptideSetRelation.setResultSummaryId(emptyRSM)

        msiEm.persist(msiPeptideSetRelation)
      }

    }

    end  = System.currentTimeMillis()
    this.logger.info("END storing  peptide sets relations ... steps duration "+(end-start) + " ms")
    start = end
    msiEm.flush()
    end  = System.currentTimeMillis()
    this.logger.info("ENtity manager flushed. duration "+(end-start) + " ms")
    //VDS CHANGE MERGE RSM !!!!
    // Update sourceRSM id
    //ONLY FOR RESET ID
    if (eraseSourceIds) {
      logger.info("Erase source RSM id")
      sourceRSM.id = emptyRSM.getId
      sourceRSM
    } else {
      //  ONLY FOR READBACK
      logger.info("read back source RSM")
      rsmProvider.getResultSummary(emptyRSM.getId, true).get
    }
  }

  private def createMsiPepSetAndItems( msiEm : EntityManager,
                            quantRsmId : Long,
                            emptyRSM : MsiResultSummary,
                            sourcePeptideSet : PeptideSet,
                            msiMasterProteinSetOp : Option[MsiProteinSet],
                            msiPepSetScoringId : Long,
                            peptideSetItems: Array[PeptideSetItem],
                            msiMasterPepInstByPepInstId : HashMap[Long, MsiPeptideInstance],
                            masterPepSetsByMergedPepSetId2Fill : HashMap[Long,MsiPeptideSet],
                            eraseSourceIds : Boolean ) : MsiPeptideSet = {

    val msiPepScoring = new MsiScoring()
    msiPepScoring.setId(msiPepSetScoringId)

    val msiMasterPeptideSet = new MsiPeptideSet()
    msiMasterPeptideSet.setIsSubset(sourcePeptideSet.isSubset)
    msiMasterPeptideSet.setPeptideCount(peptideSetItems.length)
    msiMasterPeptideSet.setPeptideMatchCount(sourcePeptideSet.peptideMatchesCount)
    msiMasterPeptideSet.setSequenceCount(sourcePeptideSet.sequencesCount)
    msiMasterPeptideSet.setScore(sourcePeptideSet.score)
    msiMasterPeptideSet.setScoring(msiPepScoring)
    msiMasterPeptideSet.setResultSummaryId(quantRsmId)
    if(msiMasterProteinSetOp.isDefined){
      msiMasterPeptideSet.setProteinSet(msiMasterProteinSetOp.get)
    }
    msiEm.persist(msiMasterPeptideSet)

    val msiMasterPeptideSetId = msiMasterPeptideSet.getId
    masterPepSetsByMergedPepSetId2Fill.put(sourcePeptideSet.id , msiMasterPeptideSet)

    //VDS  !!! CHANGE MERGE RSM !!!!
    // Update the master peptide set id
    //ONLY FOR RESET IDS
    if (eraseSourceIds)
      sourcePeptideSet.id = msiMasterPeptideSetId

    // Link master peptide set to master peptide instances
    for (sameSetItem <- peptideSetItems) {
      val mergedSameSetPepInst = sameSetItem.peptideInstance
      val msiMasterPepInst = msiMasterPepInstByPepInstId(mergedSameSetPepInst.id)

      val msiPepSetItemPK = new MsiPeptideSetItemPK()
      msiPepSetItemPK.setPeptideSetId(msiMasterPeptideSetId)
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
    msiMasterPeptideSet
  }

  private def createProteinMatchesLinks(msiEm : EntityManager,
                                        msiMasterProtMatches : Array[MsiProteinMatch],
                                        msiMasterProteinSets :ArrayBuffer[MsiProteinSet],
                                        msiMasterPeptideSet : MsiPeptideSet,
                                        sourceProtMatchIdByMasterId : HashMap[Long, Long],
                                        sourceProtMatchById:  Map[Long, ProteinMatch],
                                        masterQuantPepMatchIdByMergedPepMatchId : HashMap[Long, Long],
                                        msiMasterPepInstByPepInstId : HashMap[Long, MsiPeptideInstance],
                                        emptyRSM : MsiResultSummary,
                                        quantRsId : Long,
                                        eraseSourceIds : Boolean) = {

    for (msiMasterProtMatch <- msiMasterProtMatches) {

      val msiMasterProtMatchId = msiMasterProtMatch.getId

      // TODO: Map master protein match to master peptide set => ORM has to be fixed
      /*val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
      new Pairs::Msi::RDBO::PeptideSetProteinMatchMap(
                                peptide_set_id = quantPeptideSetId,
                              protein_match_id = quantProtMatchId,
                              result_summary_id = quantRsmId,
                                db = msiRdb
                            ).save*/

      msiMasterProteinSets.foreach( msiProtSet =>{
        // Link master protein match to master protein set
        val msiProtSetProtMatchItemPK = new MsiProtSetProtMatchItemPK()
        msiProtSetProtMatchItemPK.setProteinSetId(msiProtSet.getId)
        msiProtSetProtMatchItemPK.setProteinMatchId(msiMasterProtMatchId)

        // TODO: change JPA definition
        val msiProtSetProtMatchItem = new MsiProtSetProtMatchItem()
        msiProtSetProtMatchItem.setId(msiProtSetProtMatchItemPK)
        msiProtSetProtMatchItem.setProteinSet(msiProtSet)
        msiProtSetProtMatchItem.setProteinMatch(msiMasterProtMatch)
        msiProtSetProtMatchItem.setResultSummary(emptyRSM)
        msiProtSetProtMatchItem.setIsInSubset(msiMasterPeptideSet.getIsSubset)
        msiEm.persist(msiProtSetProtMatchItem)

      })

      // Link master protein match to master peptide set
      val msiPepSetProtMatchMapPK = new MsiPepSetProtMatchMapPK()
      msiPepSetProtMatchMapPK.setPeptideSetId(msiMasterPeptideSet.getId)
      msiPepSetProtMatchMapPK.setProteinMatchId(msiMasterProtMatchId)

      val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
      msiPepSetProtMatchMap.setId(msiPepSetProtMatchMapPK)
      msiPepSetProtMatchMap.setPeptideSet(msiMasterPeptideSet)
      msiPepSetProtMatchMap.setProteinMatch(msiMasterProtMatch)
      msiPepSetProtMatchMap.setResultSummary(emptyRSM)
      msiEm.persist(msiPepSetProtMatchMap)

      // Link master protein match to master peptide matches using master sequence matches
      val sourceProtMatch = sourceProtMatchById(sourceProtMatchIdByMasterId(msiMasterProtMatchId))
      val sourceSeqMatches = sourceProtMatch.sequenceMatches
      val mappedMasterPepMatchesIdSet = new HashSet[Long]

      for (sourceSeqMatch <- sourceSeqMatches) {

        val bestPepMatchId = sourceSeqMatch.getBestPeptideMatchId
        //Get masterBestPepMatchID. Check in validated peptideMatch and if not define get associated PeptideInstance's best Peptidematch
        val masterPepMatchId =  if (masterQuantPepMatchIdByMergedPepMatchId.contains(bestPepMatchId)) masterQuantPepMatchIdByMergedPepMatchId(bestPepMatchId) else {
          val correspondingPepInst = msiMasterPepInstByPepInstId.values.filter(_.getPeptide.getId.equals(sourceSeqMatch.getPeptideId()))
          if(correspondingPepInst.nonEmpty)
            correspondingPepInst.head.getBestPeptideMatchId
          else -1l
        }

        if (masterPepMatchId > 0) {
          // val masterPepMatchId = masterQuantPepMatchIdByMergedPepMatchId(bestPepMatchId)

          //VDS  !!! CHANGE MERGE RSM !!!!
          // Update seqMatch best peptide match id
          //ONLY FOR RESET ID
          if (eraseSourceIds)
            sourceSeqMatch.bestPeptideMatchId = masterPepMatchId

          if (!mappedMasterPepMatchesIdSet.contains(masterPepMatchId)) {
            mappedMasterPepMatchesIdSet.add(masterPepMatchId)

            val msiMasterSeqMatchPK = new SequenceMatchPK()
            msiMasterSeqMatchPK.setProteinMatchId(msiMasterProtMatchId)
            msiMasterSeqMatchPK.setPeptideId(sourceSeqMatch.getPeptideId)
            msiMasterSeqMatchPK.setStart(sourceSeqMatch.start)
            msiMasterSeqMatchPK.setStop(sourceSeqMatch.end)

            val msiMasterSeqMatch = new MsiSequenceMatch()
            msiMasterSeqMatch.setId(msiMasterSeqMatchPK)
            msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(sourceSeqMatch.residueBefore))
            msiMasterSeqMatch.setResidueAfter(scalaCharToCharacter(sourceSeqMatch.residueAfter))
            msiMasterSeqMatch.setIsDecoy(false)
            msiMasterSeqMatch.setBestPeptideMatchId(masterPepMatchId)
            msiMasterSeqMatch.setResultSetId(quantRsId)
            msiEm.persist(msiMasterSeqMatch)

          }
        }
      }
    }
  }

  private def createProteinMatches( msiEm : EntityManager,
                         emptyRS: MsiResultSet,
                         sourcePeptideSet : PeptideSet,
                         masterProtMatchIdBySourceId2Fill : HashMap[Long, Long],
                         sourceProtMatchIdByMasterId2Fill : HashMap[Long, Long],
                         sourceProtMatchById:  Map[Long, ProteinMatch],
                         defaultScoringId: Long,
                         eraseSourceIds : Boolean) : Array[MsiProteinMatch]  = {

    val msiMasterProtMatches = sourcePeptideSet.proteinMatchIds.map { protMatchId =>

      val mergedProtMatch = sourceProtMatchById(protMatchId)

      // retrieve the right scoring_id
      val scoreType = mergedProtMatch.scoreType
      var msiProtMatchScoringId = ScoringRepository.getScoringIdForType(msiEm, scoreType)
      if (msiProtMatchScoringId == null) {
        msiProtMatchScoringId = defaultScoringId
      }


      val msiMasterProtMatch = new MsiProteinMatch()
      msiMasterProtMatch.setAccession(mergedProtMatch.accession)
      msiMasterProtMatch.setDescription(mergedProtMatch.description)
      msiMasterProtMatch.setGeneName(mergedProtMatch.geneName)
      msiMasterProtMatch.setScore(mergedProtMatch.score)
      msiMasterProtMatch.setPeptideCount(mergedProtMatch.sequenceMatches.length)
      msiMasterProtMatch.setPeptideMatchCount(mergedProtMatch.peptideMatchesCount)
      msiMasterProtMatch.setIsDecoy(mergedProtMatch.isDecoy)
      msiMasterProtMatch.setIsLastBioSequence(mergedProtMatch.isLastBioSequence)
      msiMasterProtMatch.setTaxonId(mergedProtMatch.taxonId)
      if (mergedProtMatch.getProteinId > 0) msiMasterProtMatch.setBioSequenceId(mergedProtMatch.getProteinId)
      msiMasterProtMatch.setScoringId(msiProtMatchScoringId)
      msiMasterProtMatch.setResultSet(emptyRS)
      msiEm.persist(msiMasterProtMatch)

      val msiMasterProtMatchId = msiMasterProtMatch.getId

      // Map new protein match id by Source/Merged protein Match id
      masterProtMatchIdBySourceId2Fill += mergedProtMatch.id -> msiMasterProtMatchId
      // Map protein match TMP id by the new id
      sourceProtMatchIdByMasterId2Fill += msiMasterProtMatchId -> mergedProtMatch.id

      //VDS !!! CHANGE MERGE RSM !!!!
      // Update master protein match id
      //ONLY FOR RESET IDS
      if (eraseSourceIds)
        mergedProtMatch.id = msiMasterProtMatchId

      // map protein_match to seq_databases
      val seqDBsIds = mergedProtMatch.seqDatabaseIds
      for (seqDbId <- seqDBsIds) {

        // Link master protein match to master peptide set
        val msiProteinMatchSeqDatabaseMapPK = new ProteinMatchSeqDatabaseMapPK()
        msiProteinMatchSeqDatabaseMapPK.setProteinMatchId(msiMasterProtMatchId)
        msiProteinMatchSeqDatabaseMapPK.setSeqDatabaseId(seqDbId)

        val msiProteinMatchSeqDatabaseMap = new ProteinMatchSeqDatabaseMap()
        msiProteinMatchSeqDatabaseMap.setId(msiProteinMatchSeqDatabaseMapPK)
        msiProteinMatchSeqDatabaseMap.setResultSetId(emptyRS)
        msiEm.persist(msiProteinMatchSeqDatabaseMap)
      }

      msiMasterProtMatch
    } //END Create msiMasterProtMatch for current PeptideSet
    msiMasterProtMatches
  }
}

/**
 * Should not be used any more : RsmDuplicator with  eraseSourceIds: Boolean = false should be the same !
 */
@deprecated("Use RsmDuplicator with 'eraseSourceIds = false' instead","1.1.0")
class ReadBackRsmDuplicator(rsmProvider: IResultSummaryProvider) extends IRsmDuplicator with LazyLogging {
  
  override def cloneAndStoreRSM(sourceRSM: ResultSummary, emptyRSM: MsiResultSummary, emptyRS: MsiResultSet,  eraseSourceIds: Boolean, msiEm: EntityManager): ResultSummary = {

    val msiMasterPepInstByMergedPepInstId = new HashMap[Long, MsiPeptideInstance]

    // Retrieve result summary and result set ids
    val quantRsmId = emptyRSM.getId
    val quantRsId = emptyRS.getId

    // Retrieve peptide instances of the merged result summarya
    val mergedPepInstances = sourceRSM.peptideInstances
    val mergedPepMatchById = sourceRSM.resultSet.get.getPeptideMatchById

    // TODO: load scoring from MSIdb
    val msiScoring = new MsiScoring()
    msiScoring.setId(4)

    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info("cloning master quant peptide instances...")

    // Define some vars
    val masterQuantPepMatchIdByMergedPepMatchId = new HashMap[Long, Long]

    for (mergedPepInstance <- mergedPepInstances) {

      val peptideId = mergedPepInstance.peptide.id
      val mergedPepInstPepMatchIds = mergedPepInstance.getPeptideMatchIds
      assert(mergedPepInstPepMatchIds.length == 1, "peptide matches have not been correctly merged")

      // TODO: Retrieve the best peptide match
      //val identParentPepMatches = masterPepInstPepMatchIds.map { masterPepMatchById(_) }
      //val bestParentPepMatch = identParentPepMatches.reduce { (a,b) => if( a.score > b.score ) a else b } 
      val mergedPepMatch = mergedPepMatchById(mergedPepInstPepMatchIds(0))

      // Create a quant peptide match which correspond to the best peptide match of this peptide instance
      val msiMasterPepMatch = new MsiPeptideMatch()
      msiMasterPepMatch.setCharge(mergedPepMatch.msQuery.charge)
      msiMasterPepMatch.setExperimentalMoz(mergedPepMatch.msQuery.moz)
      msiMasterPepMatch.setScore(mergedPepMatch.score)
      msiMasterPepMatch.setRank(mergedPepMatch.rank)
      msiMasterPepMatch.setCDPrettyRank(mergedPepMatch.cdPrettyRank)
      msiMasterPepMatch.setSDPrettyRank(mergedPepMatch.sdPrettyRank)
      msiMasterPepMatch.setDeltaMoz(mergedPepMatch.deltaMoz)
      msiMasterPepMatch.setMissedCleavage(mergedPepMatch.missedCleavage)
      msiMasterPepMatch.setFragmentMatchCount(mergedPepMatch.fragmentMatchesCount)
      msiMasterPepMatch.setIsDecoy(false)
      msiMasterPepMatch.setPeptideId(mergedPepMatch.peptide.id)

      // FIXME: retrieve the right scoring_id
      msiMasterPepMatch.setScoringId(1)

      // FIXME: change the ORM to allow these mappings
      //msiMasterPepMatch.setBestPeptideMatchId(bestPepMatch.id) 
      //msiMasterPepMatch.setMsQueryId(bestPepMatch.msQueryId)

      // FIXME: remove this mapping when the ORM is updated
      val msiMSQFake = new MsQuery
      msiMSQFake.setId(mergedPepMatch.msQuery.id)
      msiMasterPepMatch.setMsQuery(msiMSQFake)

      msiMasterPepMatch.setResultSet(emptyRS)
      mergedPepMatch.properties.map(props => msiMasterPepMatch.setSerializedProperties(ProfiJson.serialize(props)))

      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)

      val msiMasterPepMatchId = msiMasterPepMatch.getId

      // Map master peptide match id by in memory merged peptide match id
      masterQuantPepMatchIdByMergedPepMatchId(mergedPepMatch.id) = msiMasterPepMatchId

      //Retrieve ORM Peptide 
      val ormPep = msiEm.find(classOf[Peptide], peptideId)

      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(mergedPepInstPepMatchIds.length) // TODO: check that
      msiMasterPepInstance.setProteinMatchCount(mergedPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(mergedPepInstance.proteinSetsCount)
      msiMasterPepInstance.setTotalLeavesMatchCount(mergedPepInstance.totalLeavesMatchCount)
      msiMasterPepInstance.setValidatedProteinSetCount(mergedPepInstance.validatedProteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptide(ormPep)
      msiMasterPepInstance.setBestPeptideMatchId(msiMasterPepMatchId)
      msiMasterPepInstance.setResultSummary(emptyRSM)
      msiEm.persist(msiMasterPepInstance)

      val msiMasterPepInstanceId = msiMasterPepInstance.getId
      //FIXME  VDS MIGRATE CODE 
      msiMasterPepInstByMergedPepInstId += (mergedPepInstance.id -> msiMasterPepInstance)
      //      msiMasterPepInstById += (msiMasterPepInstanceId -> msiMasterPepInstance)

      // Link the best master peptide match to the quant peptide instance
      val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
      msiPepInstMatchPK.setPeptideInstanceId(msiMasterPepInstanceId)
      msiPepInstMatchPK.setPeptideMatchId(msiMasterPepMatchId)

      val msiPepInstMatch = new MsiPepInstPepMatchMap()
      msiPepInstMatch.setId(msiPepInstMatchPK)
      msiPepInstMatch.setPeptideInstance(msiMasterPepInstance)
      msiPepInstMatch.setPeptideMatch(msiMasterPepMatch)
      msiPepInstMatch.setResultSummary(emptyRSM)

      //msiMasterPepInstance.setPeptidesMatches(Set(msiMasterPepMatch))
      msiEm.persist(msiPepInstMatch)

      // PeptideReadablePTMString
      if (mergedPepInstance.peptide.readablePtmString != null && !mergedPepInstance.peptide.readablePtmString.isEmpty()) {
        val msiPeptideReadablePtmStringPK = new MsiPeptideReadablePtmStringPK()
        msiPeptideReadablePtmStringPK.setPeptideId(mergedPepMatch.peptide.id)
        msiPeptideReadablePtmStringPK.setResultSetId(emptyRS.getId())

        val msiPeptideReadablePtmString = new MsiPeptideReadablePtmString()
        msiPeptideReadablePtmString.setId(msiPeptideReadablePtmStringPK)
        msiPeptideReadablePtmString.setResultSet(emptyRS)
        msiPeptideReadablePtmString.setPeptide(ormPep)
        msiPeptideReadablePtmString.setReadablePtmString(mergedPepInstance.peptide.readablePtmString)

        // Save PeptideReadablePTMString
        msiEm.persist(msiPeptideReadablePtmString)
      }

      // Map this quant peptide match to identified child peptide matches
      if (mergedPepMatch.getChildrenIds != null) {
        for (childPepMatchId <- mergedPepMatch.getChildrenIds) {

          val msiPepMatchRelationPK = new MsiPeptideMatchRelationPK()
          msiPepMatchRelationPK.setChildPeptideMatchId(childPepMatchId)
          msiPepMatchRelationPK.setParentPeptideMatchId(msiMasterPepMatchId)

          val msiPepMatchRelation = new MsiPeptideMatchRelation()
          msiPepMatchRelation.setId(msiPepMatchRelationPK)
          msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)

          val childPM: MsiPeptideMatch = msiEm.find(classOf[MsiPeptideMatch], childPepMatchId)
          msiPepMatchRelation.setChildPeptideMatch(childPM)

          msiPepMatchRelation.setParentResultSetId(emptyRS)

          msiEm.persist(msiPepMatchRelation)

        }
      }
    }

    // Retrieve some vars
    val mergedPeptideSets = sourceRSM.peptideSets
    this.logger.debug("number of grouped peptide sets: " + mergedPeptideSets.length + " sameset " + mergedPeptideSets.filter(!_.isSubset).length)
    val mergedProteinSets = sourceRSM.proteinSets
    this.logger.debug("number of grouped protein sets: " + mergedProteinSets.length)
    val mergedProtSetById = sourceRSM.getProteinSetById
    val mergedProtMatchById = sourceRSM.resultSet.get.getProteinMatchById

    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info("storing quantified peptide sets and protein sets...")
    for (mergedPeptideSet <- mergedPeptideSets) {

      val msiMasterProtMatchIdByMergedId = new HashMap[Long, Long]
      val mergedProtMatchIdByMasterId = new HashMap[Long, Long]

      // Store master protein matches
      val msiMasterProtMatches = mergedPeptideSet.proteinMatchIds.map { protMatchId =>

        val mergedProtMatch = mergedProtMatchById(protMatchId)

        val msiMasterProtMatch = new MsiProteinMatch()
        msiMasterProtMatch.setAccession(mergedProtMatch.accession)
        msiMasterProtMatch.setDescription(mergedProtMatch.description)
        msiMasterProtMatch.setGeneName(mergedProtMatch.geneName)
        msiMasterProtMatch.setScore(mergedProtMatch.score)
        msiMasterProtMatch.setPeptideCount(mergedProtMatch.sequenceMatches.length)
        msiMasterProtMatch.setPeptideMatchCount(mergedProtMatch.peptideMatchesCount)
        msiMasterProtMatch.setIsDecoy(mergedProtMatch.isDecoy)
        msiMasterProtMatch.setIsLastBioSequence(mergedProtMatch.isLastBioSequence)
        msiMasterProtMatch.setTaxonId(mergedProtMatch.taxonId)
        if (mergedProtMatch.getProteinId > 0) msiMasterProtMatch.setBioSequenceId(mergedProtMatch.getProteinId)
        // FIXME: retrieve the right scoring id from OM scoring type 
        msiMasterProtMatch.setScoringId(3)
        msiMasterProtMatch.setResultSet(emptyRS)
        msiEm.persist(msiMasterProtMatch)

        val msiMasterProtMatchId = msiMasterProtMatch.getId

        // Map new protein match id by Merged protein Match id
        msiMasterProtMatchIdByMergedId += mergedProtMatch.id -> msiMasterProtMatchId
        // Map protein match TMP id by the new id
        mergedProtMatchIdByMasterId += msiMasterProtMatchId -> mergedProtMatch.id

        // TODO: map protein_match to seq_databases
        val seqDBsIds = mergedProtMatch.seqDatabaseIds
        for (seqDbId <- seqDBsIds) {

          // Link master protein match to master peptide set
          val msiProteinMatchSeqDatabaseMapPK = new ProteinMatchSeqDatabaseMapPK()
          msiProteinMatchSeqDatabaseMapPK.setProteinMatchId(msiMasterProtMatchId)
          msiProteinMatchSeqDatabaseMapPK.setSeqDatabaseId(seqDbId)

          val msiProteinMatchSeqDatabaseMap = new ProteinMatchSeqDatabaseMap()
          msiProteinMatchSeqDatabaseMap.setId(msiProteinMatchSeqDatabaseMapPK)
          msiProteinMatchSeqDatabaseMap.setResultSetId(emptyRS)
          msiEm.persist(msiProteinMatchSeqDatabaseMap)
        }
        msiMasterProtMatch
      }

      // TODO: find what to do with subsets
      if (mergedPeptideSet.isSubset == false) {

        val masterProteinSetOpt = mergedProtSetById.get(mergedPeptideSet.getProteinSetId)
        assert(masterProteinSetOpt.isDefined, "missing protein set with id=" + mergedPeptideSet.getProteinSetId)

        //////// Check if the protein set has at least a peptide instance with a relevant quantitation
        //val isProteinSetQuantitationRelevant = 0
        //for( tmpPepInstance <- samesetPeptideInstances ) {
        //  val rdbQuantPepInstance = quantPepByIdentPepId( tmpPepInstance.id )
        //  if( rdbQuantPepInstance.isQuantitationRelevant ) {
        //    isProteinSetQuantitationRelevant = 1
        //    last
        //  }
        //}

        // Determine the typical protein match id using the accession number
        val mergedProteinSet = masterProteinSetOpt.get
        var mergedTypicalProtMatchId = mergedProteinSet.getRepresentativeProteinMatchId

        if (mergedTypicalProtMatchId <= 0) {
          mergedTypicalProtMatchId = mergedProteinSet.samesetProteinMatchIds.minBy(mergedProtMatchById(_).accession)
        }
        val typicalProtMatchId = msiMasterProtMatchIdByMergedId(mergedTypicalProtMatchId)

        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated(mergedProteinSet.isValidated)
        msiMasterProteinSet.setIsDecoy(mergedProteinSet.isDecoy)
        msiMasterProteinSet.setSelectionLevel(2)
        msiMasterProteinSet.setProteinMatchId(typicalProtMatchId)
        msiMasterProteinSet.setResultSummary(emptyRSM)
        msiEm.persist(msiMasterProteinSet)

        val msiMasterProteinSetId = msiMasterProteinSet.getId
        //FIXME MIGRATE CODE
        //        msiMasterProtSetById(msiMasterProteinSetId) = msiMasterProteinSet
        //        msiMasterProtSetByMergedProtSetId(mergedProteinSet.id) = msiMasterProteinSet

        // Retrieve peptide set items
        val samesetItems = mergedPeptideSet.items

        // Store master peptide set
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset(false)
        msiMasterPeptideSet.setPeptideCount(samesetItems.length)
        msiMasterPeptideSet.setPeptideMatchCount(mergedPeptideSet.peptideMatchesCount)
        msiMasterPeptideSet.setSequenceCount(mergedPeptideSet.sequencesCount)
        msiMasterPeptideSet.setProteinSet(msiMasterProteinSet)
        // FIXME: retrieve the right scoring id
        msiMasterPeptideSet.setScore(mergedPeptideSet.score)
        msiMasterPeptideSet.setScoring(msiScoring)
        msiMasterPeptideSet.setResultSummaryId(quantRsmId)
        msiEm.persist(msiMasterPeptideSet)

        val msiMasterPeptideSetId = msiMasterPeptideSet.getId

        // Link master peptide set to master peptide instances
        for (samesetItem <- samesetItems) {
          val mergedSameSetPepInst = samesetItem.peptideInstance
          val msiMasterPepInst = msiMasterPepInstByMergedPepInstId(mergedSameSetPepInst.id)

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

          val msiMasterProtMatchId = msiMasterProtMatch.getId

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
          msiPepSetProtMatchMapPK.setProteinMatchId(msiMasterProtMatchId)

          val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          msiPepSetProtMatchMap.setId(msiPepSetProtMatchMapPK)
          msiPepSetProtMatchMap.setPeptideSet(msiMasterPeptideSet)
          msiPepSetProtMatchMap.setProteinMatch(msiMasterProtMatch)
          msiPepSetProtMatchMap.setResultSummary(emptyRSM)
          msiEm.persist(msiPepSetProtMatchMap)

          // Link master protein match to master peptide matches using master sequence matches
          val mergedProtMatch = mergedProtMatchById(mergedProtMatchIdByMasterId(msiMasterProtMatchId))
          val mergedSeqMatches = mergedProtMatch.sequenceMatches
          val mappedMasterPepMatchesIdSet = new HashSet[Long]

          for (mergedSeqMatch <- mergedSeqMatches) {

            val bestPepMatchId = mergedSeqMatch.getBestPeptideMatchId
            if (masterQuantPepMatchIdByMergedPepMatchId.contains(bestPepMatchId)) {
              val masterPepMatchId = masterQuantPepMatchIdByMergedPepMatchId(bestPepMatchId)

              if (mappedMasterPepMatchesIdSet.contains(masterPepMatchId) == false) {
                mappedMasterPepMatchesIdSet(masterPepMatchId) = true

                val msiMasterSeqMatchPK = new SequenceMatchPK()
                msiMasterSeqMatchPK.setProteinMatchId(msiMasterProtMatchId)
                msiMasterSeqMatchPK.setPeptideId(mergedSeqMatch.getPeptideId)
                msiMasterSeqMatchPK.setStart(mergedSeqMatch.start)
                msiMasterSeqMatchPK.setStop(mergedSeqMatch.end)

                val msiMasterSeqMatch = new MsiSequenceMatch()
                msiMasterSeqMatch.setId(msiMasterSeqMatchPK)
                msiMasterSeqMatch.setResidueBefore(scalaCharToCharacter(mergedSeqMatch.residueBefore))
                msiMasterSeqMatch.setResidueAfter(scalaCharToCharacter(mergedSeqMatch.residueAfter))
                msiMasterSeqMatch.setIsDecoy(false)
                msiMasterSeqMatch.setBestPeptideMatchId(masterPepMatchId)
                msiMasterSeqMatch.setResultSetId(quantRsId)
                msiEm.persist(msiMasterSeqMatch)

              }
            }
          }
        }
      }
    }
    msiEm.flush()

    rsmProvider.getResultSummary(emptyRSM.getId, true).get

  }

}