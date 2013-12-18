package fr.proline.core.algo.msq

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import org.apache.commons.math.stat.descriptive.StatisticalSummary
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
    val abundanceMatrix = masterQuantPeptides.map( _.getRawAbundancesForQuantChannels(qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrix)
    
    // --- Estimate the noise model ---    
    val ratiosByMQPepId = Map() ++ masterQuantPeptides.map( _.id -> new ArrayBuffer[Option[ComputedRatio]] )
    val mqPepById = Map() ++ masterQuantPeptides.map( mqPep => mqPep.id -> mqPep )
    
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
  
  def computeMasterQuantProtSetProfiles( masterQuantProtSets: Seq[MasterQuantProteinSet], statTestsAlpha: Float = 0.01f ) {
    
    case class MQPepProfilesCluster(
      mqProtSet: MasterQuantProteinSet,
      mqPeps: Seq[MasterQuantPeptide]
      //abundanceMatrixIndex: Int
    )
    
    // --- Clusterize MQ peptides profiles and compute corresponding abundance matrix ---
    val profilesClusters = new ArrayBuffer[MQPepProfilesCluster](masterQuantProtSets.length)
    val abundanceMatrixBuffer = new ArrayBuffer[Array[Float]](masterQuantProtSets.length)    
    
    for( masterQuantProtSet <- masterQuantProtSets ) {
      
      // Clusterize MasterQuantPeptides according to their profile
      val mqPepsByProfileAsStr = new HashMap[String,ArrayBuffer[MasterQuantPeptide]]()
      
      masterQuantProtSet.masterQuantPeptides.foreach { mqPep =>
        // TODO: allows to configure the desired specificity level
        if( mqPep.peptideInstance.get.isProteinSetSpecific ) {
          val mqPepProfileOpt = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get.get( groupSetupNumber.toString )
          
          for( mqPepProfile <- mqPepProfileOpt ) {
            val profileSlopes = mqPepProfile.ratios.map( _.map( _.state ).get )
            val profileAsStr = _stringifyProfile( profileSlopes )
            
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
    val normalizedMatrix = AbundanceNormalizer.normalizeAbundances(abundanceMatrixBuffer.toArray)
    
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
      
      val( filledMatrix, ratios ) = computeRatios( ratioDef, normalizedMatrix, statTestsAlpha )
 
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
    statTestsAlpha: Float
  ): Tuple2[Array[Array[Float]],Seq[AverageAbundanceRatio]] = {

    // Retrieve some vars
    val numeratorSampleNumbers = sampleNumbersByGroupNumber(ratioDef.numeratorGroupNumber)
    val denominatorSampleNumbers = sampleNumbersByGroupNumber(ratioDef.denominatorGroupNumber)
    val allSampleNumbers = numeratorSampleNumbers ++ denominatorSampleNumbers
    require( numeratorSampleNumbers != null && numeratorSampleNumbers.isEmpty == false, "numeratorSampleNumbers must be defined" )
    require( denominatorSampleNumbers != null && denominatorSampleNumbers.isEmpty == false, "denominatorSampleNumbers must be defined" )
    
    // Map quant channel indices by the sample number
    val qcIndicesBySampleNum = Map() ++ ( allSampleNumbers ).map { sampleNum =>
      sampleNum -> quantChannelsBySampleNumber(sampleNum).map( qc => qcIdxById(qc.id) )
    }
    
    def _getSamplesAbundances(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.flatMap( i => i.map( abundances(_) ) )
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
          require( qcIndices != null && qcIndices.length > 2, "qcIndices must be defined" )
          
          val sampleAbundances = qcIndices.map(normalizedRow).map(_.toDouble)
          val sampleStatSummary = CommonsStatHelper.calcStatSummary(sampleAbundances)
          val abundance = sampleStatSummary.getMean.toFloat
          
          if( abundance.isNaN == false && abundance > 0 )
            absoluteErrors += AbsoluteErrorObservation( abundance, sampleStatSummary.getStandardDeviation.toFloat )
        }
      } else {
        
        val numeratorMeanAbundance = _meanAbundance( _getSamplesMeanAbundance( normalizedRow, numeratorSampleNumbers ) )
        val denominatorMeanAbundance = _meanAbundance( _getSamplesMeanAbundance( normalizedRow, denominatorSampleNumbers ) )
        
        val maxAbundance = math.max(numeratorMeanAbundance,denominatorMeanAbundance)
        
        if( maxAbundance.isNaN == false && maxAbundance > 0 ) {          
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
      
    } else {
      // TODO: find what to do if when insufficient technical replicates
      this.logger.warn("insufficient number of analysis replicates => can't infer missing values")
      normalizedMatrix
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
        absoluteVariationsBuffer += AbsoluteErrorObservation( numeratorSummary.getMean.toFloat, numeratorSummary.getStandardDeviation.toFloat )
        absoluteVariationsBuffer += AbsoluteErrorObservation( denominatorSummary.getMean.toFloat, denominatorSummary.getStandardDeviation.toFloat )
      }
      // Else we merge biological sample data and compute statistics at a lower level
      else {        
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcStatSummary( _getSamplesAbundances( filledRow, numeratorSampleNumbers ).map(_.toDouble) )
        denominatorSummary = CommonsStatHelper.calcStatSummary( _getSamplesAbundances( filledRow, denominatorSampleNumbers ).map(_.toDouble) )  
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
    
    // Update the variation state of ratios
    if( minSamplesCountPerGroup > 2 ) { 
      val absoluteVariationModel = ErrorModelComputer.computeAbsoluteErrorModel(absoluteVariationsBuffer,nbins=Some(5))
      AbundanceRatiolizer.updateRatioStates(ratiosBuffer, relativeVariationModel, Some(absoluteVariationModel), statTestsAlpha)
    }
    else if( minQCsCountPerSample > 2 ) {
      AbundanceRatiolizer.updateRatioStates(ratiosBuffer, relativeVariationModel, Some(absoluteNoiseModel), statTestsAlpha)
    } else {
      AbundanceRatiolizer.updateRatioStates(ratiosBuffer, relativeVariationModel, None, statTestsAlpha)
    }
    
    (filledMatrix,ratiosBuffer)
  }
  
  private def _meanAbundance(abundances: Array[Float]): Float = {
    val defAbundances = abundances.filter(a => a.isNaN == false && a > 0)
    if( defAbundances.length == 0 ) Float.NaN else defAbundances.sum / defAbundances.length
  }
  
  private def _stringifyProfile( slopes: Seq[Int] ): String = { slopes.mkString(";") }

}