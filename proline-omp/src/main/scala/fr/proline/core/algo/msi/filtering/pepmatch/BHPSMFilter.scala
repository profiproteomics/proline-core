package fr.proline.core.algo.msi.filtering.pepmatch

import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.primitives.toFloat
import fr.proline.core.algo.msi.filtering.{FilterPropertyKeys, IPeptideMatchFilter, PepMatchFilterParams, PeptideMatchFiltering}
import fr.proline.core.om.model.msi.{Ms2Query, PeptideMatch}
import org.apfloat.{Apfloat, ApfloatMath}

import scala.collection.mutable.HashMap

object BHFilter {

  def adjustPValues(pvalues: Array[Double] ): Array[Double] = {
    // order the pvalues.
    val withIndices = pvalues.zipWithIndex
    val sorted = withIndices.sortBy(_._1)
    val indices = sorted.map(_._2)
    val orderedPValues = sorted.map(_._1)

    // iterate through all p-values:  largest to smallest
    val m = pvalues.length
    var adjustedPvalues = Array.ofDim[Double](m)

    for( i <- (m - 1) to 0 by -1) {
      if (i == m - 1) adjustedPvalues(i) = orderedPValues(i)
      else {
        val unadjustedPvalue = orderedPValues(i)
        val divideByM = i + 1
        val left = adjustedPvalues(i + 1)
        val right = (m / divideByM.toDouble) * unadjustedPvalue
        adjustedPvalues(i) = Math.min(left, right)
      }
    }
    val reorderedAdjustedPValues = Array.ofDim[Double](m)
    for(i <- 0 to orderedPValues.length-1) {
      reorderedAdjustedPValues(indices(i)) = adjustedPvalues(i)
    }
    reorderedAdjustedPValues
  }
}


class BHPSMFilter(var adjPValueTreshold: Float = 0.01f) extends IPeptideMatchFilter with LazyLogging {

  val filterParameter = PepMatchFilterParams.BH_AJUSTED_PVALUE.toString
  val filterDescription = "peptide match filter based on BH adjusted pValue"

  /**
    * Validate each PeptideMatch by setting their isValidated attribute. A valid PeptideMatch is a PepTideMatch
    * whose Isotope offset used for matching is less or equal to specified threshold
    *
    *
    * @param pepMatches            All PeptideMatches
    * @param incrementalValidation If incrementalValidation is set to false,
    * PeptideMatch.isValidated will be explicitly set to true or false.
    *                              Otherwise, only excluded PeptideMatch will be changed by setting their isValidated property to false.
    * @param traceability          Specify if filter could saved information in peptideMatch properties
    *
    */
  override def filterPeptideMatches(pepMatches: Seq[PeptideMatch], incrementalValidation: Boolean, traceability: Boolean): Unit = {

    // Reset validation status if validation is not incremental
    if( !incrementalValidation ) PeptideMatchFiltering.resetPepMatchValidationStatus(pepMatches)

    val filteredPepMatches = pepMatches.filter(_.isValidated)

    val (decoyPepMatches, targetPepMatches) = filteredPepMatches.partition(_.isDecoy)

    var pValues1 = targetPepMatches.map(pepMatch => Math.pow(10, -pepMatch.score/10.0)).toArray
    var candidates = targetPepMatches.map(pepMatch => pepMatch.msQuery.properties.get.getTargetDbSearch.get.getCandidatePeptidesCount).toArray
    var queryIds = targetPepMatches.map(pepMatch => pepMatch.msQuery.initialId).toArray

    var pValues = targetPepMatches.map(pepMatch => {
      val q = pepMatch.msQuery.properties.get.getTargetDbSearch.get.getCandidatePeptidesCount
      val qp = math.max(1, math.log10(q))

      val precision = 64
      val apScore = new Apfloat(pepMatch.score, precision)
      val appValue = ApfloatMath.pow(new Apfloat(10.0, precision), apScore.divide(new Apfloat(10.0, precision)).negate())
      val apSidak = new Apfloat(1.0,precision).subtract(ApfloatMath.pow(new Apfloat(1.0, precision).subtract(appValue), new Apfloat(qp, precision)))
      apSidak.doubleValue()
    }).toArray

    var adjustedPValues = BHFilter.adjustPValues(pValues)

    for (i <- 0 to pValues.length-1) {
        targetPepMatches(i).isValidated = (adjustedPValues(i) < adjPValueTreshold)
    }

    pValues = decoyPepMatches.map(pepMatch => Math.pow(10, -pepMatch.score/10.0)).toArray
    adjustedPValues = BHFilter.adjustPValues(pValues)

    for (i <- 0 to pValues.length-1) {
      decoyPepMatches(i).isValidated = (adjustedPValues(i) < adjPValueTreshold)
    }

  }


  /**
    * Returns the Threshold value that has been set.
    */
  override def getThresholdValue(): Any = adjPValueTreshold


  override def setThresholdValue(newThr: Any): Unit = {
    adjPValueTreshold = toFloat(newThr)
  }

  /**
    * Return all properties that will be usefull to know wich kind iof filter have been applied
    * and be able to reapply it.
    *
    */
  override def getFilterProperties(): Map[String, Any] =  {
    val props = new HashMap[String, Any]
    props += (FilterPropertyKeys.THRESHOLD_VALUE -> adjPValueTreshold )
    props.toMap
  }
}
