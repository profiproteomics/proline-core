package fr.proline.core.algo.msi.scoring
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi.{PeptideSetProperties, ResultSummary, ScoringProperties}
import net.sourceforge.jdistlib.ChiSquare
import org.apache.commons.math3.distribution.ChiSquaredDistribution

import scala.collection.mutable.ArrayBuffer

object FisherScoreUpdater {

  def _jDistLogCdf(x: Double, n: Int) : (Double, Int) = {
    val c = new ChiSquare(2*n);
    val logCdf = c.cumulative(x, false, true)
    (logCdf, 0)
  }

  def _logCdf(x: Double, n: Int) : (Double, Int) = {
    val d = new ChiSquaredDistribution(2*n);
    val p = 1.0 - d.cumulativeProbability(x)

    val logCdf = math.log(p)
    if (logCdf.isInfinity) {
      (d.logDensity(x-1), 1)
    } else {
      (logCdf, 0)
    }
  }

}

class FisherScoreUpdater extends IPeptideSetScoreUpdater with LazyLogging {

  override def updateScoreOfPeptideSets(rsm: ResultSummary, params: Any*): Unit = {
    for (peptideSet <- rsm.peptideSets) {
      val scores = peptideSet.getPeptideInstances.map(_.scoringProperties.get.score)
      val maxScore = peptideSetScore(scores)
      peptideSet.score = maxScore._1.toFloat
      peptideSet.scoreType = PepSetScoring.FISHER_SCORE.toString

      val properties = PeptideSetProperties(
        Some(ScoringProperties(
          pValue = math.pow(10, -maxScore._1/10.0),
          score = maxScore._1.toFloat,
          scoreType = if (maxScore._2 == 0) Some("exact cdf") else Some("approximated cdf"))))
      peptideSet.properties = Some(properties)
    }
  }

  def peptideSetScore(scores: Seq[Double]) : (Double, Int) = {
    val cumscores = scores.sorted.reverse.scanLeft(0.0D)(_ + _*math.log(10)/5).tail
    var proteinScores = ArrayBuffer.empty[(Double,Int)]
    var nPep = 1
    for (cumscore <- cumscores) {
      val (score, scoreType) = FisherScoreUpdater._jDistLogCdf(cumscore, nPep)
      proteinScores += ((-10.0*score/math.log(10), scoreType))
      nPep = nPep+1
    }

    proteinScores.maxBy(_._1)
  }


}
