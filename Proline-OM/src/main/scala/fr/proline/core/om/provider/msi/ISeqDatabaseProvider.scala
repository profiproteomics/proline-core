package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.SeqDatabase


trait ISeqDatabaProvider {
  
  def getSeqDatabases( seqDBIds: Seq[Int] ): Array[Option[SeqDatabase]]
  
  def getSeqDatabase( seqDBId:Int ): Option[SeqDatabase] = { getSeqDatabases( Array(seqDBId) )(0) }
 
}