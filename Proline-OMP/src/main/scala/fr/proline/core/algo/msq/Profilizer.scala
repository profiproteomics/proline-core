package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import org.apache.commons.math.stat.descriptive.StatisticalSummary
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.msq._
import fr.proline.util.primitives.isZeroOrNaN

case class ProfilizerConfig(
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

/**
 * Analyze profiles of Master Quant Peptides and Master Quant Protein sets
 */
class Profilizer( expDesign: ExperimentalDesign, groupSetupNumber: Int = 0, masterQCNumber: Int = 1 ) extends Logging {
  
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
    
    // --- Reset quant profiles for each masterQuantPeptide ---
    for( mqPep <- masterQuantPeptides; mqPepProps <- mqPep.properties ) {
      mqPepProps.setMqPepProfileByGroupSetupNumber(None)
    }
    
    // --- Apply MC filter if requested ---
    val mqPepsAfterMCFilter = if( config.discardMissedCleavedPeptides == false ) masterQuantPeptides
    else this.discardMissedCleavedPeptides(masterQuantPeptides)
    
    // --- Apply Oxidation filter if requested ---
    val mqPepsAfterAllFilters = if( config.discardOxidizedPeptides == false ) mqPepsAfterMCFilter
    else this.discardOxidizedPeptides(mqPepsAfterMCFilter)
    
    // --- Compute the abundance matrix ---
    val abundanceMatrix = mqPepsAfterAllFilters.map( _.getRawAbundancesForQuantChannels(qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( config.applyNormalization == false ) abundanceMatrix
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
      
      val( filledMatrix, ratios ) = computeRatios( ratioDef, normalizedMatrix, config.peptideStatTestsAlpha, config )
 
      for ( ratio <- ratios ) {
        val index = ratio.entityId.toInt
        val masterQuantPep = mqPepsAfterAllFilters(index)
        val abundances = filledMatrix(index)
        masterQuantPep.setAbundancesForQuantChannels(abundances,qcIds)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numeratorMean.toFloat,
          denominator = ratio.denominatorMean.toFloat,
          state = ratio.state.map(_.id).getOrElse(0),
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
      mqPeptProfileMap += ( groupSetupNumber.toString -> quantProfile )
      mqPepProps.setMqPepProfileByGroupSetupNumber( Some(mqPeptProfileMap) )
      
      mqPep.properties = Some(mqPepProps)
    }
    
    ()
    
  }
  
  protected def discardMissedCleavedPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Seq[MasterQuantPeptide] = {
    
    // FIXME: we assume here that Trypsin has been used => retrieve the right enzyme to apply this filter correctly
    val regex = ".*?[R|K]".r
    val detectedMCSeqParts = new ArrayBuffer[String]()
    
    val keptMqPeps = masterQuantPeptides.filter { mqPep =>
      if( mqPep.peptideInstance.isEmpty ) true
      else {
        val pepSeq = mqPep.peptideInstance.get.peptide.sequence
        
        val seqParts = regex.findAllIn(pepSeq).toArray
        
        if(seqParts.length == 1) true
        else {
          // Append detected missed cleaved sequences in the buffer
          detectedMCSeqParts ++= seqParts
          
          // Discard detected missed cleaved peptide
          mqPep.selectionLevel = 1
          
          false
        }
      }
    }
    
    // Convert the detectedSeqsWithMC buffer into a Set
    val detecteMCSeqSet = detectedMCSeqParts.toSet
    
    // Filter kept master quant peptides again to remove the counterpart of the MC ones      
    keptMqPeps.filter { mqPep =>
      if( mqPep.peptideInstance.isEmpty ) true
      else {
        val pepSeq = mqPep.peptideInstance.get.peptide.sequence
        
        if( detecteMCSeqSet.contains(pepSeq) == false ) true
        else {
          mqPep.selectionLevel = 1
          false
        }
      }
    }
    
  }
  
  protected def discardOxidizedPeptides( masterQuantPeptides: Seq[MasterQuantPeptide] ): Seq[MasterQuantPeptide] = {

    val pattern = """\[O\]""".r.pattern
    val detectedOxSeqs = new ArrayBuffer[String]()
    
    val keptMqPeps = masterQuantPeptides.filter { mqPep =>
      if( mqPep.peptideInstance.isEmpty ) true
      else {
        val peptide = mqPep.peptideInstance.get.peptide
        val ptmString = peptide.ptmString
        
        if( pattern.matcher(ptmString).matches == false ) true
        else {
          // Append detected oxidized sequence in the buffer
          detectedOxSeqs += peptide.sequence
          
          // Discard detected oxidized peptide
          mqPep.selectionLevel = 1
          
          false
        }
      }
    }
    
    // Convert the detectedSeqsWithMC buffer into a Set
    val detectedOxSeqSet = detectedOxSeqs.toSet
    
    // Filter kept master quant peptides again to remove the counterpart of the oxidized ones      
    keptMqPeps.filter { mqPep =>
      if( mqPep.peptideInstance.isEmpty ) true
      else {
        val pepSeq = mqPep.peptideInstance.get.peptide.sequence
        
        if( detectedOxSeqSet.contains(pepSeq) == false ) true
        else {
          mqPep.selectionLevel = 1
          false
        }
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
    
    for( masterQuantProtSet <- masterQuantProtSets ) {
      
      // Reset quant profiles for this masterQuantProtSet
      for( masterQuantProtSetProps <- masterQuantProtSet.properties) {
        masterQuantProtSetProps.setMqProtSetProfilesByGroupSetupNumber(None)
      }
      
      // Clusterize MasterQuantPeptides according to their profile
      val mqPepsByProfileAsStr = new HashMap[String,ArrayBuffer[MasterQuantPeptide]]()
      
      masterQuantProtSet.masterQuantPeptides.foreach { mqPep =>
        
        if( mqPep.selectionLevel >= 2 && (config.useOnlySpecificPeptides == false || mqPep.peptideInstance.get.isProteinSetSpecific) ) {
          val mqPepProfileOpt = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get.get( groupSetupNumber.toString )
          
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
        
        val abundanceMatrix = mqPeps.map( _.getAbundancesForQuantChannels(qcIds) ).toArray
        
        /*for (row <- abundanceMatrix ) {
          val nbDefs = row.count(ab => ab.isNaN == false && ab > 0)
          if( nbDefs < 6 ) println(mqPeps(0).peptideInstance.get.peptide.sequence)
        }*/
        
        val summarizedAbundances = abundanceMatrix.transpose.map( _meanAbundance( _ ) )
        
        profilesClusters += MQPepProfilesCluster(
          mqProtSet = masterQuantProtSet,
          mqPeps = mqPeps
        )
        
        /*val nbDefs = summarizedAbundances.count(ab => ab.isNaN == false && ab > 0)
        if( nbDefs < 6 ) println(masterQuantProtSet.proteinSet.getTypicalProteinMatch.get.accession)*/
        
        abundanceMatrixBuffer += summarizedAbundances
      }
    }
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( config.applyNormalization == false ) abundanceMatrixBuffer.toArray
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
      
      val( filledMatrix, ratios ) = computeRatios( ratioDef, normalizedMatrix, config.proteinStatTestsAlpha, config )
 
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
      
      mqProtSetProfileMap += (groupSetupNumber.toString -> mqProfiles.toArray)
      mqProtSetProps.setMqProtSetProfilesByGroupSetupNumber( Some(mqProtSetProfileMap) )
      
      mqProtSet.properties = Some(mqProtSetProps)
    }
    
    ()
  }
  
  protected def computeRatios(
    ratioDef: RatioDefinition,
    normalizedMatrix: Array[Array[Float]],
    statTestsAlpha: Float,
    config: ProfilizerConfig
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

      // Compute statistics at technical replicate level if enough replicates
      if( minQCsCountPerSample > 2 ) {
        // Iterate over each sample in order to build the absolute error model using existing sample analysis replicates
        for( sampleNum <- allSampleNumbers ) {
          val qcIndices = qcIndicesBySampleNum(sampleNum)
          require( qcIndices != null && qcIndices.length > 2, "qcIndices must be defined" )
          
          val sampleAbundances = qcIndices.map( normalizedRow(_) ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
          //println(sampleAbundances)
          
          val sampleStatSummary = CommonsStatHelper.calcStatSummary(sampleAbundances)
          val abundance = sampleStatSummary.getMean.toFloat
          
          if( isZeroOrNaN(abundance) == false )
            absoluteErrors += AbsoluteErrorObservation( abundance, sampleStatSummary.getStandardDeviation.toFloat )
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
        
        val matrixBySampleNum = allSampleNumbers.map( _ -> new ArrayBuffer[Array[Float]] ).toMap      
        //println( s"normalizedMatrix.length: ${normalizedMatrix.length}" )
        
        /*allSampleNumbers.foreach { sampleNum =>
          qcIndicesBySampleNum(sampleNum).map( println(_) )
        }*/
        
        // Extract abundance matrices for each biological sample
        normalizedMatrix.foreach { normalizedRow =>
          //val nbDefsVals = normalizedRow.count(isZeroOrNaN(_) == false)
          //if( nbDefsVals > 0 ) println( s"nbDefsVals: ${nbDefsVals}" )
          
          allSampleNumbers.foreach { sampleNum =>
            matrixBySampleNum(sampleNum) += qcIndicesBySampleNum(sampleNum).map(normalizedRow(_))
          }
        }
        
        val tmpFilledMatrix = Array.ofDim[Float](normalizedMatrix.length,quantChannels.length)
        
        for( (sampleNum,sampleMatrixBuffer) <- matrixBySampleNum ) {
          
          val qcIndices = qcIndicesBySampleNum(sampleNum)
          val inferredSampleMatrix = MissingAbundancesInferer.inferAbundances(sampleMatrixBuffer.toArray, absoluteNoiseModel)
          
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
      statTestsAlpha,
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