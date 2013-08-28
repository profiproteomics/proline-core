package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.msq._

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
    
  def computeMasterQuantPeptideProfiles( masterQuantPeptides: Seq[MasterQuantPeptide], statTestsAlpha: Float = 0.01f ) {
    
    // --- Compute the abundance matrix ---
    val abundanceMatrix = masterQuantPeptides.map( _.getAbundancesForQuantChannels(qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrix)
    
    // --- Estimate the noise model ---    
    val ratiosByMasterQuantPeptide = Map() ++ masterQuantPeptides.map( _ -> new ArrayBuffer[Option[ComputedRatio]] )
    
    // Iterate over the ratio definitions
    for ( ratioDef <- groupSetup.ratioDefinitions ) {
      
      val( filledMatrix, ratios ) = computeRatios( ratioDef, normalizedMatrix, statTestsAlpha )
 
      for ( ratio <- ratios ) {
        val index = ratio.entityId.toInt
        val masterQuantPep = masterQuantPeptides(index)
        val abundances = filledMatrix(index)
        masterQuantPep.setAbundancesForQuantChannels(abundances,qcIds)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numeratorMean.toFloat,
          denominator = ratio.denominatorMean.toFloat,
          state = ratio.state.map(_.id)
        )
        
        ratiosByMasterQuantPeptide(masterQuantPep) += Some(computedRatio)
      }
    
    }
    
    
    // Update the profiles using the obtained results
    for ( (mqPep,ratios) <- ratiosByMasterQuantPeptide ) {

      val mqPepProps = mqPep.properties.getOrElse( new MasterQuantPeptideProperties() )
      
      val quantProfile = new MasterQuantPeptideProfile( ratios = ratios.toArray )
      mqPepProps.setMqPepProfileByGroupSetupNumber(
        Some( mqPepProps.getMqPepProfileByGroupSetupNumber.getOrElse( Map() ) + ( groupSetupNumber -> quantProfile ) )
      )
      
      mqPep.properties = Some(mqPepProps)
    }
    
    ()
    
  }
  
  def computeMasterQuantProtSetProfiles( masterQuantProtSets: Seq[MasterQuantProteinSet], statTestsAlpha: Float = 0.01f ) {
    
    case class MQPepProfilesCluster(
      mqProtSet: MasterQuantProteinSet,
      mqPeps: Seq[MasterQuantPeptide]
      //abundanceMatrixIndex: Int
    )
    
    val abundanceMatrixBuffer = new ArrayBuffer[Array[Float]](masterQuantProtSets.length)
    val profilesClusters = new ArrayBuffer[MQPepProfilesCluster](masterQuantProtSets.length)
    
    for( masterQuantProtSet <- masterQuantProtSets ) {
      
      // Clusterize MasterQuantPeptides according tyo their profile
      val mqPepsByProfileAsStr = new HashMap[String,ArrayBuffer[MasterQuantPeptide]]()
      
      masterQuantProtSet.masterQuantPeptides.foreach { mqPep =>
        val mqPepProfileOpt = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get.get( groupSetupNumber )
        
        for( mqPepProfile <- mqPepProfileOpt ) {
          val profileSlopes = mqPepProfile.ratios.map( _.map( _.state.getOrElse(0) ).get )
          val profileAsStr = _stringifyProfile( profileSlopes )
          
          mqPepsByProfileAsStr.getOrElseUpdate(profileAsStr, new ArrayBuffer[MasterQuantPeptide] ) += mqPep
        }
        
      }
      
      // Build master quant protein set profiles
      for( (profileAsStr,mqPeps) <- mqPepsByProfileAsStr ) {
        
        val abundanceMatrix = mqPeps.map( _.getAbundancesForQuantChannels(qcIds) ).toArray
        val summarizedAbundances = abundanceMatrix.transpose.map( _meanAbundance( _ ) )
        
        profilesClusters += MQPepProfilesCluster(
          mqProtSet = masterQuantProtSet,
          mqPeps = mqPeps
        )
        
        abundanceMatrixBuffer += summarizedAbundances
      }
    }
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrixBuffer.toArray)
    
    // Create an map which will store the abundances corresponding to each profile cluster
    val abundancesByProfileClusterBuilder = Map.newBuilder[MQPepProfilesCluster,Array[Float]]
    abundancesByProfileClusterBuilder.sizeHint(normalizedMatrix.length)
    
    normalizedMatrix.indices.foreach { rowIndex =>
      val profileCluster = profilesClusters(rowIndex)
      abundancesByProfileClusterBuilder += profileCluster -> normalizedMatrix(rowIndex)
    }
    val abundancesByProfileCluster = abundancesByProfileClusterBuilder.result
    
    // Create an map which will store the ratios corresponding to each profile cluster
    val ratiosByProfileCluster = Map() ++ profilesClusters.map( _ -> new ArrayBuffer[Option[ComputedRatio]] )
    
    // Iterate over the ratio definitions
    for ( ratioDef <- groupSetup.ratioDefinitions ) {
      
      val( filledMatrix, ratios ) = computeRatios( ratioDef, normalizedMatrix, statTestsAlpha )
 
      for ( ratio <- ratios ) {

        val computedRatio = ComputedRatio(
          numerator = ratio.numeratorMean.toFloat,
          denominator = ratio.denominatorMean.toFloat,
          state = ratio.state.map(_.id)
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
        ratios = ratios.toArray,
        mqPeptideIds = mqPeptideIds
      )
      
      mqProfilesByProtSet(mqProtSet) += quantProfile
    }
    
    // Update Master Quant protein sets properties
    for( (mqProtSet,mqProfiles) <- mqProfilesByProtSet ) {
      val mqProtSetProps = mqProtSet.properties.getOrElse( new MasterQuantProteinSetProperties() )      
      val mqProtSetProfileMap = mqProtSetProps.getMqProtSetProfilesByGroupSetupNumber.getOrElse( Map() )
      
      mqProtSetProps.setMqProtSetProfilesByGroupSetupNumber(
        Some( mqProtSetProfileMap + ( groupSetupNumber -> mqProfiles.toArray ) )
      )
      
      mqProtSet.properties = Some(mqProtSetProps)
    }
    
    ()
  }
  
  protected def computeRatios(
    ratioDef: RatioDefinition,
    normalizedMatrix: Array[Array[Float]],
    statTestsAlpha: Float
  ): Tuple2[Array[Array[Float]],Seq[AverageAbundanceRatio]] = {

    // Retrieve some vars
    val numeratorSampleNumbers = sampleNumbersByGroupNumber(ratioDef.numeratorGroupNumber)
    val denominatorSampleNumbers = sampleNumbersByGroupNumber(ratioDef.denominatorGroupNumber)
    val allSampleNumbers = numeratorSampleNumbers ++ denominatorSampleNumbers
    
    // Map quant channel indices by the sample number
    val qcIndicesBySampleNum = Map() ++ ( allSampleNumbers ).map { sampleNum =>
      sampleNum -> quantChannelsBySampleNumber(sampleNum).map( qc => qcIdxById(qc.id) )
    }
    
    def _getSamplesMeanAbundance(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.map { i => _meanAbundance( i.map( abundances(_) ) ) }
    }
    
    // --- Estimate the noise models ---
    val absoluteErrors = new ArrayBuffer[AbsoluteErrorObservation](normalizedMatrix.length)
    val relativeErrors = new ArrayBuffer[RelativeErrorObservation](normalizedMatrix.length)
    
    // Iterator over each row of the abundance matrix in order to compute some statistical models
    normalizedMatrix.foreach { normalizedRow =>

      if( minQCsCountPerSample > 2 ) {
        // Iterate over each sample in order to build the absolute error model using existing sample analysis replicates
        for( sampleNum <- allSampleNumbers ) {
          val qcIndices = qcIndicesBySampleNum(sampleNum)
          
          if( qcIndices.length > 1 ) {
            val sampleAbundances = qcIndices.map(normalizedRow).map(_.toDouble)
            val sampleStatSummary = CommonsStatHelper.calcStatSummary(sampleAbundances)
            
            absoluteErrors += AbsoluteErrorObservation( sampleStatSummary.getMean.toFloat, sampleStatSummary.getStandardDeviation.toFloat )
          }
        }
      } else {
        
        val numeratorMeanAbundance = _meanAbundance( _getSamplesMeanAbundance( normalizedRow, numeratorSampleNumbers ) )
        val denominatorMeanAbundance = _meanAbundance( _getSamplesMeanAbundance( normalizedRow, denominatorSampleNumbers ) )
        
        if( denominatorMeanAbundance.isNaN == false && denominatorMeanAbundance > 0 ) {
          val maxAbundance = math.max(numeratorMeanAbundance,denominatorMeanAbundance)
          relativeErrors += RelativeErrorObservation( maxAbundance, numeratorMeanAbundance/denominatorMeanAbundance)
        }
        
      }
      
    }
    
    // Estimate the absolute noise model
    val absoluteNoiseModel = if( minQCsCountPerSample > 2 ) {
      ErrorModelComputer.computeAbsoluteErrorModel(absoluteErrors,nbins=Some(5))
    } else {
      this.logger.warn("insufficient number of analysis replicates => try to estimate absolute noise model using relative observations")
      ErrorModelComputer.computeRelativeErrorModel(relativeErrors, nbins=Some(5)).toAbsoluteErrorModel
    }
    
    // --- Infer missing abundances ---      
    val filledMatrix = if( minQCsCountPerSample > 2 ) {
      
      val matrixBySampleNum = Map() ++ allSampleNumbers.map( _ -> new ArrayBuffer[Array[Float]] )
      
      // Extract abundance matrices for each biological sample
      normalizedMatrix.foreach { normalizedRow =>        
        allSampleNumbers.foreach { sampleNum =>            
          matrixBySampleNum(sampleNum) += qcIndicesBySampleNum(sampleNum).map(normalizedRow(_))
        }
      }
      
      val filledMatrix = Array.ofDim[Float](normalizedMatrix.length,quantChannels.length)
      
      for( (sampleNum,sampleMatrixBuffer) <- matrixBySampleNum ) {
        
        val qcIds = qcIndicesBySampleNum(sampleNum)
        val inferredSampleMatrix = MissingAbundancesInferer.inferAbundances(sampleMatrixBuffer.toArray, absoluteNoiseModel)
        
        var filledMatrixRow = 0
        inferredSampleMatrix.foreach { inferredAbundances =>
          
          inferredAbundances.zip(qcIds).foreach { case (abundance,colIdx) =>
            filledMatrix(filledMatrixRow)(colIdx) = abundance
          }
          
          filledMatrixRow += 1
        }

      }
      
      filledMatrix
      
    } else {
      // TODO: find what to do if when insufficient technical replicates
      normalizedMatrix
    }
    
    
    // --- Determine the significant abundance changes ---
    
    // Create a new buffer for ratios to be computed
    val ratiosBuffer = new ArrayBuffer[AverageAbundanceRatio](normalizedMatrix.length)
    
    // Compute the error models
    val absoluteVariationsBuffer = new ArrayBuffer[AbsoluteErrorObservation](normalizedMatrix.length) // for n sample replicates
    val relativeVariationsBuffer = new ArrayBuffer[RelativeErrorObservation](normalizedMatrix.length) // for 1 sample replicate
    
    var rowIdx = 0
    filledMatrix.foreach { fullRow =>
      
      // Retrieve the entity ID
      //val masterQuantPepId = masterQuantPeptides(rowIdx).id
      
      // Compute numerator and denominator abundances
      val numeratorMeanAbundances = _getSamplesMeanAbundance( fullRow, numeratorSampleNumbers )
      val denominatorMeanAbundances = _getSamplesMeanAbundance( fullRow, numeratorSampleNumbers )
      
      // Compute numerator and denominator statistical summaries
      val numeratorSummary = CommonsStatHelper.calcStatSummary(numeratorMeanAbundances.map(_.toDouble))
      val denominatorSummary = CommonsStatHelper.calcStatSummary(denominatorMeanAbundances.map(_.toDouble))
      
      // Compute the ratio for this row
      val ratio = new AverageAbundanceRatio( rowIdx, numeratorSummary, denominatorSummary )
      ratiosBuffer += ratio
      
      // Check if we have enough biological sample replicates
      if( minSamplesCountPerGroup > 2 ) {
        
        // We can then make absolute statistical validation at the biological sample level
        absoluteVariationsBuffer += AbsoluteErrorObservation( numeratorSummary.getMean.toFloat, numeratorSummary.getStandardDeviation.toFloat )
        absoluteVariationsBuffer += AbsoluteErrorObservation( denominatorSummary.getMean.toFloat, denominatorSummary.getStandardDeviation.toFloat )
        
      } else {
        
        // We can only use the relative error model
        if( ratio.ratioValue.isDefined ) {
          relativeVariationsBuffer += RelativeErrorObservation( ratio.maxAbundance.toFloat, ratio.ratioValue.get )
        }
      }

      rowIdx += 1
    }
    
    val relativeVariationModel = ErrorModelComputer.computeRelativeErrorModel(relativeVariationsBuffer,nbins=Some(5))
    
    // Update the variation state of ratios
    AbundanceRatiolizer.updateRatioStates(ratiosBuffer, absoluteNoiseModel, relativeVariationModel, statTestsAlpha)
    
    (normalizedMatrix,ratiosBuffer)
  }
  
  private def _meanAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter(a => a.isNaN == false && a > 0)
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _stringifyProfile( slopes: Seq[Int] ): String = { slopes.mkString(";") }

}