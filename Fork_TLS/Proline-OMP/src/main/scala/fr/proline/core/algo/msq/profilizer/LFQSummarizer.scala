package fr.proline.core.algo.msq.profilizer

import org.apache.commons.math3.util.CombinatoricsUtils
import scala.collection.mutable.ArrayBuffer
import fr.profi.util.math.filteredMedian
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.SingularValueDecomposition
import com.typesafe.scalalogging.LazyLogging

object LFQSummarizer extends LazyLogging {

  def summarize(abundanceMatrix: Array[Array[Float]], minAbundances: Array[Float]): Array[Float] = {
    val valuesColumnCount = abundanceMatrix.transpose.map{col => col.count(!_.isNaN)}
    _summarize(abundanceMatrix, buildImputedRatioMatrix(abundanceMatrix, minAbundances, valuesColumnCount), valuesColumnCount)
  }

  def summarize(abundanceMatrix: Array[Array[Float]]): Array[Float] = {
    val valuesColumnCount = abundanceMatrix.transpose.map{col => col.count(!_.isNaN)}
    _summarize(abundanceMatrix, buildRatioMatrix(abundanceMatrix), valuesColumnCount)
  }

  private def _summarize(abundanceMatrix: Array[Array[Float]], r: Array[Array[Double]], valuesColumnCount: Array[Int]): Array[Float] = {
    val ratios = rowMedian(r)
    val logRatios = ratios.map { Math.log(_) }
    val naNRatiosIndexes = logRatios.zipWithIndex.filter(p => p._1.isNaN).map(_._2)
    val filteredLogRatios = logRatios.filter { !_.isNaN() }

    if (filteredLogRatios.isEmpty) {
      logger.warn("No eligible columns for ratios computation")
      return Array.fill(abundanceMatrix.head.length)(Float.NaN)
    }

    val gc = buildCoefficientMatrix(abundanceMatrix, naNRatiosIndexes)
    val solver = new SingularValueDecomposition(new Array2DRowRealMatrix(gc, false)).getSolver()
    val constants = new ArrayRealVector(filteredLogRatios, false)
    var solution = solver.solve(constants);

    var coeffs = solution.toArray()
    coeffs = coeffs.zipWithIndex.map{ case (p, i) => if (valuesColumnCount(i) == 0)  { 0.0 } else { Math.exp(p) } }
    
    val coeffSum = coeffs.foldLeft(0.0)(_ + _)
    val intensitySum = abundanceMatrix.map(_.foldLeft(0.0)((a, b) => if (b.isNaN()) { a } else { a + b })).foldLeft(0.0)((a, b) => a + b)

    coeffs = coeffs.map(_ * intensitySum / coeffSum);

    var abundances = coeffs.map(_.toFloat)
    abundances = abundances.map { p => if (p == 0.0) { Float.NaN } else { p } };
    abundances
  }
    
  private def buildImputedRatioMatrix(pep: Array[Array[Float]], min: Array[Float], valuesColumnCount: Array[Int]): Array[Array[Double]] = {
    var matrix = buildRatioMatrix(pep)
    val nonNullRatiosCount = matrix.transpose.map{col => col.count(!_.isNaN())}
    var rowIdx = 0
    for (row <- pep) {
      val nr = matrix(rowIdx)
      var index = 0
      for (i <- 0 to (row.length - 2)) {
        for (j <- (i + 1) to (row.length - 1)) {
          if (!(row(i).isNaN() && row(j).isNaN()) ) { 
              val a = if (row(j).isNaN() && (nonNullRatiosCount(index) != 0)) min(j) else row(j)
              val b = if(row(i).isNaN() && (nonNullRatiosCount(index) != 0)) min(i) else row(i)
              nr(index) = a / b
            }
          index = index + 1
        }
      }
      rowIdx+=1
    }
    matrix
  }
    
  private def buildRatioMatrix(pep: Array[Array[Float]]): Array[Array[Double]] = {
    // Compute the number of combination of 2 items among pep(0).length : n!/(n-p)!p! with p = 2  
    // previous formula was CombinatoricsUtils.factorial(pep(0).length) / (2 * CombinatoricsUtils.factorial(pep(0).length - 2))
    val length = (1 to pep(0).length - 1).view.sum
    var matrix = ArrayBuffer[Array[Double]]()

    for (row <- pep) {
      val nr = Array.fill[Double](length)(Double.NaN)
      var index = 0
      for (i <- 0 to (row.length - 2)) {
        for (j <- (i + 1) to (row.length - 1)) {
          if (!row(i).isNaN() && !row(j).isNaN() && (row(i) * row(j)) != 0) { nr(index) = row(j) / row(i) }
          index = index + 1
        }
      }
      matrix += nr
    }
    (matrix.toArray)
  }

  def buildCoefficientMatrix(pep: Array[Array[Float]], naNRatiosIndexes: Array[Int]): Array[Array[Double]] = {

    var matrix = ArrayBuffer[Array[Double]]()
    var row = 0
    for (i <- 0 to (pep(0).length - 2)) {
      for (j <- (i + 1) to (pep(0).length - 1)) {
        if (!naNRatiosIndexes.contains(row)) {
          val nr = Array.fill[Double](pep(0).length)(0)
          nr(i) = -1.0
          nr(j) = 1.0
          matrix += nr
        }
        row = row + 1
      }
    }
    matrix.toArray
  }

  private def rowMedian(values: Array[Array[Double]]): Array[Double] = {
    val medians = Array.fill[Double](values(0).length)(0)
    for (col <- 0 to values(0).length - 1) {
      val colValues = Array.fill[Double](values.length)(Double.NaN)
      var k = 0
      for (row <- values) {
        colValues(k) = row(col)
        k = k + 1
      }
      medians(col) = filteredMedian(colValues)
    }
    medians
  }

}