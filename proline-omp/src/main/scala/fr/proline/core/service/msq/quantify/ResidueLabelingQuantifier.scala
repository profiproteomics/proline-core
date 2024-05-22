package fr.proline.core.service.msq.quantify

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.ms
import fr.proline.context._
import fr.proline.core.algo.msq.config._
import fr.proline.core.algo.msq.summarizing.ResidueLabelingEntitiesSummarizer
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.PTMFakeProvider
import fr.proline.core.om.provider.msi.impl.{SQLPeptideProvider, SQLResultSummaryProvider}
import fr.proline.core.om.storer.msi.impl.{RsmDuplicator, StorerContext}
import fr.proline.core.om.storer.msi.{PeptideInstanceWriter, PeptideMatchWriter, PeptideWriter}
import fr.proline.core.orm.msi.ObjectTreeSchema.SchemaName
import fr.proline.core.orm.msi.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.msi.{ObjectTreeSchema, PtmSpecificity, ResultSummary => MsiResultSummary}
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.core.orm.uds.QuantitationChannel
import fr.proline.core.service.lcms.io.ExtractMapSet
import org.apache.commons.collections4.{ListUtils, SetUtils}
import org.apache.commons.lang3.StringUtils

import java.util
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks

class ResidueLabelingQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val experimentalDesign: ExperimentalDesign,
  val quantMethod: ResidueLabelingQuantMethod,
  val quantConfig: ResidueLabelingQuantConfig
) extends AbstractMasterQuantChannelQuantifier with LazyLogging {
  
  private val groupSetupNumber = 1
  private val masterQcExpDesign = experimentalDesign.getMasterQuantChannelExpDesign(udsMasterQuantChannel.getNumber, groupSetupNumber)
  
  private val tagById = quantMethod.tagById

  private lazy val qcByRSMIdAndTagId = {
    val qcsByRsmId = masterQc.quantChannels.groupBy(_.identResultSummaryId)
    for ((rsmId, qcs) <- qcsByRsmId;
         qc <- qcs) yield (rsmId, qc.quantLabelId.get) -> qc
  }

  // Map each PTM Specificity Id to associated ResidueTag Id. Set -1 for Tag with no modification
  private val tagByPtmId = {
    val tmptagByPtmId = { for (tag <- quantConfig.tags;
         ptmId <- tag.ptmSpecificityIds) yield ptmId -> tag.tagId }
    val mappedTagIds = tmptagByPtmId.map(_._2).toList
    val unmappedTagIds = tagById.keys.filter(!mappedTagIds.contains(_)).toList
    require(unmappedTagIds.length <= 1, "There cannot be more than one tag corresponding to unlabeled peptides")
    (tmptagByPtmId ++ unmappedTagIds.map((-1L,_))).toLongMap()
  }


  protected def quantifyMasterChannel(): Unit = {

    // --- TODO: merge following code with AbstractLabelFreeFeatureQuantifier ---
    // DBO: put in AbstractMasterQuantChannelQuantifier ??? possible conflict with WeightedSpectralCountQuantifier ???

    require( udsDbCtx.isInTransaction, "UdsDb connection context must be inside a transaction")
    require( msiDbCtx.isInTransaction, "MsiDb connection context must be inside a transaction")
    val rsmProvider = new SQLResultSummaryProvider(PeptideCacheExecutionContext(executionContext))
    val rsmDuplicator = new RsmDuplicator(rsmProvider)

    val msiDbHelper = new MsiDbHelper(msiDbCtx)
    //!! Do not initialize  entityCache until modif of qChannels identRSMs
    val allQuChannels=   udsMasterQuantChannel.getQuantitationChannels.asScala.toList
    val newAllQChannels = new ArrayBuffer[QuantitationChannel]()
    val newRsmIdByOldId = new mutable.HashMap[Long,Long]()
    val quantChannelsByRsmId =allQuChannels.groupBy(_.getIdentResultSummaryId)
    quantChannelsByRsmId.foreach(entry =>{

      val identRsm = rsmProvider.getResultSummary(entry._1, loadResultSet = true).get
      val oldRsmId = identRsm.id
      val childIds = msiDbHelper.getResultSetChildrenIds(identRsm.getResultSetId)
      val qChannelMsiQuantRs =  if(childIds==null || childIds.isEmpty)
        storeMsiQuantResultSet(List.empty[Long])
      else
        storeMsiQuantResultSet(childIds.toList)

      val childRsmIds = msiDbHelper.getResultSummaryChildrenIds(identRsm.id)
      val qChannelMsiQuantRsm = if (childRsmIds == null || childRsmIds.isEmpty)
        storeMsiQuantResultSummary(qChannelMsiQuantRs, Array.empty[Long])
      else
        storeMsiQuantResultSummary(qChannelMsiQuantRs, childRsmIds)

      rsmDuplicator.cloneAndStoreRSM(identRsm, qChannelMsiQuantRsm, qChannelMsiQuantRs, false, msiEm)

      entry._2.foreach(qch => {
        qch.setIdentResultSummaryId(qChannelMsiQuantRsm.getId)
        udsEm.persist(qch)
        newAllQChannels += qch
      })
      newRsmIdByOldId += oldRsmId->qChannelMsiQuantRsm.getId
    })

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet()

    // Create corresponding master quant result summary
    val msiQuantRsm = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRsm.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    udsMasterQuantChannel.setQuantitationChannels(newAllQChannels.asJava)
    for(index <- masterQc.quantChannels.indices){
      val prevQCh = masterQc.quantChannels(index)
      masterQc.quantChannels(index) = prevQCh.copy(identResultSummaryId = newRsmIdByOldId(prevQCh.identResultSummaryId))
    }
    udsEm.persist(udsMasterQuantChannel)

    // Store master quant result summary

    // Build or clone master quant result summary, then store it
    // TODO: remove code redundancy with the AbstractLabelFreeFeatureQuantifier (maybe add a dedicated method in AbstractMasterQuantChannelQuantifier)
    val quantRsm = rsmDuplicator.cloneAndStoreRSM(mergedResultSummary, msiQuantRsm, msiQuantResultSet, masterQc.identResultSummaryId.isEmpty, msiEm)
    
    // Compute and store quant entities (MQ Peptides, MQ ProteinSets)
    this.computeAndStoreQuantEntities(msiQuantRsm, quantRsm)
    
    // Flush the entity manager to perform the update on the master quant peptides
    msiEm.flush()
    
    // --- TODO: END OF merge following code with AbstractLabelFreeFeatureQuantifier ---

    ()
  }
  

  
  protected def computeAndStoreQuantEntities(msiQuantRsm: MsiResultSummary, quantRsm : ResultSummary): Unit = {
    
    logger.info("computing residue labeling quant entities...")
    
    // Quantify peptide ions if a label free quant config is provided
    val lfqConfig = quantConfig.labelFreeQuantConfig

    logger.info(" ***** Find missing peptides for Peptides-tag tuples ...")
    val start = System.currentTimeMillis()
    createMissingTupleData(quantRsm)
    val end  = System.currentTimeMillis()
    logger.info(" ***** TOOK = "+(end-start)+" ms")

    for( rsm <- entityCache.quantChannelResultSummaries ) {
      createMissingTupleData(rsm)
    }

    logger.info("computing label-free quant entities...")
//    throw new RuntimeException("Test on going")

    // Extract features from mzDB files
    logger.info("Nb peptInstance for qRsm "+ quantRsm.peptideInstances.length +" validated  "+quantRsm.peptideInstances.filter(pi => pi.validatedProteinSetsCount>0).length)
    val (pepByRunAndScanNbr, psmByRunAndScanNbr) = entityCache.getPepAndPsmByRunIdAndScanNumber(quantRsm)
    val psmBypep  = psmByRunAndScanNbr.head._2.values.flatten.groupBy(_.peptide)
    val nbrPsm=  psmBypep.values.flatten.size
    logger.info("psmBypep..."+ psmBypep.size +" nbr peptides = "+nbrPsm+" pm ")
    //throw new RuntimeException("Test on going")
    val mapSetExtractor = new ExtractMapSet(
      executionContext.getLCMSDbConnectionContext,
      udsMasterQuantChannel.getName,
      entityCache.getSortedLcMsRuns(),
      masterQcExpDesign,
      lfqConfig,
      Some(pepByRunAndScanNbr),
      Some(psmByRunAndScanNbr)
    )
    mapSetExtractor.run()

    // Retrieve some values
    val lcmsMapSet = mapSetExtractor.extractedMapSet
    val rawMapIds = lcmsMapSet.getRawMapIds()
    val lcMsScans = entityCache.getLcMsScans(rawMapIds)
    val spectrumIdByRsIdAndScanNumber = entityCache.spectrumIdByRsIdAndScanNumber
    val ms2ScanNumbersByFtId = entityCache.getMs2ScanNumbersByFtId(lcMsScans, rawMapIds)


    val entitiesSummarizer = if(lfqConfig.pepIonSummarizingMethod.isDefined) {
      new ResidueLabelingEntitiesSummarizer(
        this.qcByRSMIdAndTagId,
        this.tagByPtmId,
        lcmsMapSet,
        spectrumIdByRsIdAndScanNumber,
        ms2ScanNumbersByFtId,
        lfqConfig.pepIonSummarizingMethod.get)
    } else {
      new ResidueLabelingEntitiesSummarizer(
        this.qcByRSMIdAndTagId,
        this.tagByPtmId,
        lcmsMapSet,
        spectrumIdByRsIdAndScanNumber,
        ms2ScanNumbersByFtId)
    }

    logger.info("summarizing quant entities...")

    val(mqPeptides,mqProtSets) = entitiesSummarizer.computeMasterQuantPeptidesAndProteinSets(
      this.masterQc,
      quantRsm,
      this.entityCache.quantChannelResultSummaries
    )


    val lcMsMapIdByRunId = Map() ++ lcmsMapSet.childMaps.map( lcmsMap => lcmsMap.runId.get -> lcmsMap.id )
    // Update the LC-MS map id of each master quant channel
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels.asScala
    for( (udsQc,qc) <- udsQuantChannels.zip(this.masterQc.quantChannels) ) {
      val lcMsMapIdOpt = lcMsMapIdByRunId.get( qc.runId.get )
      require( lcMsMapIdOpt.isDefined, "Can't retrieve the LC-MS map id for the run #"+ qc.runId.get)
      qc.lcmsMapId = lcMsMapIdOpt
      udsQc.setLcmsMapId( lcMsMapIdOpt.get )
      udsEm.merge(udsQc)
    }

    // Update the map set id of the master quant channel
    udsMasterQuantChannel.setLcmsMapSetId(lcmsMapSet.id)
    udsEm.merge(udsMasterQuantChannel)

    this.storeMasterQuantPeptidesAndProteinSets(msiQuantRsm,mqPeptides,mqProtSets)
  }

  private def createMissingTupleData(quantRsm: ResultSummary): Unit = {

    //groups all peptide with same sequence and PTMs regardless of tags specific PTMs
    val peptideInstanceListByTupleKey = new mutable.HashMap[String, ArrayBuffer[PeptideInstance]]()
    // for each tuple entry, list PTMs list regardless of tags specific PTMs
    val peptidePTMsListByTupleKey = new mutable.HashMap[String, Array[LocatedPtm]]()

    logger.info("- Found "+quantRsm.peptideInstances.length+" peptideInstances ")
    var nbIons = 0
    //Create Maps for searching/processing tag group
    for( pepI <- quantRsm.peptideInstances){
      val key = pepI.peptide.sequence +"%"+ pepI.peptide.ptms.filter(p => !tagByPtmId.contains(p.definition.id)).sortBy(_.seqPosition).map(_.toPtmString()).mkString("")
      nbIons = nbIons + pepI.peptideMatches.map(_.charge).distinct.length
      peptideInstanceListByTupleKey.getOrElseUpdate(key, new ArrayBuffer[PeptideInstance]()) += pepI
      if(!peptidePTMsListByTupleKey.contains(key)) {
        peptidePTMsListByTupleKey.put(key,  pepI.peptide.ptms.filter(p => !tagByPtmId.contains(p.definition.id)).sortBy(_.seqPosition))
      }
    }
    logger.info("- Found "+nbIons+" ions. "+peptideInstanceListByTupleKey.size+" peptideInstances tag groups ")


    //For counting ... and creation
    val counting = CountingLabelingData()
    //search for missing peptides in peptide tuples
   val (psmToCreateOnly, psmAndPepToCreate) =  searchMissingPeptideInTuple(peptideInstanceListByTupleKey,  peptidePTMsListByTupleKey, counting) //search missing data for each tap group

    // -- print counting info
    logger.info("  --- Nb tag group with peptides for all tags: " + counting.tagTupleWithNoMissingPeptideCount)
    for ((tagId,tag) <- tagById) {
      var count = counting.missingPeptideCountByTagId.get(tagId)
      logger.info("  --- Nb peptides with missing tags " + tag.name + ":" + count)
      count = counting.missingPsmByTagId.get(tagId)
      logger.info("  --- Nb PSM with missing tags " + tag.name + ":" + count)
      val countMore = counting.morePeptidesByTagId.get(tagId)
      logger.info("  --- Nb more peptides in tags " + tag.name + ":" + countMore)
    }
    logger.info(" ---- Nb Peptide found during search  ....  "+counting.foundPepInMissingPep)
    logger.info(" ---- Nb Peptide still not found during search  ....  "+counting.notFoundPepInMissingPep)
    logger.info(" ---- Create peptides for following ")

    var nbPsm = 0
    psmAndPepToCreate.foreach(entry =>{
      nbPsm = nbPsm + entry._2.size
    })
    logger.info(psmAndPepToCreate.size+" peptides to create which corresponds to " + nbPsm+" PSMs ")

    createMissingDataInDatabase( psmAndPepToCreate, psmToCreateOnly, quantRsm)

//    for(fpep <- foundPeps){
//      if(fpep.isDefined) {
//        Console.out.println("--Id\t"+fpep.get.id+"\tSeq\t"+fpep.get.sequence+"\tptms\t"+fpep.get.ptms)
//      }
//    }

  }

  private def searchMissingPeptideInTuple(peptideInstanceListByTupleKey: mutable.HashMap[String, ArrayBuffer[PeptideInstance]], peptidePTMsListByTupleKey : mutable.HashMap[String, Array[LocatedPtm]], counting : CountingLabelingData)
      : ( ArrayBuffer[PeptideMatch], mutable.HashMap[ (String,Array[LocatedPtm]), ArrayBuffer[PeptideMatch]]  ) = {

    val pepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(executionContext))

    val ptmIdsByTagId = tagByPtmId.groupBy { case (_, tag) => tag }.mapValues(_.keys.toList)
    val ptmDefAndCompositionByPtmId: mutable.Map[Long, (PtmDefinition, String)] = getPTMDefAndCompositions(ptmIdsByTagId.values.flatten.toArray)
    val emtyTagId = tagByPtmId.getOrElse(-1, -1L)

    //description of missing object to create (peptides and PSMs)
    val psmToCreateByPeptide = new mutable.HashMap[(String,Array[LocatedPtm]), ArrayBuffer[PeptideMatch]]()
    val psmToCreateOnly = new ArrayBuffer[PeptideMatch]()

    //Go through each tuple (group of same peptides with different tag)
    peptideInstanceListByTupleKey.foreach(pepInstancesEntry => {
      val pepKeyEntry = pepInstancesEntry._1
      val pepInstances = pepInstancesEntry._2

      var allPeptidesInTupleFound = true
      var allPSMInTupleFound = true

      val pepInstIdsByChargeAndTagId: mutable.HashMap[Long, mutable.HashMap[Integer, ArrayBuffer[Long]]] = getPeptideInstByTagAndCharge(pepInstances, ptmIdsByTagId, emtyTagId)
      val allCharges = pepInstIdsByChargeAndTagId.values.flatten.map(_._1).toSet.asJava

      var pepInterest = false
      if (pepKeyEntry.startsWith("AAVLYVMDLSEQCGHGLR")) {
        logger.info("- Entry " + pepKeyEntry + " with "+pepInstances.size+" pepInstances ")
        pepInterest = true
      }
      //for each tag
      pepInstIdsByChargeAndTagId.foreach(entry => {
        val tagId = entry._1

        //current tuple sequence, empty tag PTMs list and mass
        val seq = pepKeyEntry.split("%")(0) //current seq
        val locatedPTMs = peptidePTMsListByTupleKey.getOrElse(pepKeyEntry, Array.empty[LocatedPtm]).to[ArrayBuffer]
        if(pepInterest)
          logger.info(" tag "+tagId)
        if (!tagId.equals(emtyTagId)) { //add specific tag ptms
          ptmIdsByTagId(tagId).foreach(nextPtmIdTag => {
            val (ptmDef, composition) = ptmDefAndCompositionByPtmId(nextPtmIdTag)
            val nbResidue = StringUtils.countMatches(seq, ptmDef.residue)
            var indexOfResidue = 0
            for (_ <- 0 until nbResidue) {
              indexOfResidue = seq.indexOf(ptmDef.residue, indexOfResidue) + 1
              if (indexOfResidue > 0)
                locatedPTMs += new LocatedPtm(ptmDef, indexOfResidue, ptmDef.ptmEvidences(0).monoMass, 0.0, composition, false, false)
            }
          })
        }
        val pepMass = Peptide.calcMass(seq, locatedPTMs.toArray)
        if(pepInterest)
          locatedPTMs.foreach(lptm => {logger.info("- LocPtm at "+lptm.seqPosition+ " : " +lptm.toPtmString() )})

        if (pepInstIdsByChargeAndTagId(tagId).isEmpty) { //Missing tag. Peptide (and psm) to create
          //update counting
          var nbMissingTag = counting.missingPeptideCountByTagId.getOrDefault(tagId, 0)
          nbMissingTag += 1
          counting.missingPeptideCountByTagId.put(tagId, nbMissingTag)
          logger.trace("\t--Missing TAG for pep group\t" + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id + "\ttag id\t" + tagId + "_" + tagById(tagId).name)
          if(pepInterest)
            logger.info("\t--Missing TAG for pep group\t" + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id + "\ttag id\t" + tagId + "_" + tagById(tagId).name)
          allPeptidesInTupleFound = false

//           val foundPepOpt = pepProvider.getPeptide(seq, locatedPTMs.toArray)
//          if(foundPepOpt.isDefined) {
//            val nbF = counting.foundPepInMissingPep
//            counting.foundPepInMissingPep = nbF+1
//          } else {
//            val nbF = counting.notFoundPepInMissingPep
//            counting.notFoundPepInMissingPep = nbF + 1
//          }

          // ---- Create Missing Peptide ---
          //list all PSMs to create : one per existing charge in tuple
          val psmToCreateForPep = new ArrayBuffer[PeptideMatch]()
          allCharges.forEach(chg => {
            var psmToCreate : PeptideMatch = null
            psmToCreate = createFakePSMFor(tagId, emtyTagId, pepInstances, pepInstIdsByChargeAndTagId, chg, pepMass, None)//foundPepOpt)
            psmToCreateForPep += psmToCreate
          })//end for all charge

          val nbPSM = counting.missingPsmByTagId.getOrDefault(tagId, 0)
          counting.missingPsmByTagId.put(tagId, nbPSM + psmToCreateForPep.size)
          psmToCreateByPeptide.put((seq -> locatedPTMs.toArray), psmToCreateForPep)
//          if(foundPepOpt.isEmpty) {
//            psmToCreateByPeptide.put(seq -> locatedPTMs.toArray, psmToCreateForPep)
//          } else {
//            //psm only
//            allPSMInTupleFound = false
//            psmToCreateOnly ++= psmToCreateForPep
//          }

        } else { //found current tag ----------------

          val pepInstInTagIds = pepInstIdsByChargeAndTagId(tagId).values.flatten.toSeq

          //Test missing charge
          val missingCharges: SetUtils.SetView[Integer] = SetUtils.difference(allCharges, pepInstIdsByChargeAndTagId(tagId).keySet.asJava)
          if (!missingCharges.isEmpty) {

            //update counting
            val nbPSM = counting.missingPsmByTagId.getOrDefault(tagId, 0)
            counting.missingPsmByTagId.put(tagId, nbPSM + missingCharges.size)
            logger.trace("\t--Missing PSM only for " + missingCharges.size + " ions (" + missingCharges + ") for current pep group\t"+ pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id+ "\ttag id\t" + tagById(tagId).name)
            if (pepInterest)
              logger.info("\t--Missing PSM only for " + missingCharges.size + " ions (" + missingCharges + ") for current pep group\t"+ pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id+ "\ttag id\t" + tagById(tagId).name)
            allPSMInTupleFound = false

            //get full located PTMs' peptide for current tag
            val fullPtmPis = pepInstances.filter(pi =>{
              pepInstInTagIds.contains(pi.id) &&  ListUtils.subtract(locatedPTMs.toList.asJava, pi.peptide.ptms.toList.asJava).isEmpty
            })

            val fullPTMPi = if(fullPtmPis.isEmpty) {
              val maxPtmPi = -1
              var foundMaxPtmPi : PeptideInstance = null
              for(index <- pepInstances.indices){
                val curPiPTMs = pepInstances(index).peptide.ptms.length
                if(curPiPTMs>maxPtmPi)
                  foundMaxPtmPi = pepInstances(index)
              }
              foundMaxPtmPi
            } else fullPtmPis.head

            //create missing PSMs
            missingCharges.forEach( chg =>{
              var psmToCreate : PeptideMatch = null
              psmToCreate = createFakePSMFor(tagId, emtyTagId, pepInstances,  pepInstIdsByChargeAndTagId, chg, pepMass, Some(fullPTMPi.peptide))
              psmToCreateOnly += psmToCreate

            })//for all missing charge
          }

          val pepInsIds = pepInstIdsByChargeAndTagId(tagId).values.flatten.toSet
          //if more than one peptide for current tag ...
          if (pepInsIds.size > 1) {
            logger.info("More than one peptide " + pepInsIds.size + " for current tag " + tagById(tagId).name + " pep " + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id)
            var nbMoreTag = counting.morePeptidesByTagId.getOrDefault(tagId, 0)
            nbMoreTag += (pepInsIds.size - 1)
            counting.morePeptidesByTagId.put(tagId, nbMoreTag)
          }
        } // end found tag

      }) //end for all tags

      if (allPeptidesInTupleFound) {
        counting.tagTupleWithNoMissingPeptideCount += 1
        //        if (pepInstances.size > nbTags)
        //          logger.info("Full Group with more than " + nbTags + " peptides (" + pepInstances.size + ") ; " + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id)
      }
    })
    (psmToCreateOnly,psmToCreateByPeptide)
  }

  private def createFakePSMFor(tagId : Long, emtyTagId:Long, pepInstances: ArrayBuffer[PeptideInstance], pepInstIdsByChargeAndTagId: mutable.HashMap[Long, mutable.HashMap[Integer, ArrayBuffer[Long]]],
                           chg : Int, pepMass: Double, pepRef : Option[Peptide]): PeptideMatch = {
    var psmDefined = false
    var psmReference: PeptideMatch = null

    if (!tagId.equals(emtyTagId)) {
      //try to get reference PSM from empty tag
      val pepInstIdsForCharge = pepInstIdsByChargeAndTagId.getOrElse(emtyTagId, new mutable.HashMap[Integer, ArrayBuffer[Long]]).get(chg)
      if (pepInstIdsForCharge.isDefined && pepInstIdsForCharge.get.nonEmpty) {
        //search PSM found for charge in empty tag
        val piReference = pepInstances.filter(pi => pi.id.equals(pepInstIdsForCharge.get.head)).head
        psmReference = piReference.peptideMatches.find(pm => pm.charge == chg).get
        psmDefined = true
      }
    }

    if (!psmDefined) {
      val tuplePSMsForCharge = pepInstances.flatMap(pi => pi.peptideMatches.filter(pm => pm.charge == chg)).toList
      psmReference = if ( pepRef.isDefined && tuplePSMsForCharge.count(pm => pm.peptide.equals(pepRef.get)) > 0) {
        tuplePSMsForCharge.filter(pm => pm.peptide.equals(pepRef.get)).head
      } else
        tuplePSMsForCharge.head
      // other data to get ?
      // score =  tuplePSMsForCharge.map(psm => psm.score).sum / tuplePSMsForCharge.size,
    }

    val prop = psmReference.properties.getOrElse(PeptideMatchProperties())
    prop.comment = Some("FAKE_PSM_TAG=" + tagById(tagId).name)
    val dMasses =pepMass - psmReference.peptide.calculatedMass
    val exMoz = psmReference.getExperimentalMoz() + ms.massToMoz(dMasses, chg)
    val dMass = exMoz - ms.massToMoz(pepMass, chg)

    new PeptideMatch(
      id = PeptideMatch.generateNewId(),
      rank = psmReference.rank,
      score = psmReference.score,
      scoreType = psmReference.scoreType,
      charge = chg,
      deltaMoz = dMass.toFloat,
      isDecoy = psmReference.isDecoy,
      peptide = if(pepRef.isDefined) pepRef.get else psmReference.peptide,//TODO to change once peptide will be created if not specified
      missedCleavage = psmReference.missedCleavage,
      fragmentMatchesCount = 0,
      msQuery = psmReference.getMs2Query().copy(id = Ms2Query.generateNewId(), moz=exMoz),
      properties = Some(prop),
      resultSetId = psmReference.resultSetId
    )
  }

  private def createMissingDataInDatabase(pepKeyAndPsmToCreate : mutable.HashMap[(String, Array[LocatedPtm]), ArrayBuffer[PeptideMatch]], psmToCreateOnly: ArrayBuffer[PeptideMatch], rsm : ResultSummary ): Unit = {

    val pepProvider = new SQLPeptideProvider(PeptideCacheExecutionContext(executionContext))
    val pepToCreateKeys = pepKeyAndPsmToCreate.keySet.toSeq
    val peptidesToCreate = new ArrayBuffer[Peptide]()
    val peptidesInstToCreate = new ArrayBuffer[PeptideInstance]()
    val pepUniqueKeyByPep = new mutable.HashMap[Peptide, (String, Array[LocatedPtm])]()


    val foundPeps = pepProvider.getPeptidesAsOptionsBySeqAndPtms(pepToCreateKeys)

    logger.info("Found " + foundPeps.count(_.isDefined) + " on " + pepKeyAndPsmToCreate.size + " peptides. ")
    for (index <- pepToCreateKeys.indices) {
      val psmsToCreate = pepKeyAndPsmToCreate(pepToCreateKeys(index))

      val newPeptide =  if (foundPeps(index).isDefined) {
        //associate this pep to all psm to create
        val newPsmsToCreate = new ArrayBuffer[PeptideMatch]()
        psmsToCreate.foreach(pm => newPsmsToCreate += pm.copy(peptide = foundPeps(index).get))
        pepKeyAndPsmToCreate.put(pepToCreateKeys(index), newPsmsToCreate)
        foundPeps(index).get

      } else {
      val seq = pepToCreateKeys(index)._1
      val lPtms = pepToCreateKeys(index)._2
      val cMass = Peptide.calcMass(seq, lPtms)
      val pep = new Peptide(
          id = Peptide.generateNewId(),
          sequence = seq,
          ptmString = Peptide.makePtmString(lPtms) ,
          ptms = lPtms,
          calculatedMass = cMass,
          properties = None
      )
      peptidesToCreate += pep
      pepUniqueKeyByPep.put(pep,pepToCreateKeys(index))
        pep
      }

      //create associated peptideInstance
      val prop = PeptideInstanceProperties(None, Some("FAKE_PEPTIDE_INSTANCE"))
      val pepInst = PeptideInstance(id = PeptideInstance.generateNewId(),
            peptide = newPeptide,
            peptideMatchIds = psmsToCreate.map(pm => pm.id).toArray,
            peptideMatches = psmsToCreate.toArray,
            bestPeptideMatchId = psmsToCreate(0).id,
            validatedProteinSetsCount = 1, //VDS TODO Search in other tag ...
            resultSummaryId = rsm.id,
            properties = Some(prop)
      )
      peptidesInstToCreate += pepInst
    } // For each peptide to create

    //create missing peptides
    logger.info("  Create " +peptidesToCreate.size + " peptides " )
    PeptideWriter(msiDbCtx.getDriverType).insertPeptides(peptidesToCreate, new StorerContext(executionContext))
    for (cPeptide <- peptidesToCreate) {
      val psmsToCreate = pepKeyAndPsmToCreate(pepUniqueKeyByPep(cPeptide))
      val newPsmsToCreate = new ArrayBuffer[PeptideMatch]()
      psmsToCreate.foreach(pm => {
        newPsmsToCreate += pm.copy(peptide = cPeptide)
      })
      pepKeyAndPsmToCreate.put(pepUniqueKeyByPep(cPeptide), newPsmsToCreate)
    }

    //create missing peptides matches
    val allPepMatches = (psmToCreateOnly ++ pepKeyAndPsmToCreate.values.flatten).toArray
    val psmByTmpId = allPepMatches.map(pm => (pm.id , pm)).toMap
    logger.info("  Create " + allPepMatches.length + " peptides matches ")
    PeptideMatchWriter(msiDbCtx.getDriverType).insertPeptideMatches(allPepMatches, msiDbCtx)
    rsm.resultSet.get.addPeptideMatches(allPepMatches)

    //create missing peptides instance
    peptidesInstToCreate.foreach(pi => {
      pi.bestPeptideMatchId = psmByTmpId(pi.bestPeptideMatchId).id
      val pmIds = pi.peptideMatchIds
      val newPmIds = new Array[Long](pmIds.length)
      val newPm = new Array[PeptideMatch](pmIds.length)
      for(index <- pmIds.indices){
        newPmIds(index) = psmByTmpId(pmIds(index)).id
        newPm(index) = psmByTmpId(pmIds(index))
      }
      pi.peptideMatchIds = newPmIds
      pi.peptideMatches = newPm
    })

    logger.info("  Create " + peptidesInstToCreate.size + " peptides instance.RSM has "+rsm.peptideInstances.size)
    PeptideInstanceWriter(msiDbCtx.getDriverType).insertPeptideInstances(peptidesInstToCreate, msiDbCtx)
    rsm.addPeptideInstances(peptidesInstToCreate.toArray)
    logger.info(" DONE Create peptides instance.RSM has now "+rsm.peptideInstances.size)
  }

   private def getPeptideInstByTagAndCharge(peptideInstances : ArrayBuffer[PeptideInstance], ptmIdsByTagId:  Map[Long, List[Long]], emptyTag : Long) : mutable.HashMap[Long,mutable.HashMap[Integer, ArrayBuffer[Long]]] = {

    var remainingPeps = ArrayBuffer.empty[PeptideInstance]
    remainingPeps ++= peptideInstances

    val result = new mutable.HashMap[Long, mutable.HashMap[Integer, ArrayBuffer[Long]]]()

    for (tagId <- ptmIdsByTagId.keySet) {
      var ptmIdsForTag = ptmIdsByTagId(tagId)
      val matchingPeps = ArrayBuffer.empty[PeptideInstance]
      val revert = tagId.equals(emptyTag)
      if(revert)
        ptmIdsForTag = ptmIdsByTagId.values.flatten.toList

      val pepInstToLook = new ArrayBuffer[PeptideInstance]() ++ remainingPeps //don't test already associated peptides

      for (index <- pepInstToLook.indices) {
        var found = false
        val pi = pepInstToLook(index)
        Breaks.breakable {
          for (nextPtm <- pi.peptide.ptms) {
            if (ptmIdsForTag.contains(nextPtm.definition.id)) {
              if (revert)
                found = true
              else {
                matchingPeps += pi
                remainingPeps = remainingPeps.filter(pepI => !pepI.equals(pi))//remove(index)
              }
              Breaks.break()
            }
          }
        }
        if (revert && !found) {
          matchingPeps += pi //didn't find any PTM so consider this peptide as matching
          remainingPeps = remainingPeps.filter(pepI => !pepI.equals(pi))
        }
      }

      //Group found peptide by charge
      val pepIdByCharge = new mutable.HashMap[Integer, ArrayBuffer[Long]]
      for (pi <- matchingPeps) {
        val piId = pi.id
        val charges = pi.peptideMatches.map(_.charge).distinct.toList
        charges.foreach(charge  => {
          val pepInsIds = pepIdByCharge.getOrElseUpdate(charge, new ArrayBuffer[Long])
          pepInsIds += piId
          pepIdByCharge.put(charge, pepInsIds)
        })
      }
      result.put(tagId, pepIdByCharge)
    }

    result
  }

  private def getPTMDefAndCompositions(tagsPtms: Array[Long]): mutable.HashMap[Long, (PtmDefinition, String)] = {
    val ptmDefAndCompo = new mutable.HashMap[Long, (PtmDefinition, String)]()
    tagsPtms.foreach(ptmId => {
      if (ptmId != -1L) {
        val ptmSpecif = msiEm.find(classOf[PtmSpecificity], ptmId)
        var composition = ""
        var monoMass = -1.0
        var dataFound = false
        val evidenceIt = if (ptmSpecif.getEvidences != null && !ptmSpecif.getEvidences.isEmpty)
          ptmSpecif.getEvidences.iterator()
        else
          ptmSpecif.getPtm.getEvidences.iterator()

        while (!dataFound && evidenceIt.hasNext) {
          val evidence = evidenceIt.next()
          if (evidence.getComposition != null && evidence.getComposition.nonEmpty && !evidence.getComposition.equals("0")) {
            composition = evidence.getComposition
            monoMass = evidence.getMonoMass
            //found for specificity stop search. Else, found generic search if specific exist
            if (evidence.getSpecificity != null && evidence.getSpecificity.getId == ptmSpecif.getId)
              dataFound = true
          }
        }

        val residue = ptmSpecif.getResidue
        val ptmDef = PTMFakeProvider.getPtmDefinition(ptmSpecif.getPtm.getShortName, residue, PtmLocation.ANYWHERE, monoMass)
        ptmDef.get.id = ptmId
        ptmDefAndCompo.put(ptmId, (ptmDef.get, composition))
      }
    })
    ptmDefAndCompo
  }


  //  private def createMissingTuplePeptidesV1(mergedRsm: ResultSummary): Unit = {
//
//    //groups all peptide with same sequence and PTMs regardless of tags specific PTMs
//    val peptideInstanceByPTMNoTagKey = new mutable.HashMap[String, ArrayBuffer[PeptideInstance]]()
//
//    //for each group entry, keep LocatedPTMS regardless of tags specific PTM: for missing peptide creation
//    val peptidePTMsListByPTMNoTagKey = new mutable.HashMap[String, ArrayBuffer[LocatedPtm]]()
//
//    //Get All peptideMatch grouped by charge, for all peptide instance => Only for missing !  VDSTODO
//    val pepMatchesByChgByPepInstId = new mutable.HashMap[Long, Map[Int, Array[PeptideMatch]]]
//    val chargeByPepInstId = new mutable.HashMap[Long, List[Int]]
//
//    logger.info("- Found " + mergedRsm.peptideInstances.size + " peptideInstances ");
//    var nbIons = 0
//    //Create Maps for searching/processing tag group
//    for (pepI <- mergedRsm.peptideInstances) {
//
//      val key = pepI.peptide.sequence + "%" + pepI.peptide.ptms.filter(p => !tagByPtmId.contains(p.definition.id)).sortBy(_.seqPosition).map(_.toPtmString()).mkString("")
//            if(!peptidePTMsListByPTMNoTagKey.contains(key)){
//              //First Time, get none PTMTag list
//              val ptmList =  pepI.peptide.ptms.filter(p => !tagByPtmId.contains(p.definition.id)).sortBy(_.seqPosition).to[ArrayBuffer]
//              peptidePTMsListByPTMNoTagKey.put(key, ptmList)
//            }
//
//      val pepMatchesByChg = pepI.peptideMatches.groupBy(_.charge)
//      pepMatchesByChgByPepInstId.put(pepI.id, pepMatchesByChg)
//      val chargeList = pepI.peptideMatches.map(_.charge).distinct.toList
//      chargeByPepInstId.put(pepI.id, chargeList)
//      nbIons += chargeList.size
//      peptideInstanceByPTMNoTagKey.getOrElseUpdate(key, new ArrayBuffer[PeptideInstance]()) += pepI
//    }
//    logger.info("- Found " + nbIons + " ions. " + peptideInstanceByPTMNoTagKey.size + " peptideInstances tag groups ");
//
//    val nbTags = tagById.size
//    val ptmIdsByTagId = tagByPtmId.filter(e => e._1 != -1L).groupBy { case (ptm, tag) => tag }.mapValues(_.keys.toList)
//    val emtyTagId = tagByPtmId.get(-1)
//    val allTagsPtms = ptmIdsByTagId.values.flatten.toSeq
//    val ptmDefAndCompositionByPtmId = getPTMDefAndCompositions(allTagsPtms.toArray)
//
//    //For counting ... and creation
//    var tagGroupWithNoMissingPeptideCount2: Int = 0
//    val missingTagsByTagId: util.HashMap[Long, Integer] = new util.HashMap[Long, Integer]
//    val morePeptidesByTagId: util.HashMap[Long, Integer] = new util.HashMap[Long, Integer]
//    val peptidesToCreate = new ArrayBuffer[(String, String)]()
//
//    //search for missing peptides in peptide groups
//    peptideInstanceByPTMNoTagKey.foreach(pepInstancesEntry => {
//      val pepKeyEntry = pepInstancesEntry._1
//      val pepInstances = pepInstancesEntry._2
//
//      var allTagFound = true
//
//      ptmIdsByTagId.foreach(entry => {
//        val tagId = entry._1
//        val isEmptyTag = emtyTagId.isDefined && tagId == emtyTagId.get
//        val ptmOfInterest = if (isEmptyTag) allTagsPtms else entry._2
//        val matchCurrentTagPepInst = pepInstances.count(pepInst => pepInst.peptide.ptms.map(_.definition.id).count(id => ptmOfInterest.contains(id)) > 0)
//
//        if ((isEmptyTag && matchCurrentTagPepInst == pepInstances.size) || (!isEmptyTag && matchCurrentTagPepInst == 0)) {
//          //No peptide instance for current Tag !
//          var nbMissingTag: Int = missingTagsByTagId.getOrDefault(tagId, 0)
//          nbMissingTag += 1;
//          missingTagsByTagId.put(tagId, nbMissingTag)
//
//          logger.trace("\t--Missing TAG for pep group\t" + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id + "\ttag id\t" + tagId + "_" + tagById(tagId).name + "\tFound ? \tpep")
//          allTagFound = false
//
//          // ---- Create Missing Peptide ---
//          val splittedKey = pepKeyEntry.split("%")
//          val seq = splittedKey(0)
//          var ptmString: String = ""
//          if (splittedKey.length > 1) { //Other Ptm
//            ptmString = splittedKey(1)
//          }
//
//          if (!isEmptyTag) {
//            var lPtms = peptidePTMsListByPTMNoTagKey.getOrElse(pepKeyEntry, ArrayBuffer.empty[LocatedPtm])
//            ptmString = "" //recreate ptmString
//
//            ptmOfInterest.foreach(nextPtmIdTag => {
//              val (ptmDef, composition) = ptmDefAndCompositionByPtmId(nextPtmIdTag)
//              val nbResidue = StringUtils.countMatches(seq, ptmDef.residue)
//              var indexOfResidue = 0
//              for (_ <- 0 until (nbResidue)) {
//                indexOfResidue = seq.indexOf(ptmDef.residue, indexOfResidue) + 1
//                if (indexOfResidue > 0)
//                  lPtms += new LocatedPtm(ptmDef, indexOfResidue, 0.0, 0.0, composition, false, false)
//              }
//            })
//
//
//            ptmString = Peptide.makePtmString(lPtms.toArray)
//          }
//
//          peptidesToCreate += (seq -> ptmString)
//
//        } else { //found current tag
//
//          //if more than one peptide for current tag ...
//          if (!isEmptyTag && matchCurrentTagPepInst > 1) {
//            logger.info("More than one peptide " + matchCurrentTagPepInst + " for current tag " + tagById(tagId).name + " pep " + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id);
//            var nbMoreTag = morePeptidesByTagId.getOrDefault(tagId, 0);
//            nbMoreTag += (matchCurrentTagPepInst - 1);
//            morePeptidesByTagId.put(tagId, nbMoreTag);
//          }
//        } // end found tag
//
//        //            //              val pep = MsiPeptideRepository.findPeptideForSequenceAndPtmStr(msiEm, seq, ptmString)
//        //              logger.info("\t--Missing TAG for pep\t"++pepInstances(0).peptide.sequence+"_"+pepInstances(0).peptide.id+"\ttag id\t"+entry._1+"_"+tagById(entry._1).name+"\tFound with ptmString \t+pep")
//      })
//
//      if (allTagFound) {
//        tagGroupWithNoMissingPeptideCount2 += 1
//        if (pepInstances.size > nbTags)
//          logger.info("Full Group with more than " + nbTags + " peptides (" + pepInstances.size + ") ; " + pepInstances(0).peptide.sequence + "_" + pepInstances(0).peptide.id)
//      }
//    }) //End for each tap group
//
//    logger.info("  --- Nb tag group with peptides for all tags: " + tagGroupWithNoMissingPeptideCount2)
//    for ((tagId, tag) <- tagById) {
//      val count = missingTagsByTagId.get(tagId)
//      logger.info("  --- Nb peptides with missing tags " + tag.name + ":" + count)
//      val countMore = morePeptidesByTagId.get(tagId)
//      logger.info("  --- Nb more peptides in tags " + tag.name + ":" + countMore)
//    }
//
//    logger.info(" ---- Create peptides for following ")
//    //    peptidesToCreate.foreach(e => {
//    //      Console.out.println("\tSeq\t"+e._1+"\tptms\t"+e._2)
//    //    })
//
//    //    val pepProvider = new SQLPeptideProvider( PeptideCacheExecutionContext(executionContext) )
//    //    val foundPeps = pepProvider.getPeptidesAsOptionsBySeqAndPtms(peptidesToCreate)
//
//
//  }

  protected lazy val quantPeptidesObjectTreeSchema: ObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.RESIDUE_LABELING_QUANT_PEPTIDES.toString)
  }

  protected lazy val quantPeptideIonsObjectTreeSchema: ObjectTreeSchema = {
    ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(msiEm, SchemaName.RESIDUE_LABELING_QUANT_PEPTIDE_IONS.toString)
  }
  
  override protected def getMergedResultSummary(msiDbCtx: MsiDbConnectionContext): ResultSummary = {
    require(masterQc.identResultSummaryId.isDefined, "mergedIdentRsmIdOpt is not defined")
    super.getMergedResultSummary(msiDbCtx)
  }

private case class CountingLabelingData(
     var tagTupleWithNoMissingPeptideCount : Integer =0,
     missingPeptideCountByTagId: util.HashMap[Long, Integer] =  new util.HashMap[Long, Integer],
     missingPsmByTagId : util.HashMap[Long, Integer] = new util.HashMap[Long, Integer](),
     morePeptidesByTagId: util.HashMap[Long, Integer] = new util.HashMap[Long, Integer],
     var foundPepInMissingPep : Integer = 0,
     var notFoundPepInMissingPep : Integer = 0
  ) {

}

}


