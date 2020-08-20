package fr.proline.core.algo.lcms

import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints
import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.lang.EnhancedEnum
import fr.profi.util.math.median
import fr.profi.ms.algo.IsotopePatternEstimator
import fr.proline.core.algo.lcms.summarizing._
import fr.proline.core.algo.msq.config.profilizer.MqPeptideAbundanceSummarizingMethod
import fr.proline.core.algo.msq.profilizer.AbundanceSummarizer
import fr.proline.core.algo.msq.profilizer.CommonsStatHelper
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.Peakel

case class FeatureSummarizingConfig(
  peakelPreProcessingMethod: String,
  peakelSummarizingMethod: String,
  featureSummarizingMethod: String,
  maxPeakelsCount: Option[Int] = None // should be only defined for FIRST_N_PEAKELS method
)

object FeatureSummarizer {
    
  def apply( config: FeatureSummarizingConfig ): FeatureSummarizer = { 
    
    val preProcMethodOpt = PeakelPreProcessingMethod.maybeNamed( config.peakelPreProcessingMethod )
    require( preProcMethodOpt.isDefined, "unknown pre-processing method: " + config.peakelPreProcessingMethod )
    
    val peakelSummarizingMethodOpt = PeakelSummarizingMethod.maybeNamed( config.peakelSummarizingMethod )
    require( peakelSummarizingMethodOpt.isDefined, "unknown peakel summarizing method: " + config.peakelSummarizingMethod )
    
    val featureSummarizingMethodOpt = FeatureSummarizingMethod.maybeNamed( config.featureSummarizingMethod )
    require( featureSummarizingMethodOpt.isDefined, "unknown feature summarizing method: " + config.featureSummarizingMethod )
    
    new FeatureSummarizer(
      peakelPreProcessingMethod = preProcMethodOpt.get,
      peakelSummarizingMethod = peakelSummarizingMethodOpt.get,
      featureSummarizingMethod = featureSummarizingMethodOpt.get,
      maxPeakelsCount = config.maxPeakelsCount
    )
  }
}

object FeatureSummarizingMethod extends EnhancedEnum {
  val ALL_PEAKELS = Value
  val FIRST_N_PEAKELS = Value
  val HIGHEST_PEAKEL = Value
  val HIGHEST_THEORETICAL_ISOTOPE = Value
  val ISOTOPE_PATTERN_FITTING = Value
  val LOWEST_VARIANCE_ISOTOPE = Value
  //val WEIGHTED_ISOTOPE_PATTERN_FITTING = Value // Weighted Least Squares regression
}

class FeatureSummarizer(
  val peakelPreProcessingMethod: PeakelPreProcessingMethod.Value,
  val peakelSummarizingMethod: PeakelSummarizingMethod.Value,
  val featureSummarizingMethod: FeatureSummarizingMethod.Value,
  val maxPeakelsCount: Option[Int] = None
) extends LazyLogging {
  
  import FeatureSummarizingMethod._
  
  /** Features with an index corresponding to a replicate number **/
  def computeFeaturesIntensities( indexedFeatures: Seq[(Feature,Int)] ): Array[Float] = {
    if(indexedFeatures.isEmpty) return Array()
    
    val peakelsByFeature = indexedFeatures.view.map { case (feature, index) =>
      feature -> feature.relations.peakelItems.map( _.getPeakel.get )
    } toMap
    
    // *** Pre-processing of peakels *** //
    val preProcFilterOpt = peakelPreProcessingMethod match {
      case PeakelPreProcessingMethod.GAUSSIAN_FITTING => {
        Some( PeakelGaussianFittingFilter )
      }
      case PeakelPreProcessingMethod.POLYNOMIAL_FITTING => {
        Some( PeakelPolynomialFittingFilter )
      }
      case PeakelPreProcessingMethod.SAVITZKY_GOLAY_SMOOTHING => {
        Some( PeakelSavitzkyGolayFilter )
      }
      case PeakelPreProcessingMethod.NONE => None
    }
    
    val peakelIntensitiesByFeatureParMap = peakelsByFeature.par.map { case (feature, peakels) =>
      val peakelIntensity = peakels.map { peakel =>
        val filteredPeakel = preProcFilterOpt match {
          case None => peakel
          case Some(filter) => filter.applyFilter(peakel)
        }
        
        PeakelSummarizer.computePeakelIntensity(filteredPeakel, peakelSummarizingMethod)
      }
      
      feature -> peakelIntensity
    }
    
    val peakelIntensitiesByFeature = Map() ++ peakelIntensitiesByFeatureParMap
    
    featureSummarizingMethod match {
      case ALL_PEAKELS => this.sumAllPeakelIntensities(indexedFeatures, peakelIntensitiesByFeature)
      case FIRST_N_PEAKELS => this.sumFirstPeakelIntensities(indexedFeatures, peakelIntensitiesByFeature, maxPeakelsCount.get)
      case HIGHEST_PEAKEL => this.retrieveHighestPeakelIntensities(indexedFeatures, peakelIntensitiesByFeature)
      case HIGHEST_THEORETICAL_ISOTOPE => this.retrieveHighestTheoIsotopeIntensities(indexedFeatures, peakelIntensitiesByFeature)
      case ISOTOPE_PATTERN_FITTING => this.fitPeakelIntensitiesWithIsotopePattern(indexedFeatures, peakelIntensitiesByFeature)
      case LOWEST_VARIANCE_ISOTOPE => this.retrieveLowestVarianceIsotopeIntensities(indexedFeatures, peakelIntensitiesByFeature)
      //case WEIGHTED_ISOTOPE_PATTERN_FITTING => this.weightedLinearLeastSquaresRegression(indexedFeatures, peakelIntensitiesByFeature, peakelsByFeature)
    }
    
  }
  
  private def sumAllPeakelIntensities(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Array[Float] = {
    indexedFeatures.map { case (feature, index) =>
      peakelIntensitiesByFeature(feature).sum
    } toArray
  }
  
  private def sumFirstPeakelIntensities(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]],
    maxPeakelsCount: Int
  ): Array[Float] = {
    require( maxPeakelsCount > 0, "maxPeakelsCount must be greater than zero" )
    
    val intensityMatrix = indexedFeatures.map { case (feature, index) =>
      peakelIntensitiesByFeature(feature)
    }
    
    val minIntensitiesCount = intensityMatrix.map(_.length).min
    val peakelsCountToUse = math.min(minIntensitiesCount, maxPeakelsCount )
    
    intensityMatrix.map { featureIntensities =>
      featureIntensities.take(peakelsCountToUse).sum
    } toArray
  }
  
  private def retrieveHighestPeakelIntensities(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Array[Float] = {
    indexedFeatures.map { case (feature, index) =>
      peakelIntensitiesByFeature(feature).max
    } toArray
  }
  
  private def retrieveHighestTheoIsotopeIntensities(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Array[Float] = {
    indexedFeatures.map { case (feature, index) =>
      val theoIP = IsotopePatternEstimator.getTheoreticalPattern(feature.moz, feature.charge)
      val maxTheoIntensityIdxPair = theoIP.abundances.zipWithIndex.maxBy(_._1)  //VDS Warning maxBy may return wrong value if NaN
      val maxIdx = maxTheoIntensityIdxPair._2
      
      val peakelIntensities = peakelIntensitiesByFeature(feature)
      if( maxIdx > peakelIntensities.length - 1 ) Float.NaN else peakelIntensities(maxIdx)      
    } toArray
  }
  
  /*private def fitPeakelIntensitiesWithIsotopePattern(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Array[Float] = {
    
    indexedFeatures.map { case (feature, index) =>
      val theoIP = IsotopePatternEstimator.getTheoreticalPattern(feature.moz, feature.charge)
      val theoIpIntensities = theoIP.abundances
      
      val peakelIntensities = peakelIntensitiesByFeature(feature)
      val obsOverTheoRatios = theoIpIntensities.zip(peakelIntensities).map { case (theoAb, obsAb) =>
        obsAb / theoAb
      }
      val scalingFactor = median(obsOverTheoRatios)

      //val bestIsotopeIndex = retrieveLowestVarianceIsotopeIndex(indexedFeatures, peakelIntensitiesByFeature)
      //val scalingFactor = peakelIntensities(bestIsotopeIndex) / theoIpIntensities(bestIsotopeIndex)
      
      val fittedIntensities = theoIpIntensities.map( _ * scalingFactor )
      //println("peakelIntensities",peakelIntensities.toList)
      //println("fitted",fittedIntensities.toList)
      
      fittedIntensities.sum
      
    } toArray
  }*/
  
  private def fitPeakelIntensitiesWithIsotopePattern(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Array[Float] = {
    
    // Retrieve the intensity matrix
    val intensityMatrix = indexedFeatures.map { case (feature, index) =>
      peakelIntensitiesByFeature(feature)
    }
    
    // Determine a theoretical isotope pattern
    val firstFeature = indexedFeatures.head._1
    val theoIP = IsotopePatternEstimator.getTheoreticalPattern(firstFeature.moz, firstFeature.charge)
    val theoIpIntensities = theoIP.abundances
    
    // Sort matrix by the distance between observed and theoretical patterns
    val intensityMatrixSortedByDistance = intensityMatrix.filter(_.isEmpty == false).sortBy { intensityRow =>
      
      // Scale theoretical pattern to monoisotopic observed peak
      val scalingFactor = intensityRow.head / theoIpIntensities.head
      val scaledTheoIntensities = theoIpIntensities.map( _ * scalingFactor )
      
      // Compute distance between observed and theoretical patterns
      val distance = intensityRow.zip(scaledTheoIntensities).foldLeft(0f) { case(s, (o,t)) => s + math.abs(o - t) / o }
      //println("distance", distance)
      
      distance
    }
    if (intensityMatrixSortedByDistance.isEmpty) {
      logger.warn("Can't compute feature intensities without data about the corresponding peakels")
      return indexedFeatures.map(_._1.intensity).toArray
    }
    
    // Create a new matrix of fixed length
    val filteredIntensityMatrix = intensityMatrixSortedByDistance.take(3)
    val maxIntensitiesCount = filteredIntensityMatrix.map(_.length).max
    val fixedLengthIntensityMatrix = filteredIntensityMatrix.map { row =>
      val newRow = Array.fill(maxIntensitiesCount)(Float.NaN)
      for( (v,idx) <- row.zipWithIndex ) {
        newRow(idx) = v
      }
      newRow
    } toArray
    
    // Summarize the matrix to a single median isotope pattern
    val medianIP = AbundanceSummarizer.summarizeAbundanceMatrix(fixedLengthIntensityMatrix, MqPeptideAbundanceSummarizingMethod.MEDIAN_PROFILE)
    //println("medianIP",medianIP.toList)
    val maxIdx = medianIP.zipWithIndex.maxBy(_._1)._2 //VDS Warning maxBy may return wrong value if NaN
    
    indexedFeatures.map { case (feature, index) =>

      val peakelIP = peakelIntensitiesByFeature(feature)
      if (peakelIP.isEmpty) {
        logger.warn("Can't compute feature intensities without data about the corresponding peakels")
        Float.NaN
      } else {
        
        // Compute a scaling factor for each excluded peak of the observed isotope pattern
        // Here is the procedure: 
        // - each peak is iteratively removed from the observed and theoretical patterns
        // - the obtained partial patterns are then compared to compute an average distance and a scaling factor
        // - we further retain the scaling factor associated with the lowest distance
        // This procedure allows us to remove a potential peakel interference inside the observed isotope pattern
        val scalingFactorsWithDistance = peakelIP.indices.map { case idx =>

          val( peakelIpWithExclusion, medianIpWithExclusion) = if( peakelIP.length == 1 ) {
            (peakelIP,medianIP)
          } else {
            (peakelIP.take(idx) ++ peakelIP.drop(idx + 1),
            medianIP.take(idx) ++ medianIP.drop(idx + 1))
          }

          // Compute the weighted average distance
          val weightedDist = computeWeightedAverageDistance(peakelIpWithExclusion, medianIpWithExclusion)

          // Compute the weighted average of ratios
          val scalingFactor = computeWeightedAverageRatio(peakelIpWithExclusion, medianIpWithExclusion)
          
          /*if (scalingFactor.isNaN) {
            println("peakelIpWithExclusion", peakelIpWithExclusion.toList)
            println("medianIpWithExclusion", medianIpWithExclusion.toList)
          }*/

          (scalingFactor, weightedDist)
        }
        //println("scalingFactorsWithDistance",scalingFactorsWithDistance.toList)

        // Take the scaling factor minimizing the distance between IPs (minus a given excluded isotope)
        val bestScalingFactor = scalingFactorsWithDistance.minBy(_._2)._1

        // Apply the scaling factor to compute the fitted isotope pattern
        val fittedIP = medianIP.map(_ * bestScalingFactor)
        //println("peakelIP",peakelIP.toList)
        //println("fittedIP",fittedIP.toList)

        fittedIP(maxIdx)
      }
      
    } toArray
  }
  
  private def computeWeightedAverageDistance( observedIntensities: Seq[Float], theoIntensities: Seq[Float] ): Float = {
    
    var coeffSum = 0f
    val distance = observedIntensities.zip(theoIntensities).foldLeft(0f) { case (d,(obsAb,theoAb)) =>
      coeffSum += obsAb
      d + math.abs(obsAb - theoAb) * obsAb
    }
    
    distance / coeffSum
  }
  
  private def computeWeightedAverageRatio( observedIntensities: Seq[Float], theoIntensities: Seq[Float] ): Float = {
    
    var coeffSum = 0f
    val obsOverMedianWeightedRatios = observedIntensities.zip(theoIntensities).map { case (obsAb,theoAb) =>
      coeffSum += obsAb
      (obsAb / theoAb) * obsAb
    }
    
    obsOverMedianWeightedRatios.sum / coeffSum
  }
  
  private def retrieveLowestVarianceIsotopeIntensities(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Array[Float] = {
    
    val bestIsotopeIndex = retrieveLowestVarianceIsotopeIndex(indexedFeatures, peakelIntensitiesByFeature)
    
    indexedFeatures.map { case (feature, index) =>
      val featureIntensities = peakelIntensitiesByFeature(feature)
      featureIntensities(bestIsotopeIndex)
    } toArray
  }
  
  private def retrieveLowestVarianceIsotopeIndex(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]]
  ): Int = {

    val intensitiesCount = indexedFeatures.map { case (feature, index) =>
      peakelIntensitiesByFeature(feature).length
    }    
    val minIntensitiesCount = intensitiesCount.min
    if( minIntensitiesCount == 1 ) {
      return 0
    }
    
    val featuresByGroup = indexedFeatures.groupBy(_._2)
    
    val coeffsOfVarByIsotopeIndex = new HashMap[Int,ArrayBuffer[Double]]
    for( (groupIndex,features) <- featuresByGroup ) {
      val groupIntensityMatrix = features.map { case (feature, index) =>
        peakelIntensitiesByFeature(feature)
      }
      
      for( i <- 0 until minIntensitiesCount) {
        val sameIsotopeIntensities = groupIntensityMatrix.map( _(i).toDouble ).toArray
        val intensityStatSummary = CommonsStatHelper.calcExtendedStatSummary(sameIsotopeIntensities)
        val cv = intensityStatSummary.getStandardDeviation / intensityStatSummary.getMean
        
        coeffsOfVarByIsotopeIndex.getOrElseUpdate(i, new ArrayBuffer[Double]) += cv
      }
    }
    
    // Search for the isotope with the lowest mean of CVs
    val bestResult = coeffsOfVarByIsotopeIndex.minBy { case(i, coeffsOfVar) =>
      coeffsOfVar.sum / coeffsOfVar.length
    }
    val bestIsotopeIndex = bestResult._1
    //logger.debug("retrieveLowestVariancePeakelIntensities => bestIsotopeIndex = "+ bestIsotopeIndex )
    
    bestIsotopeIndex
  }
  
  private lazy val linearFitter = PolynomialCurveFitter.create(1)
  private lazy val polyFunction = new PolynomialFunction.Parametric
  
  private def weightedLinearLeastSquaresRegression(
    indexedFeatures: Seq[(Feature,Int)],
    peakelIntensitiesByFeature: Map[Feature, Array[Float]],
    peakelsByFeature: Map[Feature, Array[Peakel]]
  ): Array[Float] = {
    
    val computedIntensities = indexedFeatures.map { case (feature, index) =>
      val peakelIntensities = peakelIntensitiesByFeature(feature)
      //val peakels = peakelsByFeature(feature)
      
      val theoIP = IsotopePatternEstimator.getTheoreticalPattern(feature.moz, feature.charge)
      val theoIpIntensities = theoIP.abundances
      
      val observations = new WeightedObservedPoints()
      var i = 0
      theoIpIntensities.zip(peakelIntensities).foreach { case (theoAb, obsAb) =>
        //val peakel = peakels(i)
        // TODO: provide a weight relative to the FWHM error ?
        observations.add(obsAb, theoAb, obsAb) // weight, x, y
        
        i += 1
      }
      
      val coeffs = linearFitter.fit(observations.toList())
      val ratio = coeffs(1).toFloat
      
      theoIpIntensities.map( polyFunction.value(_, coeffs: _*) ).sum.toFloat
      
    } toArray
    
    // Re-scale computed intensities (the regression may have modified the scale of the computed values)
    val featuresIntensities = indexedFeatures.map( _._1.intensity )
    
    val observedVsComputedIntensities = featuresIntensities.zip(computedIntensities).map { case (o,c) =>
      o / c
    }
    val medianRatio = median(observedVsComputedIntensities)
    
    computedIntensities.map( _ * medianRatio )
  }

}