package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import collection.JavaConversions.{ collectionAsScalaIterable, setAsJavaSet }
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.lcms.{MapSet,Feature}
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
  expDesign: ExperimentalDesign,
  lcmsMapSet: MapSet,
  spectrumIdMap: Map[Long,HashMap[Long,Long]],
  ms2ScanNumbersByFtId : Map[Long,Seq[Int]],
  mozTolInPPM: Float,
  statTestsAlpha: Float = 0.01f
) extends IQuantifierAlgo with Logging {
  
  def computeMasterQuantPeptides(
    udsMasterQuantChannel: MasterQuantitationChannel,
    mergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {
    
    // Define some vars
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    val quantChannelIdByLcmsMapId = {
      udsQuantChannels.map { qc => qc.getLcmsMapId -> qc.getId } toMap
    }
    
    val masterPepInstByPepId = Map() ++ mergedRSM.peptideInstances.map( pi => pi.peptide.id -> pi )
    val identRsIdByRsmId = Map() ++ resultSummaries.map( rsm => rsm.id -> rsm.getResultSetId )
    val identRsIdByLcmsMapId = {
      udsQuantChannels.map { qc => qc.getLcmsMapId.longValue -> identRsIdByRsmId(qc.getIdentResultSummaryId) } toMap
    }
    
    // Define some maps
    val identPepInstById = new HashMap[Long, PeptideInstance]()
    val identPepMatchById = new HashMap[Long, PeptideMatch]()
    val identPepInstIdByPepMatchId = new HashMap[Long, Long]()

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

    // Try to find peptide instances which could correspond to detected features
    val pepInstIdSetByFtId = new HashMap[Long, HashSet[Long]]
    val masterFtByFtId = new HashMap[Long, Feature]
    val masterFtById = new HashMap[Long, Feature]
    
    val masterFeatures = masterMap.features
    for (masterFt <- masterFeatures) {
      masterFtById(masterFt.id) = masterFt

      // Iterate over each master feature child
      val ftChildren = masterFt.children
      for (ftChild <- ftChildren) {
        masterFtByFtId(ftChild.id) = masterFt

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
                  // Note: it should not be necessary to check the delta m/z
                  val deltaMoz = math.abs(ftChild.moz - msQuery.moz)
                  val pepMozTolInDalton = calcMozTolInDalton(ftChild.moz, mozTolInPPM, "ppm")

                  if (deltaMoz <= pepMozTolInDalton) {
                    val pepInstanceId = identPepInstIdByPepMatchId(identPepMatch.id)
                    pepInstIdSetByFtId.getOrElseUpdate(ftChild.id, new HashSet[Long]) += pepInstanceId
                  }
                }
              }
            }
          }
        }
      }
    }
    
    // --- Convert LC-MS features into quant peptide ions ---
    //val rsmById = resultSummaries.map { rsm => rsm.id -> rsm } toMap
    val lcmsMapById = lcmsMapSet.childMaps.map { m => m.id -> m } toMap
    val normFactorByMapId = lcmsMapSet.getNormalizationFactorByMapId

    val ftById = new HashMap[Long, Feature]
    val quantPepIonsByMasterPepInst = new HashMap[PeptideInstance, ArrayBuffer[QuantPeptideIon]]
    val unidentifiedQuantPepIonsByFt = new HashMap[Feature, ArrayBuffer[QuantPeptideIon]]
    
    // Iterate over each quantitative channel
    for (udsQuantChannel <- udsQuantChannels) {

      // Retrieve some vars
      val (udsQuantChannelId,identRsmId,lcmsMapId) = (
        udsQuantChannel.getId,
        udsQuantChannel.getIdentResultSummaryId,
        udsQuantChannel.getLcmsMapId
      )
      val lcmsMap = lcmsMapById(lcmsMapId)
      val normFactor = normFactorByMapId(lcmsMapId)

      // Iterate over LC-MS features
      for (feature <- lcmsMap.features ) {
        
        // Map this feature by its id
        ftById(feature.id) = feature
        
        val masterFt = masterFtByFtId.get(feature.id)
        
        // Try to retrieve matching peptide instances
        var matchingPepInstsAsOpts = Array(Option.empty[PeptideInstance])
          
        // Check if we have found some peptide instances for this LCMS feature
        if (pepInstIdSetByFtId.contains(feature.id) == true) {
          val tmpMatchingPepInstsAsOpts = pepInstIdSetByFtId(feature.id).toArray
            // Retrieve peptide instance
            .map( i => identPepInstById(i) )
            // Note: if the peptide instance is not included in the merged RSM, it may be associated to an invalidated protein set
            .filter( pi => masterPepInstByPepId.contains(pi.peptide.id) )
            .map(Option(_))
          
          if (tmpMatchingPepInstsAsOpts.length > 0) matchingPepInstsAsOpts = tmpMatchingPepInstsAsOpts
        }
        
        if( matchingPepInstsAsOpts.length > 1 ) {
          this.logger.trace("peptide ion identification conflict")
        }
        
        val ftIntensity = feature.intensity

        for (matchingPepInstAsOpt <- matchingPepInstsAsOpts) {

          var (peptideId, pepInstId) = (Option.empty[Long], Option.empty[Long])
          var (msQueryIds, bestPepMatchScore) = (Option.empty[Array[Long]], Option.empty[Float])

          if (matchingPepInstAsOpt != None) {
            val pepInst = matchingPepInstAsOpt.get
            peptideId = Some(pepInst.peptide.id)
            pepInstId = Some(pepInst.id)

            // Retrieve the number of peptide matches and the best peptide match score
            val pepMatches = pepInst.getPeptideMatchIds.map { identPepMatchById(_) }
            //pepMatchesCount = pepMatches.length
            
            // TODO: use filter these ids using feature.relations.ms2EventIds instead
            msQueryIds = Some(pepMatches.map { _.msQuery.id })
            
            bestPepMatchScore = Some(pepMatches.reduce((a, b) => if (a.score > b.score) a else b).score)
          }

          // Create a quant peptide ion corresponding the this LCMS feature
          val quantPeptideIon = new QuantPeptideIon(
            rawAbundance = ftIntensity,
            abundance = ftIntensity * normFactor,// TODO: getNormalizedIntensity
            moz = feature.moz,
            elutionTime = feature.elutionTime,
            duration = feature.duration,
            correctedElutionTime = feature.getCorrectedElutionTimeOrElutionTime,
            scanNumber = feature.relations.apexScanInitialId,
            peptideMatchesCount = feature.ms2Count,
            bestPeptideMatchScore = bestPepMatchScore,
            quantChannelId = udsQuantChannelId,
            peptideId = peptideId,
            peptideInstanceId = pepInstId,
            msQueryIds = msQueryIds,
            lcmsFeatureId = feature.id,
            lcmsMasterFeatureId = masterFt.map(_.id)
          )
          
          if( pepInstId != None ) {
            
            // Retrieve the master peptide instance corresponding to this peptide instance
            val peptideId = identPepInstById(pepInstId.get).peptideId
            val masterPepInst = masterPepInstByPepId(peptideId)
            
            // Add this quantPeptideIon to this master peptide instance
            quantPepIonsByMasterPepInst.getOrElseUpdate(masterPepInst, new ArrayBuffer[QuantPeptideIon]()) += quantPeptideIon
            
          } else {
            val refFt = masterFt.getOrElse(feature)
            unidentifiedQuantPepIonsByFt.getOrElseUpdate(refFt, new ArrayBuffer[QuantPeptideIon]()) += quantPeptideIon
          }
        }
      }
    }
    
    // Try to cross-assign unidentifiedQuantPepIons
    //for( (refFt,masterFtQuantPepIons) <- unidentifiedQuantPepIonsByFt ) {
    //}
    
    // Map peptide instances by the peptide id
    val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]
    
    // Iterate first over peptide instances to build Master Quant Peptides
    for( (masterPepInst,pepInstQuantPepIons) <- quantPepIonsByMasterPepInst ) {
      
      // Group quant peptide ions by master feature id or feature id if no master attached
      val quantPepIonsByFtId = pepInstQuantPepIons.groupBy( qpi => qpi.lcmsMasterFeatureId.getOrElse(qpi.lcmsFeatureId) )
      
      // Convert the quantPepIonsByFtId map into a quantPepIonMapByFt one
      val quantPepIonMapByFt = new HashMap[Feature,Map[Long,QuantPeptideIon]]
      for( (ftId,quantPepIons) <- quantPepIonsByFtId ) {
       
        // Retrieve the corresponding feature
        val refFt = if( masterFtById.contains(ftId) ) masterFtById(ftId) else ftById(ftId)
        
        // Add unidentified quant peptide ions if they exist for this feature
        val allFtQuantPepIons = if( unidentifiedQuantPepIonsByFt.contains(refFt) == false ) quantPepIons
        else {
          val unidentifiedQuantPepIons = unidentifiedQuantPepIonsByFt.remove( refFt ).get
          quantPepIons ++ unidentifiedQuantPepIons
        }
        
        quantPepIonMapByFt(refFt) = Map() ++ allFtQuantPepIons.map( qpi => qpi.quantChannelId -> qpi )
      }
        
      masterQuantPeptides += newMasterQuantPeptide( quantPepIonMapByFt, Some(masterPepInst) )

    }
    
    // Iterate over unidentified master features and build Master Quant Peptides which are not linked to peptides
    // TODO: try to group these features by charge state if they are close in RT
    for( (refFt,masterFtQuantPepIons) <- unidentifiedQuantPepIonsByFt ) {
      
      val quantPeptideIonMap = Map() ++ masterFtQuantPepIons.map( qpi => qpi.quantChannelId -> qpi )
      val quantPepIonMapByFt = HashMap( refFt -> quantPeptideIonMap )
      
      masterQuantPeptides += newMasterQuantPeptide(quantPepIonMapByFt, None)
    }
    
    // Compute the statistical analysis of abundance profiles
    val profilizer = new Profilizer(
      expDesign = expDesign,
      groupSetupNumber = 1, // TODO: retrieve from params
      masterQCNumber = udsMasterQuantChannel.getNumber
    )
    
    profilizer.computeMasterQuantPeptideProfiles(masterQuantPeptides, statTestsAlpha)

    masterQuantPeptides.toArray
  }
  
  private def newMasterQuantPeptide(
    quantPepIonMapByFt: HashMap[Feature,Map[Long,QuantPeptideIon]],
    masterPepInstAsOpt: Option[PeptideInstance]
  ): MasterQuantPeptide = {
    require( quantPepIonMapByFt != null && quantPepIonMapByFt.size > 0, "quantPepIonMapByFt must not be empty")
    
    val mqPeptideId = MasterQuantPeptide.generateNewId

    val mqPepIons = new ArrayBuffer[MasterQuantPeptideIon]
    for( (refFt, quantPepIonMap ) <- quantPepIonMapByFt ) {
      val masterFtId = if (refFt.isMaster) Some(refFt.id) else None
      
      mqPepIons += new MasterQuantPeptideIon(
        id = MasterQuantPeptideIon.generateNewId(),
        unlabeledMoz = refFt.moz,
        charge = refFt.charge,
        elutionTime = refFt.getCorrectedElutionTimeOrElutionTime,
        peptideMatchesCount = 0,
        masterQuantPeptideId = mqPeptideId,
        bestPeptideMatchId = None,
        resultSummaryId = 0,
        lcmsFeatureId = masterFtId,
        selectionLevel = 2,
        quantPeptideIonMap = quantPepIonMap
      )
    }

    // Keep the MQP with the highest intensity
    // TODO: allows to sum charge states
    val bestMQP = mqPepIons.reduceLeft { (a,b) => if( a.calcAbundanceSum > b.calcAbundanceSum ) a else b }
    
    val quantPepByQcId = Map.newBuilder[Long,QuantPeptide]
    for( (qcId,quantPepIon) <- bestMQP.quantPeptideIonMap ) {
      
      // Build the quant peptide
      val qp = new QuantPeptide(
        rawAbundance = quantPepIon.rawAbundance,
        abundance = quantPepIon.abundance,
        elutionTime = quantPepIon.elutionTime,
        peptideMatchesCount = quantPepIon.peptideMatchesCount,
        quantChannelId = qcId,
        selectionLevel = 2
      )
      
      quantPepByQcId += qcId -> qp
    }
  
    new MasterQuantPeptide(
      id = mqPeptideId,
      peptideInstance = masterPepInstAsOpt,
      quantPeptideMap = quantPepByQcId.result,
      masterQuantPeptideIons = mqPepIons.toArray,
      selectionLevel = 2,
      resultSummaryId = 0
    )

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
      
      val selectedMQPepIds = new ArrayBuffer[Long]
      val abundanceSumByQcId = new HashMap[Long,Float]
      val pepMatchesCountByQcId = new HashMap[Long,Int]
      
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
      
      val quantProteinSetByQcId = new HashMap[Long,QuantProteinSet]
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