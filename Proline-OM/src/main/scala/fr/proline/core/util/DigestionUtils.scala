package fr.proline.core.util

import fr.proline.core.om.model.msi.EnzymeCleavage
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.model.msi.Enzyme
import scala.collection.mutable.HashMap

object DigestionUtils {
  
  // SchwanhÃ¤usser et al. Nature 473, 337â€“342 (19 May 2011) doi:10.1038/nature10098, Supp data 2
  // " Protein intensities were divided by the number of theoretically observable peptides (calculated
  // by in silico protein digestion with a PERL script, all fully tryptic peptides between 6 and 30 amino 
  // acids were counted while missed cleavages were neglected)
  def getObservablePeptidesCount(sequence: String, enzyme: Enzyme): Int = {
    val positions = 0 +: getCleavagePositions(sequence, enzyme) :+ sequence.length
    positions.sliding(2).map { a => (a(1) - a(0)) }.filter(l => (l >= 6) &&  (l <= 30)).length    
  }
  
  def getCleavagePositions(sequence: String, enzyme: Enzyme): Array[Int] = {

    var cleavagePositions = new ArrayBuffer[Int]()
    
    for (i <- 1 to sequence.length() - 2) {
       enzyme.enzymeCleavages.foreach { ec =>
         val restrictionPosition = { if (ec.site == "C-term") { +1 } else {-1} }
         if (ec.residues.contains(sequence.charAt(i)) && !ec.restrictiveResidues.getOrElse("").contains(sequence.charAt(i + restrictionPosition))) {
           val shift = if (restrictionPosition < 0) 0 else 1
           cleavagePositions += (i + shift)
         }
       }
    }
          
    cleavagePositions.toArray
  }

    
  def countMissedCleavages(sequence: String, enzymeCleavage: EnzymeCleavage): Int = {

    var missedCleavages = new ArrayBuffer[String]()

    for (i <- 0 to sequence.length() - 1) {
      if (enzymeCleavage.site == "C-term" && // if it cuts in cterm
        enzymeCleavage.residues.contains(sequence.charAt(i)) && // and current aa is a cleavage site
        i + 1 < sequence.length() && // unless it is the last aa of the sequence
        !enzymeCleavage.restrictiveResidues.getOrElse("").contains(sequence.charAt(i + 1)) // and unless it is followed by a restrictive residue
        ) {
        // then it is a missed cleavage
        missedCleavages += sequence.charAt(i) + "(" + (i + 1) + ")"
      } else if (enzymeCleavage.site == "N-term" && // if it cuts in nterm 
        i != 0 && // and current aa is not the first aa of the sequence
        enzymeCleavage.residues.contains(sequence.charAt(i)) && // and current aa is a cleavage site
        !(i + 1 < sequence.length() && enzymeCleavage.restrictiveResidues.getOrElse("").contains(sequence.charAt(i + 1))) // and unless it is followed by a restrictive residue
        ) {
        // then it is a missed cleavage
        missedCleavages += sequence.charAt(i) + "(" + (i + 1) + ")"
      }
    }

    missedCleavages.size
  }

  def getEnzymeCleavagesHintCount(
    sequence: String,
    residueBefore: Option[Char],
    residueAfter: Option[Char],
    enzyme: Enzyme): Array[EnzymeCleavage] = {

    val enzymeCleavages = new HashMap[EnzymeCleavage, Int]

    // for each enzyme cleavage, count the number of hints to determine the most probable enzyme cleavage
    enzyme.enzymeCleavages.foreach(ec => {
      var nbIndications = 0
      if (ec.site == "C-term") { // expecting cleavage site in last position (unless peptide is C-term) and in residueBefore (unless peptide is N-term)
        if (ec.residues.contains(sequence.last)) nbIndications += 1
        if (residueBefore.isDefined && ec.residues.contains(residueBefore.get)) nbIndications += 1
      } else { // N-term : expecting cleavage site in first position (unless peptide is N-term) and in residueAfter (unless peptide is C-term)
        if (ec.residues.contains(sequence.head)) nbIndications += 1
        if (residueAfter.isDefined && ec.residues.contains(residueAfter.get)) nbIndications += 1
      }
      enzymeCleavages.put(ec, nbIndications)
    })

    // return only the most probable enzyme cleavage (there may be more than one in case of ambiguity)
    val maxIndications = enzymeCleavages.maxBy(_._2)._2

    enzymeCleavages.filter(_._2 == maxIndications).keys.toArray
  }
}