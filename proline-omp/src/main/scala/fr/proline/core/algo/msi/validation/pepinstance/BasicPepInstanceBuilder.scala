package fr.proline.core.algo.msi.validation.pepinstance

import java.math.{MathContext, RoundingMode}

import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.algo.msi.inference.ParsimoniousProteinSetInferer
import fr.proline.core.algo.msi.validation.IPeptideInstanceBuilder
import fr.proline.core.om.model.msi.{PeptideInstance, PeptideInstanceProperties, PeptideMatch, ScoringProperties}


object BasicPepInstanceBuilder {

  def log10(bd: BigDecimal, dp: Int): BigDecimal = {
    var b = bd
    val NUM_OF_DIGITS = dp + 2 // need to add one to get the right number of dp
    //  and then add one again to get the next number
    //  so I can round it correctly.
    val mc = new java.math.MathContext(NUM_OF_DIGITS, RoundingMode.HALF_EVEN)
    //special conditions:
    // log(-x) -> exception
    // log(1) == 0 exactly;
    // log of a number lessthan one = -log(1/x)
    if (b.signum <= 0) throw new ArithmeticException("log of a negative number! (or zero)")
    else if (b.compare(BigDecimal(1)) == 0) return BigDecimal(0)
    else if (b.compare(BigDecimal(1)) < 0) return -log10((BigDecimal(1)).bigDecimal.divide(b.bigDecimal, mc), dp)
    val sb = new StringBuffer
    //number of digits on the left of the decimal point
    var leftDigits = b.precision - b.scale
    //so, the first digits of the log10 are:
    sb.append(leftDigits - 1).append(".")
    //this is the algorithm outlined in the webpage
    var n = 0
    while ( {
      n < NUM_OF_DIGITS
    }) {
      b = b.bigDecimal.movePointLeft(leftDigits - 1).pow(10, mc)
      leftDigits = b.precision - b.scale
      sb.append(leftDigits - 1)
      n += 1
    }
    var ans = BigDecimal(sb.toString)
    //Round the number to the correct number of decimal places.
    ans = ans.round(new MathContext(ans.precision - ans.scale + dp, RoundingMode.HALF_EVEN))
    ans
  }

}
class BasicPepInstanceBuilder extends IPeptideInstanceBuilder with LazyLogging {

  def buildPeptideInstance(pepMatchGroup: Array[PeptideMatch], resultSummaryId: Long): PeptideInstance = {

    val pepMatchIds = pepMatchGroup.map( _.id )

    // Build peptide instance
    // VDS: in order to ensure always same  best Peptide math use score AND deltaMoz
    val bestPepMatch = PeptideMatch.getBestOnScoreDeltaMoZ(pepMatchGroup)

    val pvalue = 1.0d - (1.0d - BigDecimal(math.pow(10, -bestPepMatch.score/10.0))).pow(pepMatchGroup.length)
    val score = (-10.0 * BasicPepInstanceBuilder.log10(pvalue, pvalue.scale)).doubleValue()

    val pepInstProps = PeptideInstanceProperties(
      Some(ScoringProperties(
        pValue = pvalue.doubleValue(),
        score = score,
        scoreType = Some("Dunn-Sidak corrected"))))

     new PeptideInstance(
      id = PeptideInstance.generateNewId(),
      peptide = bestPepMatch.peptide,
      peptideMatchIds = pepMatchIds,
      bestPeptideMatchId = bestPepMatch.id,
      peptideMatches = pepMatchGroup,
      totalLeavesMatchCount = -1,
      properties = Some(pepInstProps),
      //peptideMatchPropertiesById = peptideMatchPropertiesById,
      resultSummaryId = resultSummaryId
    )
  }
}
