package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.SeqDatabase
import fr.proline.context.DatabaseConnectionContext

trait ISeqDatabaseProvider {
  
  /**
   * Get SeqDatabases (wrapped in Option) with specified Ids.
   *  If no SeqDatabases is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[SeqDatabase] in the same order as their specified ids.
   *  
   *  @param seqDBIds: Sequence of ids of SeqDatabase to search for
   *  @return Array of Option[SeqDatabase] corresponding to found SeqDatabases
   */
  def getSeqDatabasesAsOptions( seqDBIds: Seq[Long] ): Array[Option[SeqDatabase]]
  
  /**
   * Get SeqDatabases with specified Ids.
   *  
   *  @param seqDBIds: Sequence of ids of SeqDatabase to search for
   *  @return Array of SeqDatabase corresponding to found SeqDatabases
   */
  def getSeqDatabases( seqDBIds: Seq[Long] ): Array[SeqDatabase]
  
  /**
   * Get SeqDatabase (wrapped in Option) with specified Id.
   *  If no SeqDatabase is defined for specified id, Option.None will be returned.
   *  
   *  @param seqDBId: id of SeqDatabase to search for
   *  @return Option[SeqDatabase] corresponding to found SeqDatabase
   */
  def getSeqDatabase( seqDBId:Long ): Option[SeqDatabase] = { getSeqDatabasesAsOptions( Array(seqDBId) )(0) }
 
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

object SeqDbFakeProvider extends ISeqDatabaseProvider {
  
  val pdiDbCtx = null
  
  def getSeqDatabasesAsOptions(seqDBIds: Seq[Long]): Array[Option[SeqDatabase]] = { 
	  var result = new Array[Option[SeqDatabase]](1)
	  result(0) = None
	  result
	}
	
  def getSeqDatabases(seqDBIds: Seq[Long]): Array[SeqDatabase] = { 
    this.getSeqDatabasesAsOptions(seqDBIds).filter( _ != None ).map( _.get )
  }
	
  def getSeqDatabase( seqDBName: String,fastaPath : String ): Option[SeqDatabase] = {
	Some(
	  new SeqDatabase(
	    id = SeqDatabase.generateNewId,
		name = seqDBName,
		filePath = fastaPath,
		sequencesCount =0,
		releaseDate= new java.util.Date,
		version = ""
	  )
	)
  }
  
}