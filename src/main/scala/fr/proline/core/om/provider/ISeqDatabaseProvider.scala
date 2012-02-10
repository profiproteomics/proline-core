package fr.proline.core.om.provider
import fr.proline.core.om.msi.MsiSearchClasses.SeqDatabase



trait ISeqDatabaProvider {
  
  def getSeqDatabases( seqDBIds: Seq[Int] ): Array[Option[SeqDatabase]]
  
  def getSeqDatabase( seqDBId:Int ): Option[SeqDatabase] = { getSeqDatabases( Array(0) )(0) }
 
}