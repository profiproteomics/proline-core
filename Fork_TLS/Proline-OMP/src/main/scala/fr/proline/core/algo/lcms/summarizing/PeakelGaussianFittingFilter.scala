package fr.proline.core.algo.lcms.summarizing

import com.typesafe.scalalogging.LazyLogging

import org.apache.commons.math3.analysis.function.Gaussian
import org.apache.commons.math3.fitting.GaussianCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints
import fr.proline.core.om.model.lcms.Peakel

object PeakelGaussianFittingFilter extends IPeakelPreProcessing with LazyLogging {
  
  private val gaussianFitter = GaussianCurveFitter.create().withMaxIterations(1000)
  private val gaussianFunction = new Gaussian.Parametric
  
  def applyFilter( peakel: Peakel ): Peakel = {
    
    // Retrieve RT/intensity pairs
    val rtIntPairs = peakel.getElutionTimeIntensityPairs()
    //println(rtIntPairs.toList)
    //println(rtIntPairs.map(_._2).mkString("\t"))
    
    // Transform RT/intensity pairs into WeightedObservedPoints
    val observations = new WeightedObservedPoints()
    rtIntPairs.foreach { case (rt,intensity) =>
      observations.add(rt, intensity)
    }
    
    // Compute the coefficients of the gaussian fitter    
    val coeffs = try {
      gaussianFitter.fit(observations.toList())
    } catch {
      case e: Exception => {
        logger.trace("Error during the gaussian fitting of peakel with id="+peakel.id, e)
        return peakel
      }
    }
    
    // Check we got valid coefficients and return the peakel without any fitting if it is not the case
    if( coeffs(0) <= 0 || coeffs(1) <= 0 || coeffs(2) <= 0 ) {
      logger.trace("Invalid coefficients obtained during the gaussian fitting of peakel with id="+peakel.id)
      return peakel
    }
    
    // We may not reach the maximum value of the gaussian function
    // Thus we try to realign the peakel apex with the gaussian maximum
    val xZero = coeffs(1)
    
    // Retrieve the peak at the observed apex
    val apexRtInPair = rtIntPairs(peakel.apexIndex)
    val deltaRtFromCenter = xZero - apexRtInPair._1
    
    // Compute new peakel intensities using the computed coefficients
    val fittedIntensities = peakel.dataMatrix.elutionTimes.map { elutionTime =>
      val fittedIntensity = gaussianFunction.value(elutionTime + deltaRtFromCenter, coeffs: _*)
      if( fittedIntensity < 0 ) 0f else fittedIntensity.toFloat
    }
    
    //println("fited",fittedIntensities.toList)
    
    // Replace the peakel intensities by the computed ones if they don't contain any PositiveInfinity value
    if( fittedIntensities.contains(Float.PositiveInfinity) ) peakel
    else {
      val matrix = peakel.dataMatrix.copy(intensityValues = fittedIntensities)
      peakel.copy( dataMatrix = matrix )
    }
  }
  
}