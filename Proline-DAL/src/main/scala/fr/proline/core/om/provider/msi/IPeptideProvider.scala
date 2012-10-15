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
  def getPeptidesAsOptions( peptideIds: Seq[Int] ): Array[Option[Peptide]]
  
  /**
   *  Get Peptides with specified Ids.
   *  @param peptideIds: Sequence of ids of Peptide to search for
   *  @return Array of Peptide corresponding to found Peptide
   */
  def getPeptides( peptideIds: Seq[Int] ): Array[Peptide]
  
  //def getPeptidesForSequences( peptideSeqs: Seq[String] ): Array[Peptide]
  
  def getPeptide( peptideId:Int ): Option[Peptide] = { getPeptidesAsOptions( Array(peptideId) )(0) }  
  
  /**
   *  Get Peptide (wrapped in Option) with specified sequence and LocatedPtms.
   *  If no Peptide is defined for specified parameters, Option.None will be returned.
   *  
   *  @param peptideSeq: sequence of Peptide to search for
   *  @param pepPtms: Array of LocatedPtm of Peptide to search for
   *  @return Option[Peptide] corresponding to found Peptide
   */
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm]) : Option[Peptide]
  
	def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[Pair[String, Array[LocatedPtm]]]) : Array[Option[Peptide]]
}