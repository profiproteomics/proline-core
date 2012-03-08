package fr.proline.core.om.builder

import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide



object PeptideBuilder {

  def buildPeptide( seq: String, locatedPtms: Option[Array[LocatedPtm]], calcMass: Double) : Peptide = {
    
    /*var sb:StringBuilder = new StringBuilder()
    
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
    }*/
    var ptmString = locatedPtms match {
      case None => ""
      case _ => Peptide.makePtmString( locatedPtms.get.toList )
    }
    
    new Peptide( id = Peptide.generateNewId,
                 sequence = seq,
                 ptmString = ptmString,
                 ptms = locatedPtms.getOrElse(null),
                 calculatedMass = calcMass
                )
    
  }
}