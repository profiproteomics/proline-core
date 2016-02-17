package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.math.median
import fr.profi.util.primitives.isZeroOrNaN
import fr.proline.core.algo.msq.profilizer._
import fr.proline.core.om.model.msq._

// TODO: recompute raw abundances from peakels
// (smoothing methods, area VS apex intensity, first isotope vs max one vs isotope pattern fitting)

case class ProfilizerStatConfig(
  // TODO: replace Some(MissingAbundancesInferenceConfig) by None when IHMs haven been updated
  missValInferenceConfig: Option[MissingAbundancesInferenceConfig] = Some(MissingAbundancesInferenceConfig()),
  statTestsAlpha: Float = 0.01f,
  minZScore: Float = 0.4f, // ZScore equals ln(ratio) followed by standardisation
  minPsmCountPerRatio: Int = 0,
  applyNormalization: Boolean = true,
  applyMissValInference: Boolean = true, // TODO: remove me when IHMs haven been updated
  applyVarianceCorrection: Boolean = true,
  applyTTest: Boolean = true,
  applyZTest: Boolean = true
) {
   // TODO: remove me when IHMs haven been updated
  if( applyMissValInference ) {
    require( missValInferenceConfig.isDefined, "can't apply missing value inference if the method is not defined" )
  }
}

case class ProfilizerConfig(
  discardMissedCleavedPeptides: Boolean = true,
  discardOxidizedPeptides: Boolean = true,
  discardLowIdentPeptides: Boolean = false,
  useOnlySpecificPeptides: Boolean = true,
  applyProfileClustering: Boolean = true,
  abundanceSummarizerMethod: String = AbundanceSummarizer.Method.MEAN,
  peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
  proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig()
)

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
  private val qcIdxById = qcIds.zip( qcIds.indices ).toMap
  private val quantChannelsBySampleNumber = quantChannels.groupBy( _.sampleNumber )
  
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
      
      // Reset quant profiles for this masterQuantPeptide
      for( mqPepProps <- mqPep.properties ) {
        mqPepProps.setMqPepProfileByGroupSetupNumber(None)
      }
    }
    
    // --- Apply protein set specific filter if requested ---
    if( config.useOnlySpecificPeptides ) {
      this.discardUnspecificPeptides( masterQuantPeptides )
    }
    
    // --- Apply MC filter if requested ---
    if( config.discardMissedCleavedPeptides ) {
      println("discardMissedCleavedPeptides")
      this.discardMissedCleavedPeptides(masterQuantPeptides)
    }
    
    // --- Apply Oxidation filter if requested ---
    if( config.discardOxidizedPeptides ) {
      this.discardOxidizedPeptides(masterQuantPeptides)
    }
    
    // Keep master quant peptides passing all filters (i.e. have a selection level higher than 1)
    val( mqPepsAfterAllFilters, deselectedMqPeps ) = masterQuantPeptides.partition( _.selectionLevel >= 2 )
    
    // Reset quant peptide abundance of deselected master quant peptides
    for( mqPep <- deselectedMqPeps; (qcid,qPep) <- mqPep.quantPeptideMap ) {
      qPep.abundance = Float.NaN
    }
    
    // --- Compute the PSM count matrix ---
    val psmCountMatrix = mqPepsAfterAllFilters.map( _.getPepMatchesCountsForQuantChannels(qcIds) ).toArray
    
    // --- Compute the abundance matrix ---
    val abundanceMatrix = mqPepsAfterAllFilters.map( _.getRawAbundancesForQuantChannels(qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( config.peptideStatConfig.applyNormalization == false ) abundanceMatrix
    else AbundanceNormalizer.normalizeAbundances(abundanceMatrix)
    
    require( normalizedMatrix.length >= 10, "error during normalization, some peptides were lost...")
    //println( s"normalizedMatrix.length: ${normalizedMatrix.length}")
    
    // --- Estimate the noise model ---
    
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
          state = ratio.state.map(_.id).getOrElse(AbundanceRatioState.Invariant.id),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue,
          zScore = ratio.zScore
        )
        
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
  
  protected def discardUnspecificPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ) {
    for(
      mqPep <- masterQuantPeptides;
      if mqPep.selectionLevel >= 2;
      pepInst <- mqPep.peptideInstance
    ) {
      if( pepInst.isValidProteinSetSpecific== false )
        mqPep.selectionLevel = 1
    }
  }
  
  protected def discardMissedCleavedPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ) {
    
    // FIXME: we assume here that Trypsin has been used => retrieve the right enzyme to apply this filter correctly
    val regex = ".*?[R|K]".r
    val detectedMCSeqParts = new ArrayBuffer[String]()
    
    for( 
      mqPep <- masterQuantPeptides;
      if mqPep.selectionLevel >= 2;
      pepInst <- mqPep.peptideInstance
    ) {
      
      val pepSeq = pepInst.peptide.sequence
      val seqParts = regex.findAllIn(pepSeq).toArray
      
      if( seqParts.length > 1 ) {
        
        // Append detected missed cleaved sequences in the buffer
        detectedMCSeqParts ++= seqParts
        
        // Discard detected missed cleaved peptide
        mqPep.selectionLevel = 1
      }
    }
    
    // Convert the detectedSeqsWithMC buffer into a Set
    val detectedMCSeqSet = detectedMCSeqParts.toSet
    
    // Filter master quant peptides again to remove the counterpart of the MC ones
    this._discardPeptideSequences( masterQuantPeptides, detectedMCSeqSet )
    
  }
  
  protected def discardOxidizedPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ) {

    val pattern = """\[O\]""".r.pattern
    val detectedOxSeqs = new ArrayBuffer[String]()
    
    for(
      mqPep <- masterQuantPeptides;
      if mqPep.selectionLevel >= 2;
      pepInst <- mqPep.peptideInstance
    ) {
      
      val peptide = pepInst.peptide
      val ptmString = peptide.ptmString
      
      // Check if the ptmString contains an oxidation
      // TODO: check if this technique is error prone
      if( pattern.matcher(ptmString).matches ) {
        
        // Append detected oxidized sequence in the buffer
        detectedOxSeqs += peptide.sequence
        
        // Discard detected oxidized peptide
        mqPep.selectionLevel = 1
      }
    }
    
    // Convert the detectedSeqsWithMC buffer into a Set
    val detectedOxSeqSet = detectedOxSeqs.toSet
    
    // Filter kept master quant peptides again to remove the counterpart of the oxidized ones
    this._discardPeptideSequences( masterQuantPeptides, detectedOxSeqSet )
  }
  
  private def _discardPeptideSequences( masterQuantPeptides: Seq[MasterQuantPeptide], discardedPepSeqSet: Set[String] ) {
    
    for(
      mqPep <- masterQuantPeptides;
      if mqPep.selectionLevel >= 2;
      pepInst <- mqPep.peptideInstance
    ) {
      if( discardedPepSeqSet.contains(pepInst.peptide.sequence) ) {
        mqPep.selectionLevel = 1
      }
    }
    
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
    val clusteredMqPeps = if(config.discardLowIdentPeptides) {
      
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
        val selectedMqPepIds =mqProtSet.masterQuantPeptides.withFilter( _.selectionLevel >= 2 ).map(_.id)
        mqProtSet.properties.get.selectedMasterQuantPeptideIds = Some(selectedMqPepIds)
      }
    }

    // Clusterize MasterQuantPeptides according to the provided method
    val mqPepClusterer = if( config.applyProfileClustering ) {
      new QuantProfileBasedClusterer( groupSetupNumber ) 
    } else {
      new PeptideSetBasedClusterer() 
    }
    
    val mqPepsClusters = mqPepClusterer.computeMqPeptidesClusters(masterQuantProtSets)
    
    // --- Iterate over MQ peptides clusters to summarize corresponding abundance matrix ---
    val rawAbundanceMatrixBuffer = new ArrayBuffer[Array[Float]](masterQuantProtSets.length)
    val rawAbundancesByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Float]]
    rawAbundancesByProfileClusterBuilder.sizeHint(rawAbundanceMatrixBuffer.length)
    val abundanceMatrixBuffer = new ArrayBuffer[Array[Float]](masterQuantProtSets.length)
    val psmCountMatrixBuffer = new ArrayBuffer[Array[Int]](masterQuantProtSets.length)
    
    import AbundanceSummarizer.Method._
    
    val abSumMethod = AbundanceSummarizer.Method.withName(config.abundanceSummarizerMethod)
    
    for( mqPepsCluster <- mqPepsClusters ) {
      
      val clusteredMqPeps = mqPepsCluster.mqPeptides
      
      // Summarize raw abundances of the current profile cluster
      val mqPepRawAbundanceMatrix = clusteredMqPeps.map( _.getRawAbundancesForQuantChannels(qcIds) ).toArray
      val rawAbMatrixSumMethod = if(abSumMethod != MEDIAN_PROFILE || mqPepRawAbundanceMatrix.length >= 3) {
        abSumMethod
      } else {
        SUM
      }
      
      val summarizedRawAbundances = AbundanceSummarizer.summarizeAbundanceMatrix(
        mqPepRawAbundanceMatrix,
        rawAbMatrixSumMethod
      )
      rawAbundanceMatrixBuffer += summarizedRawAbundances
      rawAbundancesByProfileClusterBuilder += mqPepsCluster -> summarizedRawAbundances
      
      // Summarize abundances of the current profile cluster
      val mqPepAbundanceMatrix = clusteredMqPeps.map( _.getAbundancesForQuantChannels(qcIds) ).toArray
      val abMatrixSumMethod = if(abSumMethod != MEDIAN_PROFILE || mqPepRawAbundanceMatrix.length >= 3) {
        abSumMethod
      } else {
        SUM
      }
      
      abundanceMatrixBuffer += AbundanceSummarizer.summarizeAbundanceMatrix(
        mqPepAbundanceMatrix,
        abMatrixSumMethod
      )
      
      // Summarize PSM counts of the current profile cluster
      val psmCountMatrix = clusteredMqPeps.map( _.getPepMatchesCountsForQuantChannels(qcIds) ).toArray
      psmCountMatrixBuffer += psmCountMatrix.transpose.map( _.sum )
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
      
      for ( ratio <- ratios ) {

        val computedRatio = ComputedRatio(
          numerator = ratio.numerator.toFloat,
          denominator = ratio.denominator.toFloat,
          state = ratio.state.map(_.id).getOrElse(0),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue,
          zScore = ratio.zScore
        )
        
        val rowIndex = ratio.entityId.toInt
        val mqPepCluster = mqPepsClusters(rowIndex)
        
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
      val abundanceInferer = MissingAbundancesInferenceMethod.withName( inferConfig.inferenceMethod ) match {
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
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _medianAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else median(defAbundances)
  }

}
