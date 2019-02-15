package fr.proline.core.algo.lcms.summarizing

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.fitting.PolynomialCurveFitter
import org.apache.commons.math3.fitting.WeightedObservedPoints
import fr.proline.core.om.model.lcms.Peakel

object PeakelPolynomialFittingFilter extends IPeakelPreProcessing with LazyLogging {
  
  private val quadraticFitter = PolynomialCurveFitter.create(2).withMaxIterations(1000)
  private val polyFunction = new PolynomialFunction.Parametric
  
  def applyFilter( peakel: Peakel ): Peakel = {
    
    // Retrieve RT/intensity pairs above half maximum
    val apexIntensity = peakel.apexIntensity
    val halfApexIntensity = apexIntensity / 2
    val rtIntPairs = peakel.getElutionTimeIntensityPairs()
    val rtIntPairsAboveHM = rtIntPairs.filter(_._2 >= halfApexIntensity )
    
    // Transform RT/intensity pairs into WeightedObservedPoints
    val observations = new WeightedObservedPoints()
    rtIntPairsAboveHM.foreach { case (rt,intensity) =>
      observations.add(rt, intensity)
    }
    
    // Compute the coefficients of the quadratic fitter    
    val coeffs = try {
      quadraticFitter.fit(observations.toList())
    } catch {
      case e: Exception => {
        logger.trace("Error during the polynomial fitting of peakel with id="+peakel.id, e)
        return peakel
      }
    }
    
    // Check we got valid coefficients and return the peakel without any fitting if it is not the case
    if( coeffs(0) >= 0 || coeffs(1) <= 0 || coeffs(2) >= 0 ) {
      logger.trace("Invalid coefficients obtained during the polynomial fitting of peakel with id="+peakel.id)
      return peakel
    }
    
    // We may not reach the maximum value of the quadratic function
    // Thus we try to realign the peakel apex with the parabola vertex
    val( vertexRt, vertexIntensity ) = _calcParabolaVertex(coeffs)
    
    // Retrieve the peak at the observed apex
    val apexRtInPair = rtIntPairs(peakel.apexIndex)
    val deltaRtFromVertex = vertexRt - apexRtInPair._1
    
    // Compute new peakel intensities using the computed coefficients
    val fittedIntensities = peakel.dataMatrix.elutionTimes.map { elutionTime =>
      val fittedIntensity = polyFunction.value(elutionTime + deltaRtFromVertex, coeffs: _*)
      if( fittedIntensity < 0 ) 0f else fittedIntensity.toFloat
    }
    
    // Replace the peakel intensities by the computed ones
    val matrix = peakel.dataMatrix.copy(intensityValues = fittedIntensities)
    
    peakel.copy( dataMatrix = matrix )
  }
  
  private def _calcParabolaVertex(coeffs: Array[Double] ) = {
    val a = coeffs(2)
    val b = coeffs(1)
    
    val x = - b / (2 * a)
    val y = polyFunction.value(x, coeffs: _*)
    
    (x, y)
  }
  
}