package fr.proline.core.algo.msq.summarizing

import scala.collection.JavaConversions.{ collectionAsScalaIterable, setAsJavaSet }
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.util.collection._
import fr.profi.util.ms._
import fr.proline.core.om.model.lcms.{MapSet,Feature}
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._

/**
 * @author David Bouyssie
 *
 */
class LabelFreeEntitiesSummarizer(
  lcmsMapSet: MapSet,
  spectrumIdByRsIdAndScanNumber: LongMap[LongMap[Long]],
  ms2ScanNumbersByFtId: LongMap[Array[Int]]
) extends IMqPepAndProtEntitiesSummarizer with LazyLogging {
  
  private class MQPepsComputer(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRsm : ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ) extends LazyLogging {
    
    // Define some values
    val quantMergedRsmId = quantMergedRsm.id
    val qttRSMId = masterQuantChannel.quantResultSummaryId
    val quantChannels = masterQuantChannel.quantChannels
    val qcCount = quantChannels.length
    
    // Define some mappings
    val masterPepInstByPepId = quantMergedRsm.peptideInstances.toLongMapWith( pi => pi.peptide.id -> pi )
    val identRsIdByRsmId = resultSummaries.toLongMapWith( rsm => rsm.id -> rsm.getResultSetId )
    val qcIdByIdentRsmId = quantChannels.toLongMapWith( qc => qc.identResultSummaryId -> qc.id )
    val identPepInstByQcIdAndPepId = new LongMap[LongMap[PeptideInstance] ](qcCount)
    val identPepMatchById = new LongMap[PeptideMatch]()
    
    // Load the result summaries corresponding to the quant channels
    for (identResultSummary <- resultSummaries) {
      
      val qcId = qcIdByIdentRsmId(identResultSummary.id)
      val qcIdentPepInstByPepId = identPepInstByQcIdAndPepId.getOrElseUpdate(qcId, new LongMap[PeptideInstance])
  
      // Map peptide instances
      val peptideInstances = identResultSummary.peptideInstances
      for (pepInstance <- peptideInstances) {
        qcIdentPepInstByPepId += (pepInstance.peptide.id, pepInstance)
      }
  
      // Map peptide matches by their id
      identResultSummary.resultSet.get.peptideMatches.foreach { p => identPepMatchById(p.id) = p }
    }
    
    // Retrieve some LC-MS values
    val masterMap = lcmsMapSet.masterMap
    val lcmsMapById = lcmsMapSet.childMaps.mapByLong( _.id )
    val nbMaps = lcmsMapSet.childMaps.length
    val qcIdByLcmsMapId = quantChannels.toLongMapWith { qc => qc.lcmsMapId.get -> qc.id }
    val identRsIdByLcmsMapId = quantChannels.toLongMapWith { qc => 
      qc.lcmsMapId.get -> identRsIdByRsmId(qc.identResultSummaryId)
    }
    
    def computeMasterQuantPeptides(): Array[MasterQuantPeptide] = {
      
      // Define some vars
      val mqPepIonsByMasterPepInst = new HashMap[PeptideInstance, ArrayBuffer[MasterQuantPeptideIon]]
      val unidentifiedMQPepIonByMft = new HashMap[Feature,MasterQuantPeptideIon]
      
      // --- Convert master features into master quant peptide ions ---
      for (masterFt <- masterMap.features) {
        require(masterFt.children.length <= nbMaps, "master feature contains more child features than maps")
        
        // Convert child features into quant peptide ions
        val qPepIons = masterFt.children.map( childFt => _buildQuantPeptideIon(masterFt,childFt) )
        
        // Create a master quant peptide ion for this master feature
        val peptideId = masterFt.relations.peptideId
        val masterPepInstOpt = if( peptideId > 0 ) masterPepInstByPepId.get(peptideId) else None
        val mqPepIon = this._buildMasterQuantPeptideIon( masterFt, qPepIons, masterPepInstOpt )
        
        // Check if we have identified the master feature
        if( masterPepInstOpt.isEmpty )
          unidentifiedMQPepIonByMft += masterFt -> mqPepIon
        else
          mqPepIonsByMasterPepInst.getOrElseUpdate(masterPepInstOpt.get, new ArrayBuffer[MasterQuantPeptideIon] ) += mqPepIon
        
        ()
      }
      
      val masterQuantPeptides = new ArrayBuffer[MasterQuantPeptide]( mqPepIonsByMasterPepInst.size )
      
      // --- Convert identified master quant peptide ions into master quant peptides ---
      this.logger.info( mqPepIonsByMasterPepInst.size + " identified master features found")
      
      for( (masterPepInst,mqPepIons) <- mqPepIonsByMasterPepInst ) {
        masterQuantPeptides += BuildMasterQuantPeptide(mqPepIons, Some(masterPepInst), quantMergedRsmId)
      }
      
      // --- Convert unidentified master quant peptide ions into master quant peptides ---
      this.logger.info( unidentifiedMQPepIonByMft.size + " unidentified master features found")
      
      for( (masterFt,mqPepIon) <- unidentifiedMQPepIonByMft ) {
        masterQuantPeptides += BuildMasterQuantPeptide( Array(mqPepIon), None, quantMergedRsmId )
      }
      
      
      
      masterQuantPeptides.toArray
    }
    
    private def _getSpectrumIdsOfFeature( ft: Feature, specIdByScanNum: LongMap[Long] ): Array[Long] = {
      
      val childMapId = ft.relations.processedMapId
      
      // Retrieve MS2 scan numbers which are related to this feature
      val tmpFtIds = if (ft.isCluster) ft.subFeatures.map { _.id } else Array(ft.id)
      val ms2ScanNumbers = tmpFtIds.flatMap( i => ms2ScanNumbersByFtId.getOrElse(i, Array.empty[Int]) ).distinct
  
      // Stop if no MS2 available
      val specIds = if (ms2ScanNumbers.isEmpty) Array.empty[Long]
      else {
        // Retrieve corresponding MS2 spectra ids and remove existing redundancy
        ms2ScanNumbers.filter(specIdByScanNum.contains(_)).map(n => specIdByScanNum(n) )
      }
      
      specIds
    }
  
    private def _buildQuantPeptideIon(masterFt: Feature, feature: Feature): QuantPeptideIon = {
      
      // Retrieve some vars
      val childMapId = feature.relations.processedMapId
      val quantChannelId = qcIdByLcmsMapId(childMapId)
      val qcIdentPepInstByPepId = identPepInstByQcIdAndPepId(quantChannelId)
      
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

      if( pepInstOpt.isDefined ) {
        val pepInst = pepInstOpt.get

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
        
        val sameChargePepMatchesOpt = filteredPepMatches.groupBy(_.msQuery.charge).get(masterFt.charge)
        if( sameChargePepMatchesOpt.isDefined ) {
          bestPepMatchScoreOpt = Some(sameChargePepMatchesOpt.get.maxBy(_.score).score)
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
        quantChannelId = quantChannelId,
        peptideId = peptideIdOpt,
        peptideInstanceId = pepInstIdOpt,
        msQueryIds = msQueryIdsOpt,
        lcmsFeatureId = if( feature.id > 0 ) Some(feature.id) else None,
        lcmsMasterFeatureId = if( masterFt.id > 0) Some(masterFt.id) else None,
        unmodifiedPeptideIonId = None, // TODO: set this value ???
        selectionLevel = feature.selectionLevel
      )
      
    }
    
    private def _buildMasterQuantPeptideIon(
      masterFt: Feature,
      qPepIons: Seq[QuantPeptideIon],
      masterPepInstAsOpt: Option[PeptideInstance]
    ): MasterQuantPeptideIon = {
      require( qPepIons != null && qPepIons.length > 0, "qPepIons must not be empty")
      require( masterFt.isMaster, "can't create a master quant peptide ion wihtout a master feature" )
      
      // Map quant peptide ions by feature id or feature id
      val qPepIonByQcId = qPepIons.toLongMapWith( qpi => qpi.quantChannelId -> qpi )
      require( qPepIonByQcId.size == qPepIons.length, "duplicated feature detected in quant peptide ions" )
      
      // Compute the total number of peptide matches
      val pepMatchesCount = qPepIons.map( _.peptideMatchesCount ).sum
      
      // If a peptide instance id defined for this ion
      val propsOpt = masterPepInstAsOpt.map { masterPepInst =>
        
        // Retrieve the best peptide match id for each quantitative channel
        val bestPeptideMatchIdMapBuilder = scala.collection.immutable.HashMap.newBuilder[Long,Long]
        for (identResultSummary <- resultSummaries) {
          
          val qcId = qcIdByIdentRsmId(identResultSummary.id)
          val qcIdentPepInstByPepId = identPepInstByQcIdAndPepId(qcId)
          
          if( qcIdentPepInstByPepId.contains(masterPepInst.peptide.id) ) {
            val qcIdentPepInst = qcIdentPepInstByPepId(masterPepInst.peptide.id)            
            bestPeptideMatchIdMapBuilder += qcId -> qcIdentPepInst.bestPeptideMatchId
          }
        }
        
        new MasterQuantPeptideIonProperties(
          bestPeptideMatchIdMap = bestPeptideMatchIdMapBuilder.result()
        )
      }
      
      new MasterQuantPeptideIon(
        id = MasterQuantPeptideIon.generateNewId(),
        unlabeledMoz = masterFt.moz,
        charge = masterFt.charge,
        elutionTime = masterFt.getCorrectedElutionTimeOrElutionTime,
        peptideMatchesCount = pepMatchesCount,
        masterQuantPeptideId = 0,
        resultSummaryId = quantMergedRsmId,
        peptideInstanceId = masterPepInstAsOpt.map(_.id),
        bestPeptideMatchId = masterPepInstAsOpt.map(_.bestPeptideMatchId),
        lcmsMasterFeatureId = Some(masterFt.id),
        selectionLevel = masterFt.selectionLevel,
        quantPeptideIonMap = qPepIonByQcId,
        properties = propsOpt
      )
    }
    
  } // End of MqPepsComputer Class
  
  def computeMasterQuantPeptides(
    masterQuantChannel: MasterQuantChannel,
    quantMergedRSM: ResultSummary,
    resultSummaries: Seq[ResultSummary]
  ): Array[MasterQuantPeptide] = {
    val mqPepsComputer = new MQPepsComputer(masterQuantChannel,quantMergedRSM,resultSummaries)
    mqPepsComputer.computeMasterQuantPeptides()
  }
 
}