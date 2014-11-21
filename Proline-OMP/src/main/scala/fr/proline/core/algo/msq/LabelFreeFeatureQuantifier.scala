package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.HashSet
import collection.JavaConversions.{ collectionAsScalaIterable, setAsJavaSet }
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.lcms.{MapSet,Feature}
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msq._
import fr.proline.core.orm.uds.MasterQuantitationChannel
import fr.profi.util.ms._

/**
 * @author David Bouyssie
 *
 */
class LabelFreeFeatureQuantifier(
  expDesign: ExperimentalDesign,
  lcmsMapSet: MapSet,
  spectrumIdByRsIdAndScanNumber: Map[Long,HashMap[Int,Long]],
  ms2ScanNumbersByFtId : Map[Long,Seq[Int]],
  mozTolInPPM: Float,
  statTestsAlpha: Float = 0.01f
) extends IQuantifierAlgo with Logging {
  
  private class MQPepsComputer(
    udsMasterQuantChannel: MasterQuantitationChannel,
    mergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ) extends Logging {
  
    // Define some vars
    val mergedRsmId = mergedRSM.id
    val udsQuantChannels = udsMasterQuantChannel.getQuantitationChannels
    val qcCount = udsQuantChannels.size()
    
    // Retrieve some LC-MS vars
    val masterMap = lcmsMapSet.masterMap
    val lcmsMapById = lcmsMapSet.childMaps.map { m => m.id -> m } toMap
    val nbMaps = lcmsMapSet.childMaps.length
    val qcIdByLcmsMapId = {
      udsQuantChannels.map { qc => qc.getLcmsMapId.longValue -> qc.getId } toMap
    }
    
    // Define some mappings
    val masterPepInstByPepId = Map() ++ mergedRSM.peptideInstances.map( pi => pi.peptide.id -> pi )
    val identRsIdByRsmId = Map() ++ resultSummaries.map( rsm => rsm.id -> rsm.getResultSetId )
    val identRsIdByLcmsMapId = {
      Map() ++ udsQuantChannels.map { qc => qc.getLcmsMapId.longValue -> identRsIdByRsmId(qc.getIdentResultSummaryId) }
    }
    val qcIdByIdentRsmId = Map() ++ udsQuantChannels.map  { qc => qc.getIdentResultSummaryId -> qc.getId } 
    //val identPepInstById = new HashMap[Long, PeptideInstance]()
    val identPepInstByQcIdAndPepId = new HashMap[Long, HashMap[Long,PeptideInstance] ]()
    val identPepMatchById = new HashMap[Long, PeptideMatch]()
    //val identPepInstIdByPepMatchId = new HashMap[Long, Long]()
    
    // Load the result summaries corresponding to the quant channels
    for (identResultSummary <- resultSummaries) {
      
      val qcId = qcIdByIdentRsmId(identResultSummary.id)
      val qcIdentPepInstByPepId = identPepInstByQcIdAndPepId.getOrElseUpdate(qcId, new HashMap[Long,PeptideInstance])

      // Map peptide instances
      val peptideInstances = identResultSummary.peptideInstances
      for (pepInstance <- peptideInstances) {
        //val pepInstanceId = pepInstance.id
        //identPepInstById(pepInstanceId) = pepInstance
        qcIdentPepInstByPepId(pepInstance.peptide.id) = pepInstance
        //pepInstance.getPeptideMatchIds.foreach { identPepInstIdByPepMatchId(_) = pepInstanceId }
      }

      // Retrieve protein ids
      val rs = identResultSummary.resultSet.get

      // Map peptide matches by their id
      rs.peptideMatches.foreach { p => identPepMatchById(p.id) = p }
    }

    // Retrieve all identified peptide matches and map them by spectrum id
    //val allIdentPepMatches = identPepMatchById.values
    //val identPepMatchBySpectrumId = allIdentPepMatches.map { p => p.getMs2Query.spectrumId -> p } toMap
    
    def computeMasterQuantPeptides(): Array[MasterQuantPeptide] = {
      
      // Define some vars
      val mqPepIonsByMasterPepInst = new HashMap[PeptideInstance, ArrayBuffer[MasterQuantPeptideIon]]
      val unidentifiedMQPepIonByMft = new HashMap[Feature,MasterQuantPeptideIon]
      
      // --- Convert master features into master quant peptide ions --- 
      for (masterFt <- masterMap.features) {
        require(masterFt.children.length <= nbMaps, "master feature contains more child features than maps")
        
        // Convert child features into quant peptide ions
        val qPepIons = masterFt.children.map( childFt => _newQuantPeptideIon(masterFt,childFt) )
        
        // Create a master quant peptide ion for this master feature
        val peptideId = masterFt.relations.peptideId
        val masterPepInstOpt = if( peptideId > 0 ) masterPepInstByPepId.get(peptideId) else None
        val mqPepIon = this._newMasterQuantPeptideIon( masterFt, qPepIons, masterPepInstOpt )
        
        // Check if we have identified the master feature
        if( masterPepInstOpt.isEmpty )
          unidentifiedMQPepIonByMft += masterFt -> mqPepIon
        else
          mqPepIonsByMasterPepInst.getOrElseUpdate(masterPepInstOpt.get, new ArrayBuffer[MasterQuantPeptideIon] ) += mqPepIon
        
        ()
      }
      
      val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]
      
      // --- Convert identified master quant peptide ions into master quant peptides ---
      this.logger.info( mqPepIonsByMasterPepInst.size + " identified master features found")
      
      for( (masterPepInst,mqPepIons) <- mqPepIonsByMasterPepInst ) {
        masterQuantPeptides += _newMasterQuantPeptide(mqPepIons, Some(masterPepInst) )
      }
      
      // --- Convert unidentified master quant peptide ions into master quant peptides ---
      this.logger.info( unidentifiedMQPepIonByMft.size + " unidentified master features found")
      
      for( (masterFt,mqPepIon) <- unidentifiedMQPepIonByMft ) {
        masterQuantPeptides += _newMasterQuantPeptide( Array(mqPepIon), None )
      }
      
      // Compute the statistical analysis of abundance profiles
      /*val profilizer = new Profilizer(
        expDesign = expDesign,
        groupSetupNumber = 1, // TODO: retrieve from params
        masterQCNumber = udsMasterQuantChannel.getNumber
      )
      
      profilizer.computeMasterQuantPeptideProfiles(masterQuantPeptides, statTestsAlpha)*/
      
      masterQuantPeptides.toArray
    }
    
    private def _getSpectrumIdsOfFeature( ft: Feature, specIdByScanNum: HashMap[Int,Long] ): Array[Long] = {
      
      val childMapId = ft.relations.processedMapId
      
      // Retrieve MS2 scan numbers which are related to this feature
      val tmpFtIds = if (ft.isCluster) ft.subFeatures.map { _.id } else Array(ft.id)
      val ms2ScanNumbers = tmpFtIds.flatMap( i => ms2ScanNumbersByFtId.getOrElse(i, ArrayBuffer.empty[Int]) ).distinct
  
      // Stop if no MS2 available
      val specIds = if (ms2ScanNumbers.isEmpty) Array.empty[Long]
      else {
        // Retrieve corresponding MS2 spectra ids and remove existing redundancy
        ms2ScanNumbers.filter(specIdByScanNum.contains(_)).map(n => specIdByScanNum(n) )
      }
      
      specIds
    }
  
    private def _newQuantPeptideIon(masterFt: Feature, feature: Feature): QuantPeptideIon = {
      
      // Retrieve some vars
      val childMapId = feature.relations.processedMapId
      val udsQuantChannelId = qcIdByLcmsMapId(childMapId)
      val qcIdentPepInstByPepId = identPepInstByQcIdAndPepId(udsQuantChannelId)
      
      // Retrieve mapping between spectrum id and spectrum number
      val identRsId = identRsIdByLcmsMapId(childMapId)
      val specIdByScanNum = spectrumIdByRsIdAndScanNumber(identRsId)
      
      // Retrieve corresponding spectrum ids
      val ftSpecIds = _getSpectrumIdsOfFeature(feature,specIdByScanNum).toSet
      
      // Retrieve the master feature peptide id and the corresponding peptide instance for this quant channel
      val peptideId = masterFt.relations.peptideId
      val pepInstOpt = if( peptideId > 0 ) qcIdentPepInstByPepId.get(peptideId) else None
      val peptideIdOpt = pepInstOpt.map( _.peptide.id )
      val pepInstIdOpt = pepInstOpt.map( _.id )
      
      // Retrieve some vars related to identified peptide instance
      var (msQueryIdsOpt, bestPepMatchScoreOpt) = (Option.empty[Array[Long]], Option.empty[Float])

      for ( pepInst <- pepInstOpt ) {

        // Retrieve the corresponding peptide matches and the best peptide match score
        val pepMatches = pepInst.getPeptideMatchIds.map { identPepMatchById(_) }
        
        // Select MS queries which are co-eluting with the detected feature
        val msQueryIds = pepMatches
          .map( _.getMs2Query )
          .withFilter( query => ftSpecIds.contains(query.spectrumId) )
          .map(_.id)
          .distinct
        
        val filteredPepMatches = if( msQueryIds.isEmpty ) {
          logger.trace("identified feature can't be mapped with corresponding MS queries")
          pepMatches
        } else {
          msQueryIdsOpt = Some(msQueryIds)
          pepMatches.filter( pm => msQueryIds.contains(pm.msQuery.id) )
        }
        
        filteredPepMatches.groupBy(_.msQuery.charge).get(masterFt.charge).map { sameChargePepMatches =>
          bestPepMatchScoreOpt = Some(filteredPepMatches.reduce((a, b) => if (a.score > b.score) a else b).score)
        }
        
      }
      
      /*if( ftSpecIds.length != feature.ms2Count ) {
        println("nb specs="+ftSpecIds.length+" MS2 count="+feature.ms2Count)
      }*/
      
      val pepMatchesCount = msQueryIdsOpt.map(_.length).getOrElse(0)
      val ms2MatchingFrequency = if( ftSpecIds.size > 0 ) Some( pepMatchesCount.toFloat / ftSpecIds.size ) else None
      
      // Create a quant peptide ion corresponding the this LCMS feature
      new QuantPeptideIon(
        rawAbundance = feature.intensity,
        abundance = feature.getNormalizedIntensityOrIntensity,
        moz = feature.moz,
        elutionTime = feature.elutionTime,
        duration = feature.duration,
        correctedElutionTime = feature.getCorrectedElutionTimeOrElutionTime,
        scanNumber = feature.relations.apexScanInitialId,
        peptideMatchesCount = pepMatchesCount,//feature.ms2Count,
        ms2MatchingFrequency = ms2MatchingFrequency,
        bestPeptideMatchScore = bestPepMatchScoreOpt,
        // TODO: set feature properties in feature clusters 
        predictedElutionTime = feature.properties.flatMap(_.getPredictedElutionTime()),
        quantChannelId = udsQuantChannelId,
        peptideId = peptideIdOpt,
        peptideInstanceId = pepInstIdOpt,
        msQueryIds = msQueryIdsOpt,
        lcmsFeatureId = feature.id,
        lcmsMasterFeatureId = Some(masterFt.id),
        unmodifiedPeptideIonId = None, // TODO: set this value
        selectionLevel = feature.selectionLevel
      )
      
    }
    
    private def _newMasterQuantPeptideIon(
      masterFt: Feature,
      qPepIons: Seq[QuantPeptideIon],
      masterPepInstAsOpt: Option[PeptideInstance]
    ): MasterQuantPeptideIon = {
      require( qPepIons != null && qPepIons.length > 0, "qPepIons must not be empty")
      require( masterFt.isMaster, "can't create a master quant peptide ion wihtout a master feature" )
      
      // Map quant peptide ions by feature id or feature id
      val qPepIonByQcId = Map() ++ qPepIons.map( qpi => qpi.quantChannelId -> qpi )
      require( qPepIonByQcId.size == qPepIons.length, "duplicated feature detected in quant peptide ions" )
      
      // FIXME: compute this value
      val pepMatchesCount = 0
      /*val pepMatchesCount = if( masterPepInstAsOpt.isEmpty ) 0
      else {
        val psmById = // THIS NEED TO BE COMPUTED FROM MERGED RSM
        val psmsByCharge = masterPepInstAsOpt.get.getPeptideMatchIds.map( psmById(_) ).groupBy(_.msQuery.charge)
        psmsByCharge( masterFt.charge ).length
      }*/
      
      new MasterQuantPeptideIon(
        id = MasterQuantPeptideIon.generateNewId(),
        unlabeledMoz = masterFt.moz,
        charge = masterFt.charge,
        elutionTime = masterFt.getCorrectedElutionTimeOrElutionTime,
        peptideMatchesCount = pepMatchesCount,
        masterQuantPeptideId = 0,
        resultSummaryId = mergedRsmId,
        peptideInstanceId = masterPepInstAsOpt.map(_.id),
        bestPeptideMatchId = masterPepInstAsOpt.map(_.bestPeptideMatchId),
        lcmsMasterFeatureId = Some(masterFt.id),
        selectionLevel = masterFt.selectionLevel,
        quantPeptideIonMap = qPepIonByQcId
      )
    }
    
    private def _newMasterQuantPeptide(
      mqPepIons: Seq[MasterQuantPeptideIon],
      masterPepInstAsOpt: Option[PeptideInstance]
    ): MasterQuantPeptide = {
      require( mqPepIons != null && mqPepIons.length > 0, "mqPepIons must not be empty")
      
      // Generate and update master quant peptide id
      val mqPeptideId = MasterQuantPeptide.generateNewId
      mqPepIons.foreach { mqPepIon =>
        mqPepIon.masterQuantPeptideId = mqPeptideId
      }
      
      // Filter MQ peptide ions using the selection level
      var filteredMQPepIons = mqPepIons.filter(_.selectionLevel >= 2 )
      // Fall back to input list if none MQ peptide is selected
      if( filteredMQPepIons.isEmpty ) filteredMQPepIons = mqPepIons
  
      // Keep the MQP with the highest intensity sum
      // TODO: allows to sum charge states
      //val bestMQP = filteredMQPepIons.maxBy( _.calcAbundanceSum )
      val bestMQP = filteredMQPepIons.maxBy( _.calcFrequency(qcCount) )
      
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
        selectionLevel = bestMQP.selectionLevel,
        resultSummaryId = mergedRsmId
      )
  
    }
    
  } // End of MqPepsComputer Class
  
  def computeMasterQuantPeptides(
    udsMasterQuantChannel: MasterQuantitationChannel,
    mergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {    
    val mqPepsComputer = new MQPepsComputer(udsMasterQuantChannel,mergedRSM,resultSummaries)
    mqPepsComputer.computeMasterQuantPeptides()
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
      val mqPeps = new ArrayBuffer[MasterQuantPeptide]
      val abundanceSumByQcId = new HashMap[Long,Float]
      val rawAbundanceSumByQcId = new HashMap[Long,Float]
      val pepMatchesCountByQcId = new HashMap[Long,Int]
      
      
      for( mergedPepInst <- mergedProtSet.peptideSet.getPeptideInstances ) {
        // If the peptide has been quantified
        if( mqPepByPepInstId.contains(mergedPepInst.id) ) {
          val mqp = mqPepByPepInstId( mergedPepInst.id )
          mqPeps += mqp
          
          if( mqp.selectionLevel >= 2 ) selectedMQPepIds += mqp.id
          
          for( (qcId,quantPep) <- mqp.quantPeptideMap ) {
            abundanceSumByQcId.getOrElseUpdate(qcId,0)
            abundanceSumByQcId(qcId) += quantPep.abundance
            
            rawAbundanceSumByQcId.getOrElseUpdate(qcId,0)
            rawAbundanceSumByQcId(qcId) += quantPep.rawAbundance

            pepMatchesCountByQcId.getOrElseUpdate(qcId,0)
            pepMatchesCountByQcId(qcId) += quantPep.peptideMatchesCount
          }
        }
      }
      
      val quantProteinSetByQcId = new HashMap[Long,QuantProteinSet]
      for( (qcId,abundanceSum) <- abundanceSumByQcId ) {
        quantProteinSetByQcId(qcId) = new QuantProteinSet(
          rawAbundance = rawAbundanceSumByQcId(qcId),
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
        masterQuantPeptides = mqPeps.toArray,
        selectionLevel = 2,
        properties = Some(mqProtSetProps)
      )
      
      mqProtSets += mqProteinSet
    }
    
    // Compute the statistical analysis of abundance profiles
    /*val profilizer = new Profilizer(
      expDesign = expDesign,
      groupSetupNumber = 1, // TODO: retrieve from params
      masterQCNumber = udsMasterQuantChannel.getNumber
    )
    
    profilizer.computeMasterQuantProtSetProfiles(mqProtSets, statTestsAlpha)*/
    
    mqProtSets.toArray
  }
 
}