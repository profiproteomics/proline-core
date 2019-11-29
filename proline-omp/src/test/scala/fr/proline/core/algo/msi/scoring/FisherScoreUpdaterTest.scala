package fr.proline.core.algo.msi.scoring

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.algo.msi.validation.pepinstance.BasicPepInstanceBuilder
import org.apache.commons.math3.distribution.ChiSquaredDistribution
import org.junit.Test
import org.scalatest.junit.JUnitSuite

import scala.collection.mutable.ArrayBuffer

@Test
class FisherScoreUpdaterTest extends JUnitSuite with StrictLogging {

  @Test
  def enumerateSubSetsTest() = {
    var set = Set[Int]()

    for (i <- 1 to 10) {
      set += i
    }
    val start = System.currentTimeMillis()
    val powerset = subsets(set)
    logger.info("powerset size = " + powerset.size + " in " + (System.currentTimeMillis() - start) + " ms")
  }

  @Test
  def chiSquaredTest() =  {

//    val cumscore = 73.8208780814
//    val n = 3

    val n = 8
    val cumscore = n*150

    val d = new ChiSquaredDistribution(2*n);
    logger.info("log density = "+d.logDensity(cumscore))
    var p = d.cumulativeProbability(cumscore)

    logger.info("cumulative probability = "+p)
    logger.info("log cumulative probability = "+math.log(p))

    p = 1.0 - p
    logger.info("upper tail cumulative probability = "+p)
    logger.info("upper tail  log cumulative probability = "+math.log(p))



    logger.info(" -------------------- ")
    logger.info("LogCdf "+FisherScoreUpdater._logCdf(cumscore, n))
    logger.info("jDist LogCdf "+FisherScoreUpdater._jDistLogCdf(cumscore, n))

  }

  @Test
  def peptideSetTest() = {
    val updater = new FisherScoreUpdater()

//    val scores = ArrayBuffer[Double](163.00d)
    val scores = ArrayBuffer[Double](221.02,219.08,192.86,180.11,169.15,164.40,159.02,142.36,139.27,138.13,130.58,119.42,118.91,118.74,111.56,106.64,106.51,100.54,93.17,91.29,91.24,87.01,83.60,82.29,75.78,74.71,74.42,68.97,66.08,64.56,64.37,63.55,58.01,56.95,54.17,53.24,51.77,50.99,50.20,48.67,44.53,43.76,40.17,40.12,35.06,33.70)

    val (score, scoreType) = updater.peptideSetScore(scores.toSeq)

    logger.info("score = "+score)
    logger.info("score type = "+scoreType)

  }

  @Test
  def log10Test() = {

    val bd = BigDecimal("0.9999999999268861019269869")

    val logBd = BasicPepInstanceBuilder.log10( bd, bd.scale)

    logger.info("bigdecimal log10 = "+logBd.toString())
    logger.info("math.log10 = "+math.log10(bd.doubleValue()))

  }

  @Test
  def cumSumTest() = {
    val integers: List[Int] = List(1,3,4,5)
    val partialSum1 =  integers.scanLeft(0)(_ + _*2).tail
    logger.info(""+partialSum1.mkString(","))
  }

  def subsets[T](s : Set[T]) : Set[Set[T]] =
    if (s.size == 0) Set(Set()) else {
      val tailSubsets = subsets(s.tail);
      tailSubsets ++ tailSubsets.map(_ + s.head)
    }
}
