package fr.proline.core.algo.msi.scoring
import fr.proline.core.om.model.msi.PeptideMatch
import scala.collection.mutable.HashMap

object PTMLocConfidence {

  def computeLocalisationConfidences(peptideMatches:Seq[PeptideMatch]) : Map[PeptideMatch, Float] = {
    val confidences = new HashMap[PeptideMatch, Float]()
    
    val peptideMatchesByQueryId = peptideMatches.groupBy(_.msQueryId)
    for ((queryId, peptideMatches) <- peptideMatchesByQueryId) {
      val matches = peptideMatches.sortWith( _.score > _.score)
      val assignments = matches.filter( p => p.peptide.sequence == matches(0).peptide.sequence)
      if (assignments.length > 1) {
        confidences ++= _computeRelativeProbabilities(assignments)
      }
    }
    confidences.toMap
  }
  
  def _computeRelativeProbabilities(assignments:Seq[PeptideMatch]) : Map[PeptideMatch, Float] = {
    val confidences = new HashMap[PeptideMatch, Float]()
    val _r = (s1:Float, si:Float, md10Prob:Float) => scala.math.pow(10,md10Prob*(s1 - si)).toFloat
    var sum = 0.0f
    val s1 = assignments(0).score
    for (peptideMatch <- assignments) {
      _r(12.0f,2.3f,26.2f)
      val v = _r( -s1 , -peptideMatch.score , 0.1f )
      sum += v
      confidences(peptideMatch) = v
    }
    for ((pm, p) <- confidences) {
      confidences(pm) = p/sum
    }
    confidences.toMap		  
  }
  
}