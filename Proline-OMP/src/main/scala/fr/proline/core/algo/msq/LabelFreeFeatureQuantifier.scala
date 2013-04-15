package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import collection.JavaConversions.{ collectionAsScalaIterable, setAsJavaSet }
import com.weiglewilczek.slf4s.Logging

import fr.proline.core.om.model.lcms.MapSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msq._
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.proline.util.ms._

/**
 * @author David Bouyssie
 *
 */
class LabelFreeFeatureQuantifier(
  lcmsMapSet: MapSet,
  spectrumIdMap: Map[Int,HashMap[Int,Int]],
  ms2ScanNumbersByFtId : Map[Int,Seq[Int]],
  mozTolInPPM: Float
) extends IQuantifierAlgo with Logging {
  
  def computeMasterQuantPeptides(
    udsMasterQuantChannel: MasterQuantitationChannel,
    mergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {
    
    // Define some vars
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    val quantChannelIdByLcmsMapId = {
      udsQuantChannels.map { qc => qc.getLcmsMapId.intValue -> qc.getId.intValue } toMap
    }
    
    val identRsIdByRsmId = Map() ++ resultSummaries.map( rsm => rsm.id -> rsm.getResultSetId )
    val identRsIdByLcmsMapId = {
      udsQuantChannels.map { qc => qc.getLcmsMapId.intValue -> identRsIdByRsmId(qc.getIdentResultSummaryId) } toMap
    }
    
    // Define some maps
    val identPepInstById = new HashMap[Int, PeptideInstance]()
    val identPepMatchById = new HashMap[Int, PeptideMatch]()
    val identPepInstIdByPepMatchId = new HashMap[Int, Int]()

    // Load the result summaries corresponding to the quant channels
    for (identResultSummary <- resultSummaries) {

      // Map peptide instances
      val peptideInstances = identResultSummary.peptideInstances
      for (pepInstance <- peptideInstances) {
        val pepInstanceId = pepInstance.id
        identPepInstById(pepInstanceId) = pepInstance
        pepInstance.getPeptideMatchIds.foreach { identPepInstIdByPepMatchId(_) = pepInstanceId }
      }

      // Retrieve protein ids
      val rs = identResultSummary.resultSet.get

      // Map peptide matches by their id
      rs.peptideMatches.foreach { p => identPepMatchById(p.id) = p }
    }

    // Retrieve all identified peptide matches and map them by spectrum id
    val allIdentPepMatches = identPepMatchById.values
    val identPepMatchBySpectrumId = allIdentPepMatches.map { p => p.getMs2Query.spectrumId -> p } toMap

    // Retrieve peptide matches of the merged result summary
    //val identParentPeptideMatches = this.mergedResultSummary.resultSet.get.peptideMatches

    // Retrieve some vars 
    val masterMap = lcmsMapSet.masterMap

    val pepInstIdSetByFtId = new HashMap[Int, HashSet[Int]]
    val masterFeatures = masterMap.features
    for (masterFt <- masterFeatures) {

      // Iterate over each master feature child
      val ftChildren = masterFt.children
      for (ftChild <- ftChildren) {

        val childMapId = ftChild.relations.mapId

        // Check if we have a result set for this map (this map may be in the map_set but not used)
        if (identRsIdByLcmsMapId.contains(childMapId)) {

          val identRsId = identRsIdByLcmsMapId(childMapId)
          val specIdByScanNum = spectrumIdMap(identRsId)

          // Retrieve MS2 scan numbers which are related to this feature
          val tmpFtIds = if (ftChild.isCluster) ftChild.subFeatures.map { _.id } else Array(ftChild.id)
          val ms2ScanNumbers = tmpFtIds.flatMap { i => ms2ScanNumbersByFtId.getOrElse(i, ArrayBuffer.empty[Int]) }

          // Stop if no MS2 available
          if (ms2ScanNumbers.length > 0) {

            // Retrieve corresponding MS2 spectra ids and remove existing redundancy
            val specIdSet = ms2ScanNumbers.filter(specIdByScanNum.contains(_))
              .map(n => specIdByScanNum(n) )
              .toSet

            // Retrieve corresponding peptide matches if they exist
            for (spectrumId <- specIdSet) {
              if (identPepMatchBySpectrumId.contains(spectrumId)) {
                val identPepMatch = identPepMatchBySpectrumId(spectrumId)

                // Check that peptide match charge is the same than the feature one
                val msQuery = identPepMatch.msQuery
                if (ftChild.charge == msQuery.charge) {

                  // TMP FIX FOR MOZ TOLERANCE OF FEATURE/MS2 SPECTRUM
                  val deltaMoz = math.abs(ftChild.moz - msQuery.moz)
                  val pepMozTolInDalton = calcMozTolInDalton(ftChild.moz, mozTolInPPM, "ppm")

                  if (deltaMoz <= pepMozTolInDalton) {
                    val pepInstanceId = identPepInstIdByPepMatchId(identPepMatch.id)
                    pepInstIdSetByFtId.getOrElseUpdate(ftChild.id, new HashSet[Int]) += pepInstanceId
                  }

                  //////// Map the current feature to the peptide match
                  //val pepMatchProperties = identPepMatch.properties
                  //if( defined pepMatchProperties(feature_id) )
                  //  {
                  //  die "the peptide match with id='".identPepMatch.id."' is already associated with a LCMS feature" .
                  //  pepMatchProperties(feature_id) . ftChild.dump
                  //  }
                  //pepMatchProperties(feature_id) = ftChild.id
                  //
                  //push( masterFtPepMatches, identPepMatch )
                }
              }
            }
          }
        }
      }
    }
    
    // --- Convert LC-MS features into quant peptide ions ---    
    val rsmById = resultSummaries.map { rsm => rsm.id -> rsm } toMap
    val lcmsMapById = lcmsMapSet.childMaps.map { m => m.id -> m } toMap
    val normFactorByMapId = lcmsMapSet.getNormalizationFactorByMapId

    val quantPepIonsByFtId = new HashMap[Int, ArrayBuffer[QuantPeptideIon]]
    
    for (udsQuantChannel <- udsQuantChannels) {

      // Retrieve some vars
      val udsQuantChannelId = udsQuantChannel.getId
      val identRsmId = udsQuantChannel.getIdentResultSummaryId
      val identResultSummary = rsmById(identRsmId)
      val lcmsMapId = udsQuantChannel.getLcmsMapId
      val lcmsMap = lcmsMapById(lcmsMapId)
      val normFactor = normFactorByMapId(lcmsMapId)

      val lcmsFeatures = lcmsMap.features
      for (feature <- lcmsFeatures) {

        // Try to retrieve matching peptide instances
        var matchingPepInstsAsOpts = Array(Option.empty[PeptideInstance])
        if (pepInstIdSetByFtId.contains(feature.id)) {
          matchingPepInstsAsOpts = pepInstIdSetByFtId(feature.id).toArray.map { i => Some(identPepInstById(i)) }
        }

        val ftIntensity = feature.intensity

        for (matchingPepInstAsOpt <- matchingPepInstsAsOpts) {

          var (peptideId, pepInstId, pepMatchesCount) = (Option.empty[Int], Option.empty[Int], 0)
          var (msQueryIds, bestPepMatchScore) = (Option.empty[Array[Int]], Option.empty[Float])

          if (matchingPepInstAsOpt != None) {
            val pepInst = matchingPepInstAsOpt.get
            peptideId = Some(pepInst.peptide.id)
            pepInstId = Some(pepInst.id)

            // Retrieve the number of peptide matches and the best peptide match score
            val pepMatches = pepInst.getPeptideMatchIds.map { identPepMatchById(_) }
            pepMatchesCount = pepMatches.length
            msQueryIds = Some(pepMatches.map { _.msQuery.id })
            bestPepMatchScore = Some(pepMatches.reduce((a, b) => if (a.score > b.score) a else b).score)
          }

          // Create a quant peptide ion corresponding the this LCMS feature
          val quantPeptideIon = new QuantPeptideIon(
            rawAbundance = ftIntensity.toFloat,
            abundance = ftIntensity.toFloat * normFactor,
            moz = feature.moz,
            elutionTime = feature.elutionTime,
            scanNumber = feature.relations.apexScanInitialId,
            peptideMatchesCount = pepMatchesCount,
            bestPeptideMatchScore = bestPepMatchScore,
            quantChannelId = udsQuantChannelId,
            peptideId = peptideId,
            peptideInstanceId = pepInstId,
            msQueryIds = msQueryIds,
            lcmsFeatureId = feature.id
          )
          quantPepIonsByFtId.getOrElseUpdate(feature.id, new ArrayBuffer[QuantPeptideIon]()) += quantPeptideIon
        }
      }
    }
    
    // Map peptide instances by the peptide id
    val masterPepInstByPepId = Map() ++ mergedRSM.peptideInstances.map( pi => pi.peptide.id -> pi )
    
    // Iterate over master features to create master quant peptides
    // TODO: group features by charge state
    val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]
    for (masterFt <- masterFeatures) {

      // Create an inner function which will help to instantiate Master Quant peptides
      def newMasterQuantPeptide(quantPepIonMap: Map[Int, QuantPeptideIon],
        masterPepInstAsOpt: Option[PeptideInstance],
        lcmsFtId: Option[Int]): MasterQuantPeptide = {

        val mqPepIon = new MasterQuantPeptideIon(
          id = MasterQuantPeptideIon.generateNewId(),
          unlabeledMoz = masterFt.moz,
          charge = masterFt.charge,
          elutionTime = masterFt.elutionTime,
          peptideMatchesCount = 0,
          bestPeptideMatchId = None,
          resultSummaryId = 0,
          lcmsFeatureId = lcmsFtId,
          selectionLevel = 2,
          quantPeptideIonMap = quantPepIonMap
        )
        
      val quantPepByQcId = Map.newBuilder[Int,QuantPeptide]
      for( (qcId,quantPepIon) <- quantPepIonMap ) {
        
        // Build the quant peptide
        val qp = new QuantPeptide(
          rawAbundance = quantPepIon.rawAbundance,
          abundance = quantPepIon.abundance,
          elutionTime = quantPepIon.elutionTime,
          peptideMatchesCount = quantPepIon.peptideMatchesCount,
          quantChannelId = qcId,
          peptideId = 0,
          peptideInstanceId = 0,
          selectionLevel = 2
        )
        
        quantPepByQcId += qcId -> qp
      }

        /*var( pepMatchesCount, protMatchesCount, pepId, peptInstId) = (0,0,0,0)
        if( masterPepInstAsOpt != None ) {
          val masterPepInst = masterPepInstAsOpt.get
          pepMatchesCount = masterPepInst.peptideMatchesCount
          protMatchesCount = masterPepInst.proteinMatchesCount
          pepId = masterPepInst.peptide.id
          peptInstId = masterPepInst.id
        }*/

        new MasterQuantPeptide(
          id = MasterQuantPeptide.generateNewId,
          peptideInstance = masterPepInstAsOpt,
          quantPeptideMap = quantPepByQcId.result,
          masterQuantPeptideIons = Array(mqPepIon),
          selectionLevel = 2
        )

      }

      // Retrieve peptide ids related to the feature children
      val peptideIdSet = new HashSet[Int]

      for (ftChild <- masterFt.children) {

        // Check if we have a result set for this map (that map may be in the map_set but not used)
        if (identRsIdByLcmsMapId.contains(ftChild.relations.mapId)) {
          val childPeptideIons = quantPepIonsByFtId(ftChild.id)

          for (childPepIon <- childPeptideIons) {
            if (childPepIon.peptideId != None) peptideIdSet += childPepIon.peptideId.get
          }
        }
      }

      // Convert master feature into master quant peptides
      val peptideIds = peptideIdSet.toList
      if (peptideIds.length > 0) {
        val masterPepInsts = peptideIds.map { masterPepInstByPepId.get(_) }
        //val quantPepIonsByPepId = new HashMap[Int,ArrayBuffer[QuantPeptideIon]]

        // Iterate over each quant peptide instance which is matching the master feature
        // Create a master quant peptide ion for each peptide instance
        for (masterPepInstOpt <- masterPepInsts; masterPepInst <- masterPepInstOpt) {
          // TODO: check why masterPepInstOpt may be empty

          val tmpPeptideId = masterPepInst.peptide.id
          val quantPeptideIonMap = new HashMap[Int, QuantPeptideIon]

          // Link master quant peptide ion to identified peptide ions
          for (ftChild <- masterFt.children) {

            val mapId = ftChild.relations.mapId

            // Check if we have a result set for this map (that map may be in the map_set but not used)
            if (identRsIdByLcmsMapId.contains(mapId)) {

              val childPeptideIons = quantPepIonsByFtId(ftChild.id)
              val matchingPepChildPepIons = childPeptideIons.filter { p => p.peptideId != None && p.peptideId.get == tmpPeptideId }
              
              //require(matchingPepChildPepIons.length == 1, "peptide ion identification conflict")
              if( matchingPepChildPepIons.length == 1 ) {
                this.logger.trace("peptide ion identification conflict")
              }

              // Try to retrieve peptide ion corresponding to current peptide instance
              val qcId = quantChannelIdByLcmsMapId(mapId)
              
              quantPeptideIonMap(qcId) = if (matchingPepChildPepIons.length == 1) matchingPepChildPepIons(0)
              else childPeptideIons(0) // TODO: check that length is zero

            }
          }
          
         masterQuantPeptides += newMasterQuantPeptide(quantPeptideIonMap.toMap, Some(masterPepInst), Some(masterFt.id))

        }
      } else {

        // Create a master quant peptide ion which is not linked to a peptide instance

        val quantPeptideIonMap = new HashMap[Int, QuantPeptideIon]
        for (ftChild <- masterFt.children) {

          // Check if we have a result set for this map (that map may be in the map_set but not used)
          val mapId = ftChild.relations.mapId
          if (identRsIdByLcmsMapId.contains(mapId)) {

            // TODO: find what to do if more than one peptide ion
            // Currently select the most abundant
            val childPeptideIon = quantPepIonsByFtId(ftChild.id).reduce { (a, b) =>
              if (a.rawAbundance > b.rawAbundance) a else b
            }
            val qcId = quantChannelIdByLcmsMapId(mapId)
            quantPeptideIonMap(qcId) = childPeptideIon

          }
        }
        
        masterQuantPeptides += newMasterQuantPeptide(quantPeptideIonMap.toMap, None, Some(masterFt.id))

      }
    }

    // TODO create quant peptide ions which are not related to a LCMS features = identified peptide ions but not quantified

    masterQuantPeptides.toArray
  }
  
 def computeMasterQuantProteinSets(
   udsMasterQuantChannel: MasterQuantitationChannel,
   masterQuantPeptides: Seq[MasterQuantPeptide],
   mergedRSM: ResultSummary,
   resultSummaries: Seq[ResultSummary]
 ): Array[MasterQuantProteinSet] = {
   
    val mqPepByPepInstId = masterQuantPeptides.filter(_.peptideInstance.isDefined)
                                              .map { mqp => mqp.peptideInstance.get.id -> mqp } toMap
    val mqProtSets = new ArrayBuffer[MasterQuantProteinSet]
    
    for( mergedProtSet <- mergedRSM.proteinSets ) {
      
      val selectedMQPepIds = new ArrayBuffer[Int]
      val abundanceSumByQcId = new HashMap[Int,Float]
      val pepMatchesCountByQcId = new HashMap[Int,Int]
      
      for( mergedPepInst <- mergedProtSet.peptideSet.getPeptideInstances ) {
        // If the peptide has been quantified
        if( mqPepByPepInstId.contains(mergedPepInst.id) ) {
          val mqp = mqPepByPepInstId( mergedPepInst.id )
          if( mqp.selectionLevel >= 2 ) selectedMQPepIds += mqp.id
          
          for( (qcId,quantPep) <- mqp.quantPeptideMap ) {
            abundanceSumByQcId.getOrElseUpdate(qcId,0)
            abundanceSumByQcId(qcId) += quantPep.abundance
            
            pepMatchesCountByQcId.getOrElseUpdate(qcId,0)
            pepMatchesCountByQcId(qcId) += quantPep.peptideMatchesCount
          }
        }
      }
      
      val quantProteinSetByQcId = new HashMap[Int,QuantProteinSet]
      for( (qcId,abundanceSum) <- abundanceSumByQcId ) {
        quantProteinSetByQcId(qcId) = new QuantProteinSet(
          rawAbundance = abundanceSum,
          abundance = abundanceSum,
          peptideMatchesCount = pepMatchesCountByQcId(qcId),
          quantChannelId = qcId,
          selectionLevel = 2
        )
      }
      
      val mqProtSetProps = new MasterQuantProteinSetProperties()
      mqProtSetProps.setSelectedMasterQuantPeptideIds( Some(selectedMQPepIds.toArray) )
      
      val mqProteinSet = new MasterQuantProteinSet(
        proteinSet = mergedProtSet,
        quantProteinSetMap = quantProteinSetByQcId.toMap,
        selectionLevel = 2,
        properties = Some(mqProtSetProps)
      )
      
      mqProtSets += mqProteinSet
    }
    
    mqProtSets.toArray
 }
 
}