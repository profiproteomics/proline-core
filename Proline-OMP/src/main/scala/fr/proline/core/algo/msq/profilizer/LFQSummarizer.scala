package fr.proline.core.algo.msq.profilizer

import org.apache.commons.math3.util.CombinatoricsUtils
import scala.collection.mutable.ArrayBuffer
import fr.profi.util.math.RatioFitting
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.SingularValueDecomposition
import com.typesafe.scalalogging.LazyLogging

object LFQSummarizer extends LazyLogging {

  def summarize(abundanceMatrix: Array[Array[Float]], minAbundances: Array[Float]): Array[Float] = {
    RatioFitting.fit(abundanceMatrix, minAbundances)
  }

  def summarize(abundanceMatrix: Array[Array[Float]]): Array[Float] = {
    RatioFitting.fitWithoutImputation(abundanceMatrix)
  }

}