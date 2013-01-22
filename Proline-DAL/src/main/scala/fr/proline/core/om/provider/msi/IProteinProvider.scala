package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.Protein
import fr.proline.core.om.model.msi.SeqDatabase
import fr.proline.repository.DatabaseContext


trait IProteinProvider {
  
  val pdiDbCtx: DatabaseContext
  
  /**
   * Get Protein (wrapped in Option) with specified Ids.
   *  If no Proteins is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[Protein] in the same order as their specified ids.
   *  
   *  @param protIds: Sequence of ids of Protein to search for
   *  @return Array of Option[Protein] corresponding to found Protein
   */
  def getProteinsAsOptions( protIds: Seq[Int] ): Array[Option[Protein]]
  
  /**
   * Get Protein (wrapped in Option) with specified Id.
   *  If no Proteins is defined for specified id, Option.None will be returned.
   *  
   *  @param protId: id of Protein to search for
   *  @return Option[Protein] corresponding to found Protein
   */
  def getProtein( protId:Int ): Option[Protein] = getProteinsAsOptions( Array(protId) )(0) 
  
  /**
   * Get Protein (wrapped in Option) with specified sequence.
   *  If no Proteins is defined for specified sequence, Option.None will be returned.
   *    
   *  @param seq: sequence of Protein to search for
   *  @return Option[Protein] corresponding to found Protein
   */
  def getProtein( seq:String ): Option[Protein]
  
   /**
   * Get Protein (wrapped in Option) with specified accession and belonging to specified SeqDatabase.
   *  If no Protein is defined for specified parameters, Option.None will be returned.
   *  
   *  @param accession: accession of Protein to search for
   *  @param seqDb: SeqDatabase to which searched Protein belongs to 
   *  @return Option[Protein] corresponding to found Protein
   */
  def getProtein( accession:String, seqDb: SeqDatabase ): Option[Protein]
}