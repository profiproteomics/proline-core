package fr.proline.core.om.util

import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.orm.msi.PeptidePtm
import  scala.collection.JavaConverters._

object OMComparatorUtil {

  /**
   * Compare the OM <code>LocatedPtm</code> with the ORM <code>PeptidePtm</code>. Comparison is done on Ptm full Name and Location. 
   * Modified <code>Peptide</code> is assumed to be the same.
   *  
   * @param omPepPtm : first peptide ptm to compare : LocatedPtm from OM  
   * @param ormPtpPtm : second peptide to compare : PeptidePtm from PS/ORM  
   * @return true if both represents the same modification on same location false otherwise. If both are null, true is returned
   * 
   */
  def comparePeptidePtm( omPepPtm: LocatedPtm, ormPtpPtm: PeptidePtm ): Boolean = {
    if( omPepPtm == null && ormPtpPtm == null) return true
    if( omPepPtm == null || ormPtpPtm == null) return false
    
    omPepPtm.definition.names.shortName.equals(ormPtpPtm.getSpecificity().getPtm().getShortName()) && 
    omPepPtm.seqPosition == ormPtpPtm.getSeqPosition()
  }
  
  /**
   * Compare both set of OM <code>LocatedPtm</code> and ORM <code>PeptidePtm</code>. Comparison is done using above compare method (comparePeptidePtm=
   * which will test if each Set contains comparable LocatedPtm ant PeptidePtm (comparison done on Ptm full Name and Location) 
   *  
   * @param omPepPtmSet: First Set of peptide ptm to compare (Set of LocatedPtm from OM)  
   * @param ormPepPtmSet : Second Set of peptide to compare (Set of PeptidePtm from PS/ORM)  
   * @return true if both Set contains same peptides ptms (based on modifications and location) false otherwise. If both are null or empty, true is returned
   * 
   */
  def comparePeptidePtmSet( omPepPtmSet: Set[LocatedPtm], ormPepPtmSet: java.util.Set[PeptidePtm] ): Boolean = {

    if( (omPepPtmSet == null || omPepPtmSet.isEmpty ) && (ormPepPtmSet == null || ormPepPtmSet.isEmpty ))
      return true
    
    if((omPepPtmSet == null || omPepPtmSet.isEmpty ) || (ormPepPtmSet == null || ormPepPtmSet.isEmpty ))
      return false
    
    if( ormPepPtmSet.size != omPepPtmSet.size )
      return false;
    
    val remainingORMPepPtmSetBuilder = scala.collection.mutable.Set.newBuilder[fr.proline.core.orm.msi.PeptidePtm] 
    remainingORMPepPtmSetBuilder ++=(ormPepPtmSet.asScala)
    val remainingORMPepPtmSet = remainingORMPepPtmSetBuilder.result
    val omPepPtmIt = omPepPtmSet.toIterator
    var omPtmFound = true
    
    while( omPepPtmIt.hasNext && omPtmFound ) {
      val nextOMPepPtm = omPepPtmIt.next()
      
      val ormPepPtmIt = ormPepPtmSet.iterator()
      omPtmFound = false
      
      // Go through PeptidePtm until corresponding LocatedPtm is found
      while( ormPepPtmIt.hasNext && !omPtmFound ) {
        val nextORMPepPtm = ormPepPtmIt.next()
        if ( comparePeptidePtm(nextOMPepPtm, nextORMPepPtm) ){
          remainingORMPepPtmSet.remove(nextORMPepPtm) // Found : Remove from remaining list 
          omPtmFound = true
        }
      } //End go through each PeptidePtm to found corresponding LocatedPtm
      
    } //End go through each LocatedPtm
    
    remainingORMPepPtmSet.isEmpty
  }
  
}
