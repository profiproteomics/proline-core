package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.stat.StatUtils
import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.math.median
import fr.profi.util.primitives.isZeroOrNaN
import fr.profi.util.random.randomGaussian
import fr.proline.core.algo.msq.profilizer._
import fr.proline.core.algo.msq.profilizer.filtering._
import fr.proline.core.om.model.msq._

// TODO: recompute raw abundances from peakels
// (smoothing methods, area VS apex intensity, first isotope vs max one vs isotope pattern fitting)

case class ProfilizerStatConfig(
  // Note: maxCv is experimental => DO NOT PUT IN GUI
  var maxCv: Option[Float] = None, // TODO: do not discard peptides => apply this filter during the summarization step ?
  statTestsAlpha: Float = 0.01f,
  minZScore: Float = 0.4f, // ZScore equals ln(ratio) followed by standardisation
  minPsmCountPerRatio: Int = 0, // TODO: remove me ???
  applyNormalization: Boolean = true,
  
  applyMissValInference: Boolean = true, // TODO: remove me when IHMs haven been updated
  // TODO: replace Some(MissingAbundancesInferenceConfig) by None when IHMs haven been updated
  var missValInferenceMethod: String = null,
  var missValInferenceConfig: Option[MissingAbundancesInferenceConfig] = None,
  
  applyVarianceCorrection: Boolean = true,
  applyTTest: Boolean = true,
  applyZTest: Boolean = true
) {
  // Workaround for jackson support of default values
  if(missValInferenceMethod == null) missValInferenceMethod = MissingAbundancesInferenceMethod.GAUSSIAN_MODEL
  if(missValInferenceConfig.isEmpty) missValInferenceConfig = Some(MissingAbundancesInferenceConfig())
}

case class ProfilizerConfig(
  discardMissedCleavedPeptides: Boolean = true,
  var missedCleavedPeptideFilteringMethod: Option[String] = None,
  
  discardOxidizedPeptides: Boolean = true,
  var oxidizedPeptideFilteringMethod: Option[String] = None,
  
  //discardLowIdentPeptides: Boolean = false,
  useOnlySpecificPeptides: Boolean = true,
  
  applyProfileClustering: Boolean = true,
  var profileClusteringMethod: Option[String] = None,
  profileClusteringConfig: Option[MqPeptidesClustererConfig] = None,
  
  var abundanceSummarizingMethod: String = null,
  
  peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
  proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig()
) {
  // Workaround for jackson support of default values
  if( oxidizedPeptideFilteringMethod.isEmpty ) {
    oxidizedPeptideFilteringMethod = Some(OxidizedPeptideFilteringMethod.DISCARD_All_FORMS)
  }
  // Workaround for jackson support of default values
  if( missedCleavedPeptideFilteringMethod.isEmpty ) {
    missedCleavedPeptideFilteringMethod = Some(MissedCleavedPeptideFilteringMethod.DISCARD_All_FORMS)
  }
  if(profileClusteringMethod.isEmpty) {
    profileClusteringMethod = Some(MqPeptidesClusteringMethod.QUANT_PROFILE)
  }
  if( abundanceSummarizingMethod == null) {
    abundanceSummarizingMethod = AbundanceSummarizer.Method.MEAN
  }
}

/**
 * Analyze profiles of Master Quant Peptides and Master Quant Protein sets
 */
class Profilizer( expDesign: ExperimentalDesign, groupSetupNumber: Int = 1, masterQCNumber: Int = 1 ) extends LazyLogging {
  
  val errorModelBinsCount = 1
  
  // Retrieve some vars
  private val masterQuantChannels = expDesign.masterQuantChannels
  private val groupSetup = expDesign.groupSetups(groupSetupNumber-1)
  private val sampleNumbersByGroupNumber = expDesign.getSampleNumbersByGroupNumber(groupSetupNumber)
  
  private var minSamplesCountPerGroup = Int.MaxValue
  for( (groupNumber, sampleNumbers) <- sampleNumbersByGroupNumber if sampleNumbers.length < minSamplesCountPerGroup ) {
    minSamplesCountPerGroup = sampleNumbers.length
  }
  
  private val masterQC = masterQuantChannels.find( _.number == masterQCNumber ).get
  private val quantChannels = masterQC.quantChannels
  private val qcIds = quantChannels.map( _.id )
  private val qcCount = qcIds.length
  private val qcIdxById = qcIds.zip( qcIds.indices ).toMap
  private val quantChannelsBySampleNumber = quantChannels.groupBy( _.sampleNumber )
  private val qcSampleNumbers = quantChannels.map(_.sampleNumber)
  private val qcGroupNumbers = {
    val sampleByNum = expDesign.biologicalSamples.map( s => s.number -> s ).toMap
    
    val gNumBySNum = (for( (gNum,sNums) <- sampleNumbersByGroupNumber; sNum <- sNums) yield sNum -> gNum).toMap

    quantChannelsBySampleNumber.flatMap { case (sNum,qcs) =>
      val gNum = gNumBySNum(sNum)
      qcs.map { qc => gNum }
    }
  } toArray
  private val sampleCount = expDesign.biologicalSamples.length
  private val samplesQcCount = expDesign.biologicalSamples.map( s => quantChannelsBySampleNumber(s.number).length )
  
  private var minQCsCountPerSample = Int.MaxValue
  for( (sampleNumber, quantChannels) <- quantChannelsBySampleNumber if quantChannels.length < minQCsCountPerSample ) {
    minQCsCountPerSample = quantChannels.length
  }
  
  /**
   * Computes MasterQauntPeptide profiles.
   */
  def computeMasterQuantPeptideProfiles( masterQuantPeptides: Seq[MasterQuantPeptide], config: ProfilizerConfig ) {    
    require( masterQuantPeptides.length >= 10, "at least 10 peptides are required for profile analysis")
    
    // --- Reset some values ---
    for( mqPep <- masterQuantPeptides ) {
      
      // Reset selection level if not too low
      // TODO: add a parameter which specify to keep or not previously computed selection levels
      if( mqPep.selectionLevel == 1 )
        mqPep.selectionLevel = 2
        
      val mqPepPropsOpt = mqPep.properties
      if(mqPepPropsOpt.isEmpty) {
        mqPep.properties = Some(MasterQuantPeptideProperties())
      } else {
        // Reset quant profiles for this masterQuantPeptide
        val mqPepProps = mqPepPropsOpt.get
        mqPepProps.setMqPepProfileByGroupSetupNumber(None)
      }
      
    }
    
    // --- Apply protein set specific filter if requested ---
    if( config.useOnlySpecificPeptides ) {
      UnspecificPeptideFilterer.discardPeptides(masterQuantPeptides)
    }
    
    // --- Apply MC filter if requested ---
    if( config.discardMissedCleavedPeptides ) {
      require( config.missedCleavedPeptideFilteringMethod.isDefined, "config.missedCleavedPeptideFilteringMethod is empty")
      MissedCleavedPeptideFilterer.discardPeptides(masterQuantPeptides, config.missedCleavedPeptideFilteringMethod.get)
    }
    
    // --- Apply Oxidation filter if requested ---
    if( config.discardOxidizedPeptides ) {
      require( config.oxidizedPeptideFilteringMethod.isDefined, "config.oxidizedPeptideFilteringMethod is empty")
      OxidizedPeptideFilterer.discardPeptides(masterQuantPeptides, config.oxidizedPeptideFilteringMethod.get)
    }
    
    // Keep master quant peptides passing all filters (i.e. have a selection level higher than 1)
    //val( mqPepsAfterAllFilters, deselectedMqPeps ) = masterQuantPeptides.partition( _.selectionLevel >= 2 )
    // FIXME: DBO => should we work only with filtered mqPeps ?
    val mqPepsAfterAllFilters = masterQuantPeptides
    
    // Reset quant peptide abundance of deselected master quant peptides
    // DBO: is this useful ???
    /*for( mqPep <- deselectedMqPeps; (qcid,qPep) <- mqPep.quantPeptideMap ) {
      qPep.abundance = Float.NaN
    }*/
    
    // --- Compute the PSM count matrix ---
    val psmCountMatrix = mqPepsAfterAllFilters.map( _.getPepMatchesCountsForQuantChannels(qcIds) ).toArray
    
    // --- Compute the abundance matrix ---
    val rawAbundanceMatrix = mqPepsAfterAllFilters.map( _.getRawAbundancesForQuantChannels(qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( config.peptideStatConfig.applyNormalization == false ) rawAbundanceMatrix
    else AbundanceNormalizer.normalizeAbundances(rawAbundanceMatrix)
    
    require( normalizedMatrix.length == rawAbundanceMatrix.length, "error during normalization, some peptides were lost...")
    //println( s"normalizedMatrix.length: ${normalizedMatrix.length}")
    
    // Update master quant peptides abundances after normalization
    for( (mqPep, abundances) <- mqPepsAfterAllFilters.zip(normalizedMatrix) ) {
      mqPep.setAbundancesForQuantChannels(abundances,qcIds)
    }
    
    // Define some mappings
    val mqPepById = new HashMap[Long,MasterQuantPeptide]()
    mqPepById.sizeHint(mqPepsAfterAllFilters.length)

    val ratiosByMQPepId = new HashMap[Long,ArrayBuffer[Option[ComputedRatio]]]()
    ratiosByMQPepId.sizeHint(mqPepsAfterAllFilters.length)
    
    // Compute these mappings
    for( mqPep <- mqPepsAfterAllFilters ) {
      mqPepById += mqPep.id -> mqPep
      ratiosByMQPepId += mqPep.id -> new ArrayBuffer[Option[ComputedRatio]]
    }
    
    val maxCv = config.peptideStatConfig.maxCv
    
    // Iterate over the ratio definitions
    for ( ratioDef <- groupSetup.ratioDefinitions ) {
      
      val( filledMatrix, ratios ) = computeRatios(
        ratioDef,
        normalizedMatrix,
        psmCountMatrix,
        config.peptideStatConfig
      )
 
      for ( ratio <- ratios ) {
        val index = ratio.entityId.toInt
        val masterQuantPep = mqPepsAfterAllFilters(index)
        val abundances = filledMatrix(index)
        
        // Update master quant peptide abundances
        masterQuantPep.setAbundancesForQuantChannels(abundances,qcIds)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numerator.toFloat,
          denominator = ratio.denominator.toFloat,
          //numeratorCv = ratio.numeratorSummary.getCv().toFloat,
          //denominatorCv = ratio.denominatorSummary.getCv().toFloat,
          state = ratio.state.map(_.id).getOrElse(AbundanceRatioState.Invariant.id),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue,
          zScore = ratio.zScore
        )
        
        // TODO: compute these statistics independently from ratios ???
        def isCvTooHigh(summary: ExtendedStatisticalSummary): Boolean = {
          if( maxCv.isEmpty ) return false
          if( summary.getN > 2 && summary.getCv() > maxCv.get) true else false
        }
        if( isCvTooHigh(ratio.numeratorSummary) || isCvTooHigh(ratio.denominatorSummary) ) {
          if( masterQuantPep.selectionLevel == 2 ) {
            masterQuantPep.selectionLevel = 1
            masterQuantPep.properties.get.setDiscardingReason( Some("CV is too high") )
          }
        }
        
        ratiosByMQPepId(masterQuantPep.id) += Some(computedRatio)
      }
    }
    
    // Update the profiles using the obtained results
    for ( (mqPepId,ratios) <- ratiosByMQPepId ) {

      val mqPep = mqPepById(mqPepId)
      val mqPepProps = mqPep.properties.getOrElse( new MasterQuantPeptideProperties() )
      
      val quantProfile = new MasterQuantPeptideProfile( ratios = ratios.toList )
      val mqPeptProfileMap = mqPepProps.getMqPepProfileByGroupSetupNumber.getOrElse( HashMap() )
      mqPeptProfileMap += ( groupSetupNumber -> quantProfile )
      mqPepProps.setMqPepProfileByGroupSetupNumber( Some(mqPeptProfileMap) )
      
      mqPep.properties = Some(mqPepProps)
    }
    
    ()
  }
  
  def computeMasterQuantProtSetProfiles( masterQuantProtSets: Seq[MasterQuantProteinSet], config: ProfilizerConfig ) {
    require( masterQuantProtSets.length >= 10, "at least 10 protein sets are required for profile analysis")
    
    val qcsSampleNum = quantChannels.groupBy(_.sampleNumber)
    val bgBySampleNum = groupSetup.biologicalGroups.view.flatMap { bg =>
      bg.sampleNumbers.map { sampleNumber =>
        sampleNumber -> bg
      }
    } toMap
    val bgByQcIdx = quantChannels.map( qc => bgBySampleNum(qc.sampleNumber) )
    
    // Discard low identified peptides if requested
    // TODO: remove me ?
    /*if(config.discardLowIdentPeptides) {
      
      for( mqProtSet <- masterQuantProtSets ) {
        
        // Keep only selected master quant peptides in the dataset
        // TODO: keep also manual selection here
        val selectedMqPeps = mqProtSet.masterQuantPeptides.filter( _.selectionLevel >= 2 )
        
        if( selectedMqPeps.isEmpty == false ) {
          
          // Determine the number of BGs where the Master quant peptide has been identified
          val mqPepsWithIdentBgsCount = selectedMqPeps.map { tmpMqPep =>
            val psmCounts = tmpMqPep.getPepMatchesCountsForQuantChannels(qcIds)
            val psmCountsByBg = psmCounts.zip(bgByQcIdx).groupBy(_._2)
            val psmCountsSumByBg = psmCountsByBg.map { case (bg,values) =>
              bg -> values.foldLeft(0) { case (s,t) => s + t._1 }
            }
            
            val identBgsCount = psmCountsSumByBg.values.count( _ > 0)
            
            tmpMqPep -> identBgsCount
          }
          
          // Keep only the MQ peptides with the highest identification frequency
          val maxIdentBgsCount = mqPepsWithIdentBgsCount.view.map(_._2).max
          val highIdentMqPeps = mqPepsWithIdentBgsCount.filter(_._2 == maxIdentBgsCount)
          
          mqProtSet.properties.get.selectedMasterQuantPeptideIds = Some(highIdentMqPeps.map(_._1.id))
        }

      }
    } else {
      for( mqProtSet <- masterQuantProtSets ) {
        val selectedMqPepIds = mqProtSet.masterQuantPeptides.withFilter( _.selectionLevel >= 2 ).map(_.id)
        mqProtSet.properties.get.selectedMasterQuantPeptideIds = Some(selectedMqPepIds)
      }
    }*/
    
    // Update the list of selectedMasterQuantPeptideIds
    for( mqProtSet <- masterQuantProtSets ) {
      val selectedMqPepIds = mqProtSet.masterQuantPeptides.withFilter( _.selectionLevel >= 2 ).map(_.id)
      mqProtSet.properties.get.selectedMasterQuantPeptideIds = Some(selectedMqPepIds)
    }

    // Clusterize MasterQuantPeptides according to the provided method
    val clusteringMethodName = if( config.applyProfileClustering ) config.profileClusteringMethod.get
    else MqPeptidesClusteringMethod.PEPTIDE_SET.toString

    val mqPepsClusters = MasterQuantPeptidesClusterer.computeMqPeptidesClusters(
      clusteringMethodName,
      config.profileClusteringConfig,
      groupSetupNumber = 1,
      masterQuantProtSets
    )
    
    // --- Iterate over MQ peptides clusters to summarize corresponding abundance matrix ---
    val rawAbundanceMatrixBuffer = new ArrayBuffer[Array[Float]](mqPepsClusters.length)
    val rawAbundancesByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Float]]
    rawAbundancesByProfileClusterBuilder.sizeHint(rawAbundanceMatrixBuffer.length)
    val abundanceMatrixBuffer = new ArrayBuffer[Array[Float]](mqPepsClusters.length)
    val psmCountMatrixBuffer = new ArrayBuffer[Array[Int]](mqPepsClusters.length)
    val mqPepsRatiosCvs = new ArrayBuffer[ArrayBuffer[Float]](mqPepsClusters.length)
    
    val abSumMethod = AbundanceSummarizer.Method.withName(config.abundanceSummarizingMethod)
    
    for( mqPepsCluster <- mqPepsClusters ) {
      //println("mqPepsCluster.name",mqPepsCluster.name)
      
      val clusteredMqPeps = mqPepsCluster.mqPeptides
      
      // Summarize raw abundances of the current profile cluster
      val mqPepRawAbundanceMatrix = clusteredMqPeps.map( _.getRawAbundancesForQuantChannels(qcIds) ).toArray
      
      val summarizedRawAbundances = this.summarizeMatrix(mqPepRawAbundanceMatrix, abSumMethod)

      rawAbundanceMatrixBuffer += summarizedRawAbundances
      rawAbundancesByProfileClusterBuilder += mqPepsCluster -> summarizedRawAbundances
      
      // Summarize abundances of the current profile cluster
      val mqPepAbundanceMatrix = clusteredMqPeps.map( _.getAbundancesForQuantChannels(qcIds) ).toArray
      
      val abRow = this.summarizeMatrix(mqPepAbundanceMatrix, abSumMethod)
      abundanceMatrixBuffer += abRow
      
      // Summarize PSM counts of the current profile cluster
      val psmCountMatrix = clusteredMqPeps.map( _.getPepMatchesCountsForQuantChannels(qcIds) ).toArray
      psmCountMatrixBuffer += psmCountMatrix.transpose.map( _.sum )
      
      // Retrieve ratios
      val ratioMatrix = new ArrayBuffer[Array[Float]](clusteredMqPeps.length)
      clusteredMqPeps.foreach { mqPep =>
        val profile = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get(groupSetupNumber)
        val mqPepRatioValues = profile.getRatios().map(_.map(_.ratioValue).getOrElse(Float.NaN)).toArray
        ratioMatrix += mqPepRatioValues
      }
      // Transpose the ratioMatrix to work with ratios of the same definition
      val ratiosCvs = ratioMatrix.transpose.map { computedRatioValues =>
        val defRatioValues = computedRatioValues.filter( !isZeroOrNaN(_) )
        if( defRatioValues.length < 3 ) Float.NaN
        else {
          // FXIME: transform ratios before computing their CV ?
          coefficientOfVariation(defRatioValues.map(_.toDouble).toArray).toFloat
        }
      }
      
      mqPepsRatiosCvs += ratiosCvs
    }
    
    // --- Map raw abundances by the corresponding MQ peptides cluster ---
    val rawAbundancesByProfileCluster = rawAbundancesByProfileClusterBuilder.result
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( config.proteinStatConfig.applyNormalization == false ) abundanceMatrixBuffer.toArray
    else AbundanceNormalizer.normalizeAbundances(abundanceMatrixBuffer.toArray)
    
    // --- Compute the ratios corresponding to each profile cluster ---
    
    // Create a map which will store the ratios corresponding to each profile cluster
    val ratiosByMqPepCluster = mqPepsClusters.view.map( _ -> new ArrayBuffer[Option[ComputedRatio]] ).toMap
    
    // Iterate over the ratio definitions
    var finalMatrix = normalizedMatrix
    for ( ratioDef <- groupSetup.ratioDefinitions ) {
      
      val( filledMatrix, ratios ) = computeRatios(
        ratioDef,
        normalizedMatrix,
        psmCountMatrixBuffer.toArray,
        config.proteinStatConfig
      )
      // FIXME: perform the missing value inference only once
      finalMatrix = filledMatrix
      
      assert(filledMatrix.length == abundanceMatrixBuffer.length)
      
      val computedRatioIdx = ratioDef.number - 1
      for ( ratio <- ratios ) {
        
        val rowIndex = ratio.entityId.toInt
        val mqPepCluster = mqPepsClusters(rowIndex)
        val mqPepRatiosCvs = mqPepsRatiosCvs(rowIndex)
        val ratioCv = mqPepRatiosCvs(computedRatioIdx)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numerator.toFloat,
          denominator = ratio.denominator.toFloat,
          cv = Some(ratioCv),
          //numeratorCv = ratio.numeratorSummary.getCv().toFloat,
          //denominatorCv = ratio.denominatorSummary.getCv().toFloat,
          state = ratio.state.map(_.id).getOrElse(0),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue,
          zScore = ratio.zScore
        )
        
        ratiosByMqPepCluster( mqPepCluster ) += Some(computedRatio)
      }
    }
    
    // --- Map normalized abundances by the corresponding profile cluster ---
    val abundancesByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Float]]
    abundancesByProfileClusterBuilder.sizeHint(finalMatrix.length)
    
    finalMatrix.indices.foreach { rowIndex =>
      val mqPepCluster = mqPepsClusters(rowIndex)
      abundancesByProfileClusterBuilder += mqPepCluster -> finalMatrix(rowIndex)
    }
    
    val abundancesByProfileCluster = abundancesByProfileClusterBuilder.result
    
    // Update the profiles using the obtained results
    val mqProfilesByProtSet = masterQuantProtSets.view.map( _ -> new ArrayBuffer[MasterQuantProteinSetProfile] ).toMap
    
    // Group profiles by Master Quant protein set
    for ( (mqPepCluster,ratios) <- ratiosByMqPepCluster ) {

      val mqProtSet = mqPepCluster.mqProteinSet
      val mqPeptideIds = mqPepCluster.mqPeptides.map(_.id).toArray
      val rawAbundances = rawAbundancesByProfileCluster(mqPepCluster)
      val abundances = abundancesByProfileCluster(mqPepCluster)
      
      //println(mqProtSet.proteinSet.getRepresentativeProteinMatch().get.accession)
      //println(abundances.toList)
      
      val quantProfile = new MasterQuantProteinSetProfile(
        rawAbundances = rawAbundances,
        abundances = abundances, //.map(x => 0f),
        ratios = ratios.toList,
        mqPeptideIds = mqPeptideIds
      )
      
      mqProfilesByProtSet(mqProtSet) += quantProfile
    }
    
    // Update Master Quant protein sets properties
    for( (mqProtSet,mqProfiles) <- mqProfilesByProtSet ) {
      
      val mqProtSetProps = mqProtSet.properties.getOrElse( new MasterQuantProteinSetProperties() )      
      val mqProtSetProfileMap = mqProtSetProps.getMqProtSetProfilesByGroupSetupNumber.getOrElse( HashMap() )
      
      mqProtSetProfileMap += (groupSetupNumber -> mqProfiles.toArray)
      mqProtSetProps.setMqProtSetProfilesByGroupSetupNumber( Some(mqProtSetProfileMap) )
      
      mqProtSet.properties = Some(mqProtSetProps)
      //logger.debug("score:"+mqProtSet.proteinSet.peptideSet.score)
      
      val bestMQProtSetProfile = mqProtSet.getBestProfile(groupSetupNumber)
      if (bestMQProtSetProfile.isDefined) {
        //logger.debug("undefined abundance: "+bestMQProtSetProfile.get.abundances.count(ab =>isZeroOrNaN(ab)) )
        mqProtSet.setAbundancesForQuantChannels(bestMQProtSetProfile.get.abundances,qcIds)
      } else {
        // FOR TESTS ONLY //
        //logger.debug("empty abundances")
        
        // Set abundances to NaN
        for( qProtSet <- mqProtSet.quantProteinSetMap.values ) {
          qProtSet.abundance = Float.NaN
        }
        
        // Deselect master quant protein set
        /*if( mqProtSet.selectionLevel == 2 ) {
          mqProtSet.selectionLevel = 1
        }*/
      }
      
      // TODO: remove me Reset quant profiles for this masterQuantProtSet
      /*for( masterQuantProtSetProps <- mqProtSet.properties) {
        masterQuantProtSetProps.setMqProtSetProfilesByGroupSetupNumber(None)
      }*/
    }
    
    ()
  }
  
  protected def summarizeMatrix(abundanceMatrix: Array[Array[Float]], abSumMethod: AbundanceSummarizer.Method.Value ): Array[Float] = {
    if(abundanceMatrix.isEmpty) return Array()
    if(abundanceMatrix.length == 1) abundanceMatrix.head 
    
    import AbundanceSummarizer.Method._
    
    // Summarize raw abundances of the current profile cluster
    val finalAbSumMethod = abSumMethod

    // If the method is not median profile
    val summarizedAbundances = if (finalAbSumMethod != MEDIAN_BIOLOGICAL_PROFILE) {
      AbundanceSummarizer.summarizeAbundanceMatrix(
        abundanceMatrix,
        finalAbSumMethod
      )
    // Else if the method is median biological profile
    } else {
            
      // Group quant channels by biological samples 
      val bioSamplesGroupedMatrix = abundanceMatrix.map { abundanceRow =>
        
        val sampleAbundancesBySampleNum = abundanceRow.zip(qcSampleNumbers).groupBy(_._2)
        val sampleNumbers = qcSampleNumbers.distinct
        
        sampleNumbers.map { sampleNumber =>
          val sampleAbundancesTuples = sampleAbundancesBySampleNum(sampleNumber)
          sampleAbundancesTuples.map(_._1)
        }
      }
      
      //println("bioSamplesGroupedMatrix.length",bioSamplesGroupedMatrix.length)
      
      // Compute median abundances for each biological sample in each row of the matrix
      val samplesMedianAbMatrix = bioSamplesGroupedMatrix.withFilter { sampleAbundances =>
        // Internal filter: keep only rows having at least one biological sample with more than one defined value
        sampleAbundances.count( _.count( !isZeroOrNaN(_) ) >= 2 ) >= 1        
      } map { abundanceRow =>
        abundanceRow.map { sampleAbundances =>
          this._medianAbundance(sampleAbundances)
        }
      }
      
      // Check matrix is not empty after filtering
      if( samplesMedianAbMatrix.isEmpty ) {
        logger.warn("Can't biological median profile for abundance summarizing (not enough defined values)")
        return Array.fill(qcCount)(Float.NaN)
      }
      
      // Remove values in columns having a low number of defined values
      val filteredSamplesMedianAbMatrix = samplesMedianAbMatrix.transpose.map { column =>
        val nValues = column.length
        val nDefValues = column.count( !isZeroOrNaN(_) )
        val nonDefValues = column.length - nDefValues
        if( nDefValues < nonDefValues ) Array.fill(nValues)(Float.NaN)
        else column
      } transpose
      
      // Compute median profile of the samplesMedianAbMatrix
      // We will thus obtain a reference abundance value for each biological sample
      // Note: if there no intensity for a given sample, we will not generate values for this sample (missing values)
      val samplesMedianProfile = AbundanceSummarizer.summarizeAbundanceMatrix(
        filteredSamplesMedianAbMatrix,
        MEDIAN_PROFILE
      )
      //println("samplesMedianProfile", samplesMedianProfile.toList)
      
      // Transpose bioSamplesGroupedMatrix to have samples at the first level of the 3D matrix
      val samplesAbMatrices = bioSamplesGroupedMatrix.transpose
      
      // Create a matrix which will contain the sorted indices of abundances for each biological sample
      // Note: indices are sorted in descending abundance order
      val sampleSortedIndicesMatrix = new ArrayBuffer[Array[Int]](sampleCount)
      
      // Iterate over biological sample matrices to summarize them independently
      val samplesCvs = samplesAbMatrices.map { sampleAbMatrix =>
        
        // Compute TOP3 mean values
        val top3MeanSampleAb = AbundanceSummarizer.summarizeAbundanceMatrix(
          sampleAbMatrix,
          MEAN_OF_TOP3
        )
        
        // Compute ranks of TOP3 mean values
        //val sampleAbSortedIndices = new Array[Int](top3MeanSampleAb.length)
        val sampleAbSortedIndices = for( (ab,idxBeforeSort) <- top3MeanSampleAb.zipWithIndex.sortBy(- _._1) ) yield {
          idxBeforeSort
        }
        //println(sampleAbSortedIndices.mkString("\t"))
        sampleSortedIndicesMatrix += sampleAbSortedIndices
        
        /*println("before norm")
        for( r <- sampleAbMatrix ) {
          println(r.toList)
        }*/
        
        // Center the sample matrix to obtain comparable values
        //val normalizedSampleAbMatrix = AbundanceNormalizer.normalizeAbundances(sampleAbMatrix.transpose).transpose
        val centeredSampleAbMatrix = sampleAbMatrix.map { sampleAbundances =>
          // Skip samples having only a single defined value
          if( sampleAbundances.count(!isZeroOrNaN(_)) == 1 ) Array.fill(sampleAbundances.length)(Float.NaN)
          else {
            val sampleMean = this._meanAbundance(sampleAbundances)
            sampleAbundances.map( _ / sampleMean )
          }
        }
        
        /*println("after norm")
        for( r <- centeredSampleAbMatrix ) {
          println(r.toList)
        }*/
        
        // Flatten abundances observed in the different rows
        val flattenedDefSampleAbundances = centeredSampleAbMatrix.flatten.filter( isZeroOrNaN(_) == false )
        
        // Check we have enough abundances (at least 3)
        if( flattenedDefSampleAbundances.length > 2 ) {
          //println("flattenedDefSampleAbundances",flattenedDefSampleAbundances.mkString("\t"))
          val medianSampleAb = median(flattenedDefSampleAbundances)
          //println("medianSampleAb",medianSampleAb)
          val stdDev = this.standardDeviation(flattenedDefSampleAbundances.map(_.toDouble))
          //println("stdDev",stdDev)
          stdDev / medianSampleAb
        } else {
          Double.NaN
        }
      }
      
      val defSampleCvs = samplesCvs.filter( isZeroOrNaN(_) == false )
      val sampleCvMean = if (defSampleCvs.isEmpty) Double.NaN else defSampleCvs.sum / defSampleCvs.length
      if (isZeroOrNaN(sampleCvMean)) {
        logger.warn("Can't compute CVs for abundance summarizing")
        return Array.fill(qcCount)(Float.NaN)
      }
      
      // Generate abundance values using the computed gaussian model parameters (median and standard deviation values)
      val qcGeneratedValues = new ArrayBuffer[Float](qcCount)
      for( (sampleMedianAb,idx) <- samplesMedianProfile.zipWithIndex ) {
        val sampleAbSortedIndices = sampleSortedIndicesMatrix(idx)
        val sampleCv = samplesCvs(idx)
        val sampleQcCount = samplesQcCount(idx)

        val sampleCvOrMeanCv = if (!isZeroOrNaN(sampleCv)) sampleCv else sampleCvMean
        assert( isZeroOrNaN(sampleCvOrMeanCv) == false )
        
        val sampleGeneratedValues = if( isZeroOrNaN(sampleMedianAb) ) {
          Array.fill(sampleQcCount)(Float.NaN)
        } else {
          val sampleStdDev = sampleCvOrMeanCv * sampleMedianAb
          val generatedValues = _generatePositiveGaussianValues(sampleMedianAb.toDouble, sampleStdDev, 0.05f, sampleQcCount).map(_.toFloat)
          
          if( generatedValues == null ) {
            //println("sampleMedianAb",sampleMedianAb)
            //println("sampleStdDev",sampleStdDev)
          }
          
          //println("generatedValues",generatedValues.toList)
          
          // Sort generated abundances according to previously computed sorted indices
          generatedValues.sortBy( - _ ).zip(sampleAbSortedIndices).sortBy(_._2).map(_._1)
        }
        
        // TODO: how to be sure we are accumulating values in the right QC order ?
        qcGeneratedValues ++= sampleGeneratedValues
      }
      
      /*if( i >= 20 ){
        1 / 0
      }*/
      
      qcGeneratedValues.toArray
      
      /*
      // PREVIOUS IMPLEM AT GROUP LEVEL
      
      // Normalize the profiles
      val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrix.transpose).transpose
      
      // Do we need a scaling after normalization ???
      
      // Split matrix in biological replicates matrix
      val groupsMatrix = normalizedMatrix.map { normalizedRow =>
        val groupAbundancesByGroupNum = normalizedRow.zip(qcGroupNumbers).groupBy(_._2)
        
        groupSetup.biologicalGroups.map { bgGroup =>
          val groupAbundancesTuples = groupAbundancesByGroupNum(bgGroup.number)
          val groupAbundances = groupAbundancesTuples.map(_._1)
          this._medianAbundance(groupAbundances)
        }
      }
      
      val summarizedGroupsAbundances = AbundanceSummarizer.summarizeAbundanceMatrix(
        groupsMatrix,
        MEDIAN_PROFILE
      )
      
      // TODO: summarize also the normalizedMatrix
      */
      
    } // END OF if the method is median profile
    
    summarizedAbundances
  }
  
  // TODO: put in Scala commons ???
  private def _generatePositiveGaussianValues(
    expectedMean: Double,
    expectedStdDev: Double,
    stdDevTol: Float = 0.05f,
    nbValues: Int = 3,
    maxIterations: Int = 1000
  ): Array[Double] = {
    require( expectedMean.isNaN == false, "mean is NaN")
    require( isZeroOrNaN(expectedStdDev) == false, "expectedStdDev equals zero or is NaN")
    require( stdDevTol >= 0, "stdDevTol must be greater than zero")
    require( nbValues >= 3, "can't generate less than 3 values to obtain the expectedStdDev")
    require( maxIterations >= 0, "maxIterations must be greater than zero")
    
    val stdDevAbsTol = expectedStdDev * stdDevTol
    var values = new ArrayBuffer[Double](nbValues)
    
    // First pass to initialize the values
    for( i <- 0 until nbValues ) {
      values += math.abs( randomGaussian(expectedMean, expectedStdDev) )
    }
    
    val centerIdx = (nbValues / 2) - 1
    
    //println("expectedStdDev",expectedStdDev)
    
    var curStdDev = this.standardDeviation(values.toArray)
    
    val minValue = expectedMean - 3 * expectedStdDev
    val maxValue = expectedMean + 3 * expectedStdDev
    
    //println("optimizing curStdDev")
    var i = 0
    while( math.abs(curStdDev - expectedStdDev) > stdDevAbsTol ) {
      i += 1
      
      if( i == maxIterations ) {
        return null
      }
      //println("curStdDev",curStdDev)
      
      val sortedValues = values.sorted
      
      // If curStdDev is too high
      values = if( curStdDev > expectedStdDev ) {
        //println(s"curStdDev $curStdDev is too high")
        sortedValues.tail
      // Else curStdDev is too low
      } else {
        //println(s"curStdDev $curStdDev is too low ")
        sortedValues.remove(centerIdx)
        sortedValues
      }
      assert( values.length == nbValues - 1)
      
      // Generate a new value that must be between minValue and maxValue
      var isValueInAcceptableRange = false
      while( isValueInAcceptableRange == false ) {
        val value = math.abs( randomGaussian(expectedMean, expectedStdDev) )
        if( value > minValue && value < maxValue ) {
          values += value
          isValueInAcceptableRange = true
        }
      }
      
      curStdDev = this.standardDeviation(values.toArray)
    }
    
    // Center the generated values around the expectedMean
    val currentMean = values.sum / nbValues
    val centeredValues = values.map( _ * expectedMean / currentMean )
    
    centeredValues.toArray
  }
  
  private def coefficientOfVariation(values: Array[Double]): Double = {
    val mean = StatUtils.mean(values)
    Math.sqrt( StatUtils.variance(values, mean) ) / mean
  }
  
  private def standardDeviation(values: Array[Double]): Double = {
    Math.sqrt( StatUtils.variance(values) )
  }
  
  protected def computeRatios(
    ratioDef: RatioDefinition,
    normalizedMatrix: Array[Array[Float]],
    psmCountMatrix: Array[Array[Int]],
    config: ProfilizerStatConfig
  ): Tuple2[Array[Array[Float]],Seq[AverageAbundanceRatio]] = {
    
    logger.debug(s"computing ratios on a matrix containing ${normalizedMatrix.length} values...")

    // Retrieve some vars
    val numeratorSampleNumbers = sampleNumbersByGroupNumber(ratioDef.numeratorGroupNumber)
    val denominatorSampleNumbers = sampleNumbersByGroupNumber(ratioDef.denominatorGroupNumber)
    val allSampleNumbers = numeratorSampleNumbers ++ denominatorSampleNumbers
    require( numeratorSampleNumbers != null && numeratorSampleNumbers.isEmpty == false, "numeratorSampleNumbers must be defined" )
    require( denominatorSampleNumbers != null && denominatorSampleNumbers.isEmpty == false, "denominatorSampleNumbers must be defined" )
    
    // Map quant channel indices by the sample number
    val qcIndicesBySampleNum = ( allSampleNumbers ).map { sampleNum =>
      sampleNum -> quantChannelsBySampleNumber(sampleNum).map( qc => qcIdxById(qc.id) )
    } toMap
    
    def _getSamplesAbundances(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.flatMap( i => i.map( abundances(_) ) )
    }
    def _getSamplesMeanAbundance(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.map { i => this._meanAbundance( i.map( abundances(_) ) ) }
    }
    def _getSamplesMedianAbundance(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.map { i => this._medianAbundance( i.map( abundances(_) ) ) }
    }
    def _getSamplesPsmCounts(psmCounts: Array[Int], sampleNumbers: Array[Int]): Array[Int] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.flatMap( i => i.map( psmCounts(_) ) )
    }
    
    // --- Estimate the noise models ---
    val absoluteErrors = new ArrayBuffer[AbsoluteErrorObservation](normalizedMatrix.length)
    val relativeErrors = new ArrayBuffer[RelativeErrorObservation](normalizedMatrix.length)
    
    // Iterator over each row of the abundance matrix in order to compute some statistical models
    normalizedMatrix.foreach { normalizedRow =>

      // Compute statistics at technical replicate level if enough replicates (at least 3)
      if( minQCsCountPerSample > 2 ) {
        // Iterate over each sample in order to build the absolute error model using existing sample analysis replicates
        for( sampleNum <- allSampleNumbers ) {
          val qcIndices = qcIndicesBySampleNum(sampleNum)
          require( qcIndices != null && qcIndices.length > 2, "qcIndices must be defined" )
          
          val sampleAbundances = qcIndices.map( normalizedRow(_) ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
          //println(sampleAbundances)
          
          // Check we have enough abundances (at least 3)
          if( sampleAbundances.length > 2 ) {
            val sampleStatSummary = CommonsStatHelper.calcExtendedStatSummary(sampleAbundances)
            val abundance = sampleStatSummary.getMedian.toFloat
            
            if( isZeroOrNaN(abundance) == false )
              absoluteErrors += AbsoluteErrorObservation( abundance, sampleStatSummary.getStandardDeviation.toFloat )
          }
        }
      // Compute statistics at biological sample level
      } else {
        
        val numeratorMedianAbundance = _medianAbundance( _getSamplesMedianAbundance( normalizedRow, numeratorSampleNumbers ) )
        val denominatorMedianAbundance = _medianAbundance( _getSamplesMedianAbundance( normalizedRow, denominatorSampleNumbers ) )
        
        val maxAbundance = math.max(numeratorMedianAbundance,denominatorMedianAbundance)
        
        if( maxAbundance.isNaN == false && maxAbundance > 0 ) {
          relativeErrors += RelativeErrorObservation( maxAbundance, numeratorMedianAbundance/denominatorMedianAbundance)
        }
      }
    }
    
    // Estimate the absolute noise model using technical replicates
    val absoluteNoiseModel = if( absoluteErrors.isEmpty == false ) {
      ErrorModelComputer.computeAbsoluteErrorModel(absoluteErrors,nbins=Some(errorModelBinsCount))    
    // Estimate the relative noise model using sample replicates
    } else if( relativeErrors.isEmpty == false ) {
    
      this.logger.warn("Insufficient number of analysis replicates => try to estimate absolute noise model using relative observations")
      //this.logger.debug("relativeErrors:" + relativeErrors.length + ", filtered zero :" + relativeErrors.filter(_.abundance > 0).length)
      ErrorModelComputer.computeRelativeErrorModel(relativeErrors, nbins=Some(errorModelBinsCount)).toAbsoluteErrorModel
    
    // Create a fake Error Model
    } else {
      // TODO: compute the stdDev from relativeErrors ?
      this.logger.warn("Insufficient number of relative errors => create an error model corresponding to the normal distribution")
      new AbsoluteErrorModel( Seq(AbsoluteErrorBin( abundance = 0f, stdDev = 1f )) )
    }
    
    logger.debug("config.applyMissValInference value: "+ config.applyMissValInference)
    
    // --- Infer missing abundances ---
    val filledMatrix = if( config.applyMissValInference == false ) normalizedMatrix
    else {
      
      // Instantiates an abundance inferer
      val inferConfig = config.missValInferenceConfig.get
      val abundanceInferer = MissingAbundancesInferenceMethod.withName( config.missValInferenceMethod ) match {
        case MissingAbundancesInferenceMethod.GAUSSIAN_MODEL => {
          logger.info("Inferring missing values using the gaussian model method...")
          new SmartMissingAbundancesInferer(inferConfig, absoluteNoiseModel)
        }
        case MissingAbundancesInferenceMethod.PERCENTILE => {
          logger.info("Inferring missing values using the percentile method...")
          new FixedNoiseMissingAbundancesReplacer(inferConfig)
        }
      }
      
      if( minQCsCountPerSample < 3 ) {
        // TODO: find what to do if when insufficient technical replicates
        this.logger.warn("insufficient number of analysis replicates => can't infer missing values")
        normalizedMatrix
      } else {
        
        // Extract abundance matrices for each biological sample
        val abMatrixBySampleNum = allSampleNumbers.map( _ -> new ArrayBuffer[Array[Float]] ).toMap      
        normalizedMatrix.foreach { normalizedRow =>
          allSampleNumbers.foreach { sampleNum =>
            abMatrixBySampleNum(sampleNum) += qcIndicesBySampleNum(sampleNum).map(normalizedRow(_))
          }
        }
        
        // Extract PSM counts for each biological sample
        val psmCountMatrixBySampleNum = allSampleNumbers.map( _ -> new ArrayBuffer[Array[Int]] ).toMap      
        psmCountMatrix.foreach { psmCountRow =>
          allSampleNumbers.foreach { sampleNum =>
            psmCountMatrixBySampleNum(sampleNum) += qcIndicesBySampleNum(sampleNum).map(psmCountRow(_))
          }
        }
        
        val tmpFilledMatrix = Array.ofDim[Float](normalizedMatrix.length,quantChannels.length)
        
        for( (sampleNum,sampleAbMatrix) <- abMatrixBySampleNum ) {
          
          val qcIndices = qcIndicesBySampleNum(sampleNum)
          val samplePsmCountMatrix = psmCountMatrixBySampleNum(sampleNum).toArray
          
          val inferredSampleMatrix = abundanceInferer.inferAbundances(sampleAbMatrix.toArray, samplePsmCountMatrix)
          
          var filledMatrixRow = 0
          inferredSampleMatrix.foreach { inferredAbundances =>
            
            inferredAbundances.zip(qcIndices).foreach { case (abundance,colIdx) =>
              tmpFilledMatrix(filledMatrixRow)(colIdx) = abundance
            }
            
            filledMatrixRow += 1
          }
        }
        
        tmpFilledMatrix
      }
    }
    
    // --- Determine the significant abundance changes ---
    
    // Create a new buffer for ratios to be computed
    val ratiosBuffer = new ArrayBuffer[AverageAbundanceRatio](filledMatrix.length)
    
    // Compute the error models
    val absoluteVariationsBuffer = new ArrayBuffer[AbsoluteErrorObservation](filledMatrix.length) // for n sample replicates
    val relativeVariationsBuffer = new ArrayBuffer[RelativeErrorObservation](filledMatrix.length) // for 1 sample replicate
    
    var rowIdx = 0
    filledMatrix.foreach { filledRow =>
      
      var numeratorSummary: ExtendedStatisticalSummary = null
      var denominatorSummary: ExtendedStatisticalSummary = null
      
      // Check if we have enough biological sample replicates to compute statistics at this level
      if( minSamplesCountPerGroup > 2 ) {
      
        // Retrieve the entity ID
        //val masterQuantPepId = masterQuantPeptides(rowIdx).id
        
        // Compute numerator and denominator abundances
        val numeratorMedianAbundances = _getSamplesMedianAbundance( filledRow, numeratorSampleNumbers )
        val denominatorMedianAbundances = _getSamplesMedianAbundance( filledRow, denominatorSampleNumbers )
        
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcExtendedStatSummary(numeratorMedianAbundances.map(_.toDouble))
        denominatorSummary = CommonsStatHelper.calcExtendedStatSummary(denominatorMedianAbundances.map(_.toDouble))        
     
        // We can then make absolute statistical validation at the biological sample level
        val (numerator, numStdDev) = ( numeratorSummary.getMedian().toFloat, numeratorSummary.getStandardDeviation.toFloat )
        val (denom, denomStdDev) = ( denominatorSummary.getMedian().toFloat, denominatorSummary.getStandardDeviation.toFloat )
        
        if (numerator.isNaN == false &&  numStdDev.isNaN == false && denom.isNaN == false && denomStdDev.isNaN == false) {
          absoluteVariationsBuffer += AbsoluteErrorObservation( numerator, numStdDev )
          absoluteVariationsBuffer += AbsoluteErrorObservation( denom, denomStdDev )
        } else {
          this.logger.trace("Stat summary contains NaN mean/median or NaN standardDeviation")
        }
        
      }
      // Else we merge biological sample data and compute statistics at a lower level
      else {
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcExtendedStatSummary(
          _getSamplesAbundances( filledRow, numeratorSampleNumbers ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
        )
        denominatorSummary = CommonsStatHelper.calcExtendedStatSummary(
          _getSamplesAbundances( filledRow, denominatorSampleNumbers ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
        )
      }
      
      // Retrieve PSM counts
      val psmCountRow = psmCountMatrix(rowIdx)
      val numeratorPsmCounts = _getSamplesPsmCounts(psmCountRow, numeratorSampleNumbers)
      val denominatorPsmCounts = _getSamplesPsmCounts(psmCountRow, denominatorSampleNumbers)
      
      // Compute the ratio for this row
      val ratio = new AverageAbundanceRatio( rowIdx, numeratorSummary, denominatorSummary, numeratorPsmCounts, denominatorPsmCounts )
      ratiosBuffer += ratio
      
      // Update the relative error model
      if( ratio.ratioValue.isDefined && ratio.ratioValue.get > 0 ) {
        relativeVariationsBuffer += RelativeErrorObservation( ratio.maxAbundance.toFloat, ratio.ratioValue.get )
      }

      rowIdx += 1
    }
    
    if( relativeVariationsBuffer.length < 5 ) {
      logger.warn("Insufficient number of ratios to compute their significativity !")
    } else {
      val relativeVariationModel = ErrorModelComputer.computeRelativeErrorModel(relativeVariationsBuffer,nbins=Some(errorModelBinsCount))
      
      // Retrieve the right tuple of error models
      val absoluteErrorModelOpt = if( minSamplesCountPerGroup > 2 ) {
        Some( ErrorModelComputer.computeAbsoluteErrorModel(absoluteVariationsBuffer,nbins=Some(errorModelBinsCount)) )
      } else if( minQCsCountPerSample > 2 ) {
        Some(absoluteNoiseModel)
      } else None
    
      // Update the variation state of ratios
      AbundanceRatiolizer.updateRatioStates(
        ratiosBuffer,
        relativeVariationModel,
        absoluteErrorModelOpt,
        config
      )
      
    }
    
    (filledMatrix,ratiosBuffer)
  }
  
  private def _meanAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.isEmpty ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _medianAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.isEmpty ) Float.NaN else median(defAbundances)
  }

}
