package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.typesafe.scalalogging.slf4j.Logging
import org.apache.commons.math3.stat.descriptive.moment.Mean
import org.apache.commons.math3.stat.descriptive.StatisticalSummary
import fr.proline.core.om.model.msq._
import fr.profi.util.primitives.isZeroOrNaN

// TODO: remove me when the WS v0.1 has been removed
case class ProfilizerConfigV0(
  peptideStatTestsAlpha: Float = 0.01f,
  proteinStatTestsAlpha: Float = 0.01f,
  discardMissedCleavedPeptides: Boolean = true,
  discardOxidizedPeptides: Boolean = true,
  applyNormalization: Boolean = true,
  applyMissValInference: Boolean = true,
  applyVarianceCorrection: Boolean = true,
  applyTTest: Boolean = true,
  applyZTest: Boolean = true,
  applyProfileClustering: Boolean = true,
  useOnlySpecificPeptides: Boolean = true
)

case class ProfilizerStatConfig (
  statTestsAlpha: Float = 0.01f,
  applyNormalization: Boolean = true,
  applyMissValInference: Boolean = true,
  applyVarianceCorrection: Boolean = true,
  applyTTest: Boolean = true,
  applyZTest: Boolean = true  
)

case class ProfilizerConfig(   
  discardMissedCleavedPeptides: Boolean = true,
  discardOxidizedPeptides: Boolean = true,
  useOnlySpecificPeptides: Boolean = true,
  applyProfileClustering: Boolean = true,
  abundanceSummarizerMethod : String = AbundanceSummarizer.Method.MEAN.toString(),
  peptideStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig(),
  proteinStatConfig: ProfilizerStatConfig = new ProfilizerStatConfig()
)

/**
 * Analyze profiles of Master Quant Peptides and Master Quant Protein sets
 */
class Profilizer( expDesign: ExperimentalDesign, groupSetupNumber: Int = 1, masterQCNumber: Int = 1 ) extends Logging {
  
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
      if( mqPep.selectionLevel >= 1 )
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
        masterQuantPep.setAbundancesForQuantChannels(abundances,qcIds)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numeratorMean.toFloat,
          denominator = ratio.denominatorMean.toFloat,
          state = ratio.state.map(_.id).getOrElse(new Integer(AbundanceRatioState.Invariant.toString())),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue
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
    
    case class MQPepProfilesCluster(
      mqProtSet: MasterQuantProteinSet,
      mqPeps: Seq[MasterQuantPeptide]
      //abundanceMatrixIndex: Int
    )
    
    // --- Clusterize MQ peptides profiles and compute corresponding abundance matrix ---
    val profilesClusters = new ArrayBuffer[MQPepProfilesCluster](masterQuantProtSets.length)
    val abundanceMatrixBuffer = new ArrayBuffer[Array[Float]](masterQuantProtSets.length)
    val psmCountMatrixBuffer = new ArrayBuffer[Array[Int]](masterQuantProtSets.length)
    
    for( masterQuantProtSet <- masterQuantProtSets ) {
      
      // Reset quant profiles for this masterQuantProtSet
      for( masterQuantProtSetProps <- masterQuantProtSet.properties) {
        masterQuantProtSetProps.setMqProtSetProfilesByGroupSetupNumber(None)
      }
      
      // Clusterize MasterQuantPeptides according to their profile
      val mqPepsByProfileAsStr = new HashMap[String,ArrayBuffer[MasterQuantPeptide]]()
      
      masterQuantProtSet.masterQuantPeptides.foreach { mqPep =>
        
        if( mqPep.selectionLevel >= 2 ) {
          val mqPepProfileOpt = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get.get( groupSetupNumber )
          
          for( mqPepProfile <- mqPepProfileOpt ) {
            
            val profileAsStr = if( config.applyProfileClustering == false ) masterQuantProtSet.id.toString
            else {
              val profileSlopes = mqPepProfile.ratios.map( _.map( _.state ).get )
              _stringifyProfile( profileSlopes )
            }
            
            mqPepsByProfileAsStr.getOrElseUpdate(profileAsStr, new ArrayBuffer[MasterQuantPeptide] ) += mqPep
          }
        }
      }
      
      // Build master quant protein set profiles
      for( (profileAsStr,mqPeps) <- mqPepsByProfileAsStr ) {
        
        profilesClusters += MQPepProfilesCluster(
          mqProtSet = masterQuantProtSet,
          mqPeps = mqPeps
        )
        
        // Summarize abundances of the current profile cluster
        // TODO: put the method in the config
        val abundanceMatrix = mqPeps.map( _.getAbundancesForQuantChannels(qcIds) ).toArray
        abundanceMatrixBuffer += AbundanceSummarizer.summarizeAbundanceMatrix(
          abundanceMatrix,
          AbundanceSummarizer.Method.withName(config.abundanceSummarizerMethod)
        )
        
        // Summarize PSM counts of the current profile cluster
        val psmCountMatrix = mqPeps.map( _.getPepMatchesCountsForQuantChannels(qcIds) ).toArray
        psmCountMatrixBuffer += psmCountMatrix.transpose.map( _.sum )
      }
    }
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( config.proteinStatConfig.applyNormalization == false ) abundanceMatrixBuffer.toArray
    else AbundanceNormalizer.normalizeAbundances(abundanceMatrixBuffer.toArray)
    
    // --- Map normalized abundances by the corresponding profile cluster ---
    val abundancesByProfileClusterBuilder = Map.newBuilder[MQPepProfilesCluster,Array[Float]]
    abundancesByProfileClusterBuilder.sizeHint(normalizedMatrix.length)
    
    normalizedMatrix.indices.foreach { rowIndex =>
      val profileCluster = profilesClusters(rowIndex)
      abundancesByProfileClusterBuilder += profileCluster -> normalizedMatrix(rowIndex)
    }
    val abundancesByProfileCluster = abundancesByProfileClusterBuilder.result
    
    // --- Compute the ratios corresponding to each profile cluster ---
    
    // Create a map which will store the ratios corresponding to each profile cluster
    val ratiosByProfileCluster = Map() ++ profilesClusters.map( _ -> new ArrayBuffer[Option[ComputedRatio]] )
    
    // Iterate over the ratio definitions
    for ( ratioDef <- groupSetup.ratioDefinitions ) {
      
      val( filledMatrix, ratios ) = computeRatios(
        ratioDef,
        normalizedMatrix,
        psmCountMatrixBuffer.toArray,
        config.proteinStatConfig
      )
 
      for ( ratio <- ratios ) {

        val computedRatio = ComputedRatio(
          numerator = ratio.numeratorMean.toFloat,
          denominator = ratio.denominatorMean.toFloat,
          state = ratio.state.map(_.id).getOrElse(0),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue
        )
        
        val rowIndex = ratio.entityId.toInt
        val profileCluster = profilesClusters(rowIndex)
        
        ratiosByProfileCluster( profileCluster ) += Some(computedRatio)
      }
    
    }
    
    // Update the profiles using the obtained results
    val mqProfilesByProtSet = Map() ++ masterQuantProtSets.map( _ -> new ArrayBuffer[MasterQuantProteinSetProfile] )
    
    // Group profiles by Master Quant protein set
    for ( (profileCluster,ratios) <- ratiosByProfileCluster ) {

      val mqProtSet = profileCluster.mqProtSet
      val mqPeptideIds = profileCluster.mqPeps.map(_.id).toArray
      val abundances = abundancesByProfileCluster(profileCluster)
      
      val quantProfile = new MasterQuantProteinSetProfile(
        abundances = abundances,
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
      
      val bestMQProtSetProfile = mqProtSet.getBestProfile(groupSetupNumber)
      if(bestMQProtSetProfile.isDefined){
    	  mqProtSet.setAbundancesForQuantChannels(bestMQProtSetProfile.get.abundances,qcIds)
      }
      
    }
    
    ()
  }
  
  protected def computeRatios(
    ratioDef: RatioDefinition,
    normalizedMatrix: Array[Array[Float]],
    psmCountMatrix: Array[Array[Int]],
    config: ProfilizerStatConfig
  ): Tuple2[Array[Array[Float]],Seq[AverageAbundanceRatio]] = {

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
            val sampleStatSummary = CommonsStatHelper.calcStatSummary(sampleAbundances)
            val abundance = sampleStatSummary.getMean.toFloat
            
            if( isZeroOrNaN(abundance) == false )
              absoluteErrors += AbsoluteErrorObservation( abundance, sampleStatSummary.getStandardDeviation.toFloat )
          }
        }
      // Compute statistics at biological sample level
      } else {
        
        val numeratorMeanAbundance = _meanAbundance( _getSamplesMeanAbundance( normalizedRow, numeratorSampleNumbers ) )
        val denominatorMeanAbundance = _meanAbundance( _getSamplesMeanAbundance( normalizedRow, denominatorSampleNumbers ) )
        
        val maxAbundance = math.max(numeratorMeanAbundance,denominatorMeanAbundance)
        
        if( maxAbundance.isNaN == false && maxAbundance > 0 ) {
          relativeErrors += RelativeErrorObservation( maxAbundance, numeratorMeanAbundance/denominatorMeanAbundance)
        }
      }
    }
    
    // Estimate the absolute noise model using technical replicates
    val absoluteNoiseModel = if( minQCsCountPerSample > 2 ) {
      ErrorModelComputer.computeAbsoluteErrorModel(absoluteErrors,nbins=Some(5))
    } else {
    // Estimate the relative noise model using sample replicates
      this.logger.warn("insufficient number of analysis replicates => try to estimate absolute noise model using relative observations")
      //this.logger.debug("relativeErrors:" + relativeErrors.length + ", filtered zero :" + relativeErrors.filter(_.abundance > 0).length)
      ErrorModelComputer.computeRelativeErrorModel(relativeErrors, nbins=Some(5)).toAbsoluteErrorModel
    }
    
    logger.debug("config.applyMissValInference value: "+ config.applyMissValInference)
    
    // --- Infer missing abundances ---
    val filledMatrix = if( config.applyMissValInference == false ) normalizedMatrix
    else {
      logger.info("Inferring missing values...")
      
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
          
          val inferredSampleMatrix = MissingAbundancesInferer.inferAbundances(
            sampleAbMatrix.toArray, samplePsmCountMatrix, absoluteNoiseModel
          )
          
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
      
      var numeratorSummary: StatisticalSummary = null
      var denominatorSummary: StatisticalSummary = null
      
      // Check if we have enough biological sample replicates to compute statistics at this level
      if( minSamplesCountPerGroup > 2 ) {
      
        // Retrieve the entity ID
        //val masterQuantPepId = masterQuantPeptides(rowIdx).id
        
        // Compute numerator and denominator abundances
        val numeratorMeanAbundances = _getSamplesMeanAbundance( filledRow, numeratorSampleNumbers )
        val denominatorMeanAbundances = _getSamplesMeanAbundance( filledRow, denominatorSampleNumbers )
        
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcStatSummary(numeratorMeanAbundances.map(_.toDouble))
        denominatorSummary = CommonsStatHelper.calcStatSummary(denominatorMeanAbundances.map(_.toDouble))        
     
        // We can then make absolute statistical validation at the biological sample level
        val (numMean, numStdDev) = ( numeratorSummary.getMean.toFloat, numeratorSummary.getStandardDeviation.toFloat )
        val (denomMean, denomStdDev) = ( denominatorSummary.getMean.toFloat, denominatorSummary.getStandardDeviation.toFloat )
        
        if (numMean.isNaN == false &&  numStdDev.isNaN == false && denomMean.isNaN == false && denomStdDev.isNaN == false) {
          absoluteVariationsBuffer += AbsoluteErrorObservation( numMean, numStdDev )
          absoluteVariationsBuffer += AbsoluteErrorObservation( denomMean, denomStdDev )
        } else {
          this.logger.trace("Stat summary contains NaN mean or NaN standardDeviation")
        }
        
      }
      // Else we merge biological sample data and compute statistics at a lower level
      else {        
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcStatSummary(
          _getSamplesAbundances( filledRow, numeratorSampleNumbers ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
        )
        denominatorSummary = CommonsStatHelper.calcStatSummary(
          _getSamplesAbundances( filledRow, denominatorSampleNumbers ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
        )
      }
      
      // Compute the ratio for this row
      val ratio = new AverageAbundanceRatio( rowIdx, numeratorSummary, denominatorSummary )
      ratiosBuffer += ratio
      
      // Update the relative error model
      if( ratio.ratioValue.isDefined && ratio.ratioValue.get > 0 ) {
        relativeVariationsBuffer += RelativeErrorObservation( ratio.maxAbundance.toFloat, ratio.ratioValue.get )
      }

      rowIdx += 1
    }
    
    val relativeVariationModel = ErrorModelComputer.computeRelativeErrorModel(relativeVariationsBuffer,nbins=Some(5))
    
    // Retrieve the right tuple of error models
    val errorModels = if( minSamplesCountPerGroup > 2 ) {
      (relativeVariationModel, Some( ErrorModelComputer.computeAbsoluteErrorModel(absoluteVariationsBuffer,nbins=Some(5)) ) )
    } else if( minQCsCountPerSample > 2 ) {
      (relativeVariationModel, Some(absoluteNoiseModel) )
    } else (relativeVariationModel, None)
  
    // Update the variation state of ratios
    AbundanceRatiolizer.updateRatioStates(
      ratiosBuffer,
      errorModels._1,
      errorModels._2,
      config.statTestsAlpha,
      applyVarianceCorrection = config.applyVarianceCorrection,
      applyTTest = config.applyTTest,
      applyZTest = config.applyZTest
    )
    
    (filledMatrix,ratiosBuffer)
  }
  
  private def _meanAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _stringifyProfile( slopes: Seq[Int] ): String = { slopes.mkString(";") }

}

import fr.profi.util.math.median

// TODO: put in its own file
object AbundanceSummarizer {
  
  object Method extends Enumeration {
    val MEAN = Value("MEAN")
    val MEAN_OF_TOP3 = Value("MEAN_OF_TOP3")
    val MEDIAN = Value("MEDIAN")
    val MEDIAN_PROFILE = Value("MEDIAN_PROFILE")
    val NORMALIZED_MEDIAN_PROFILE = Value("NORMALIZED_MEDIAN_PROFILE")
    val SUM = Value("SUM")
  }
  
  def summarizeAbundanceMatrix( abundanceMatrix: Array[Array[Float]], method: Method.Value ): Array[Float] = {
    
    method match {
      case Method.MEAN => summarizeUsingMean(abundanceMatrix)
      case Method.MEAN_OF_TOP3 => summarizeUsingSum(abundanceMatrix)
      case Method.MEDIAN => summarizeUsingMedian(abundanceMatrix)
      case Method.MEDIAN_PROFILE => summarizeUsingMedianProfile(abundanceMatrix)
      case Method.NORMALIZED_MEDIAN_PROFILE => summarizeUsingNormalizedMedianProfile(abundanceMatrix)
      case Method.SUM => summarizeUsingSum(abundanceMatrix)
    }
    
  }
  
  def summarizeUsingMean( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    require( abundanceMatrix.length > 0, "abundanceMatrix is empty" )
    if( abundanceMatrix.length == 1 ) return abundanceMatrix.head
    
    abundanceMatrix.transpose.map( _calcMeanAbundance( _ ) )
  }
  
  def summarizeUsingMeanOfTop3( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    require( abundanceMatrix.length > 0, "abundanceMatrix is empty" )
    if( abundanceMatrix.length == 1 ) return abundanceMatrix.head
    
    // Sort rows by descending median abundance
    val sortedMatrix = abundanceMatrix.sortWith( (a,b) => median(a) > median(b) )
    
    // Take 3 highest rows
    val top3Matrix = sortedMatrix.take(3)
    
    // Mean the rows
    summarizeUsingMean(top3Matrix)
  }
  
  def summarizeUsingMedian( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    require( abundanceMatrix.length > 0, "abundanceMatrix is empty" )
    if( abundanceMatrix.length == 1 ) return abundanceMatrix.head
    
    abundanceMatrix.transpose.map( _calcMedianAbundance( _ ) )
  }
  
  def summarizeUsingMedianProfile( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    require( abundanceMatrix.length > 0, "abundanceMatrix is empty" )
    if( abundanceMatrix.length == 1 ) return abundanceMatrix.head
    
    // Transpose the matrix
    val transposedMatrix = abundanceMatrix.transpose
    
    // Select columns eligible for ratio computations (discard columns of frequency lower than 0.5)
    val colObsFreq = transposedMatrix.map( col => col.count( isZeroOrNaN(_) == false ).toFloat / col.length )
    val colObsFreqWithIdx = colObsFreq.zipWithIndex
    val eligibleColIndices = colObsFreqWithIdx.withFilter( _._1 >= 0.5 ).map( _._2 )
    val matrixWithEligibleCols = eligibleColIndices.map( i => transposedMatrix(i) ).transpose
    
    // Compute the ratio matrix
    val ratioMatrix = for( abundanceRow <- matrixWithEligibleCols ) yield {
      
      val ratioRow = for ( twoValues <- abundanceRow.sliding(2) ) yield {        
        val ( a, b ) = (twoValues.head, twoValues.last)        
        if( isZeroOrNaN(a) || isZeroOrNaN(b) ) Float.NaN else b / a
      }
      
      ratioRow.toArray
    }
    
    // Compute the median of ratios
    val medianRatios = summarizeUsingMedian(ratioMatrix)
    
    // Compute the TOP3 mean abundances
    val top3MeanAbundances = summarizeUsingMeanOfTop3(matrixWithEligibleCols)
    
    // Convert the median ratios into absolute abundances
    var previousAb = top3MeanAbundances.head
    val absoluteAbundances = new ArrayBuffer[Float]()
    absoluteAbundances += previousAb
    
    for( medianRatio <- medianRatios ) {
      val absoluteValue = previousAb * medianRatio
      absoluteAbundances += absoluteValue
      previousAb = absoluteValue
    }
    
    // Scale up the absolute abundances
    val( top3MaxValue, top3MaxIdx ) = top3MeanAbundances.zipWithIndex.maxBy(_._1)
    val curMaxValue = absoluteAbundances(top3MaxIdx)
    val scalingFactor = top3MaxValue / curMaxValue
    val scaledAbundances = absoluteAbundances.map( _ * scalingFactor )
    
    // Re-integrate empty cols
    val nbCols = transposedMatrix.length
    val eligibleColIndexSet = eligibleColIndices.toSet
    
    var j = 0
    val finalAbundances = for( i <- 0 until nbCols ) yield {
      if( eligibleColIndexSet.contains(i) == false ) Float.NaN
      else {
        val abundance = scaledAbundances(j)
        j += 1
        abundance
      }
    }
    
    finalAbundances.toArray
  }
  
  def summarizeUsingNormalizedMedianProfile( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {    
    require( abundanceMatrix.length > 0, "abundanceMatrix is empty" )
    if( abundanceMatrix.length == 1 ) return abundanceMatrix.head
    
    val transposedMatrix = abundanceMatrix.transpose
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(transposedMatrix).transpose
    
    val medianAbundances = summarizeUsingMedian(normalizedMatrix)
    
    // Scale up the mean abundances
    val top3MeanAbundances = summarizeUsingMeanOfTop3(abundanceMatrix)
    val( top3MaxValue, top3MaxIdx ) = top3MeanAbundances.zipWithIndex.maxBy(_._1)
    val curMaxValue = medianAbundances(top3MaxIdx)
    val scalingFactor = top3MaxValue / curMaxValue
    val scaledAbundances = medianAbundances.map( _ * scalingFactor )
    
    scaledAbundances
  }
  
  def summarizeUsingSum( abundanceMatrix: Array[Array[Float]] ): Array[Float] = {
    require( abundanceMatrix.length > 0, "abundanceMatrix is empty" )
    if( abundanceMatrix.length == 1 ) return abundanceMatrix.head
    
    abundanceMatrix.transpose.map( _calcAbundanceSum( _ ) )
  }
  
  // TODO: this method is duplicated in the Profilizer => put in a shared object ???
  private def _calcMeanAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _calcMedianAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else median(defAbundances)
  }
  
  private def _calcAbundanceSum(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter( isZeroOrNaN(_) == false )
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum
  }
  
}