package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.SeqDatabase


trait ISeqDatabaseProvider {
  
  /**
   * Get SeqDatabase (wrapped in Option) with specified Ids.
   *  If no SeqDatabases is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[SeqDatabase] in the same order as their specified ids.
   *  
   *  @param seqDBIds: Sequence of ids of SeqDatabase to search for
   *  @return Array of Option[SeqDatabase] corresponding to found SeqDatabase
   */
  def getSeqDatabaseAsOptions( seqDBIds: Seq[Int] ): Array[Option[SeqDatabase]]
  
  /**
   * Get SeqDatabase (wrapped in Option) with specified Id.
   *  If no SeqDatabase is defined for specified id, Option.None will be returned.
   *  
   *  @param seqDBId: id of SeqDatabase to search for
   *  @return Option[SeqDatabase] corresponding to found SeqDatabase
   */
  def getSeqDatabase( seqDBId:Int ): Option[SeqDatabase] = { getSeqDatabaseAsOptions( Array(seqDBId) )(0) }
 
  /**
   * Get SeqDatabase (wrapped in Option) with specified name and fasta file path.
   * If no SeqDatabases is defined for specified parameter, Option.None will be returned.
   *    
   *  @param seqDBName: Name of SeqDatabase to search for
   *  @param fastaPath: Path of the fasta file used for identification corresponding to SeqDatabase to search for
   *  @return Option[SeqDatabase] corresponding to found SeqDatabase
   */
  def getSeqDatabase( seqDBName: String,fastaPath : String ): Option[SeqDatabase]
  
}