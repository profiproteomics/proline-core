package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide


trait IPeptideProvider {
  
  /**
   *  Get Peptides (wrapped in Option) with specified Ids.
   *  If no Peptides is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[Peptide] in the same order as their specified ids.
   *  @param peptideIds: Sequence of ids of Peptide to search for
   *  @return Array of Option[Peptide] corresponding to found Peptide
   */
  def getPeptides( peptideIds: Seq[Int] ): Array[Option[Peptide]]
  
  /**
   *  Get Peptides (wrapped in Option) with specified Id.
   *  If no Peptide is defined for specified id, Option.None will be returned.
   *  
   *  @param peptideId: id of Peptide to search for
   *  @return Option[Peptide] corresponding to found Peptide
   */
  def getPeptide( peptideId:Int ): Option[Peptide] = { getPeptides( Array(peptideId) )(0) }
 
  
    /**
   *  Get Peptides (wrapped in Option) with specified sequence and LocatedPtms.
   *  If no Peptide is defined for specified parameters, Option.None will be returned.
   *  
   *  @param peptideSeq: sequence of Peptide to search for
   *  @param pepPtms: Array of LocatedPtm of Peptide to search for
   *  @return Option[Peptide] corresponding to found Peptide
   */
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm]) : Option[Peptide] 
}