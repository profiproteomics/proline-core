package fr.proline.core.om.builder

import fr.proline.core.om.msi.PtmClasses.LocatedPtm
import fr.proline.core.om.msi.PeptideClasses.Peptide

object PeptideBuilder {

  //TODO : create ptmString from locatedPtms
  def buildPeptide( seq : String,  locatedPtms: Array[LocatedPtm],  calcMass : Double) : Peptide = {
    var sb:StringBuilder = new StringBuilder()
    
    if(locatedPtms != null && !locatedPtms.isEmpty){
    	//To order LocatedPtms on their location on peptide seq
    	class OrdLocatedPtm(x:LocatedPtm) extends Ordered[LocatedPtm] {
    		def compare(that:LocatedPtm) = x.seqPosition-that.seqPosition
    	}
    	implicit def convert(a:LocatedPtm) = new OrdLocatedPtm(a)
    	scala.util.Sorting.quickSort(locatedPtms)
    
    	locatedPtms foreach { (lp) =>
    	sb.append(lp.seqPosition).append("(").append(lp.definition.residue).append(")")
    	}
    }
    
    val pep = new Peptide( id = Peptide.generateNewId , sequence = seq, ptmString=sb.toString(), ptms = locatedPtms, calculatedMass=calcMass)
    return pep    
    
  }
}