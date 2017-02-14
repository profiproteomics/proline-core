package fr.proline.core.algo.lcms.summarizing

import mr.go.sgfilter.SGFilterMath3
import fr.profi.util.math.median
import fr.proline.core.om.model.lcms.Peakel

object PeakelSavitzkyGolayFilter extends IPeakelPreProcessing {
  
  def applyFilter( peakel: Peakel ): Peakel = {

    val nbPoints = 5
    val polyOrder = 4
    val times = 1
    
    val(nl,nr,order) = (nbPoints,nbPoints,polyOrder)
    val coeffs = SGFilterMath3.computeSGCoefficients(nl,nr,order)
    val sgFilter = new SGFilterMath3(nbPoints,nbPoints)
    
    val intensities = peakel.dataMatrix.intensityValues
    var smoothedValues = intensities
    
    for( i <- 1 to times ) {
      smoothedValues = sgFilter.smooth(smoothedValues,coeffs)
    }
    
    /*
    // Re-scale value (they are underestimated after SG filter)
    // TODO: backport in mzdb-processing
    val smoothedVsUnsmoothedRatios = intensities.zip(smoothedValues).map { case (u,s) =>
      s / u
    }    
    val medianRatio = median(smoothedVsUnsmoothedRatios)
    println(medianRatio)*/
    
    val matrix = peakel.dataMatrix.copy(intensityValues = smoothedValues)
    peakel.copy( dataMatrix = matrix )
  }
}