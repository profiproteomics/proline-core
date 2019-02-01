package fr.proline.core.algo.msq.profilizer

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.math.RatioFitting

object LFQSummarizer extends LazyLogging {

  def summarize(abundanceMatrix: Array[Array[Float]], minAbundances: Array[Float]): Array[Float] = {
    RatioFitting.fit(abundanceMatrix, minAbundances)
  }

  def summarize(abundanceMatrix: Array[Array[Float]]): Array[Float] = {
    RatioFitting.fitWithoutImputation(abundanceMatrix)
  }

}