package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.ISeqDatabaseProvider
import scala.collection.Seq
import com.weiglewilczek.slf4s.Logging
import javax.persistence.EntityManager
import fr.proline.core.om.model.msi.SeqDatabase

class ORMSeqDatabaseProvider (val em:EntityManager ) extends ISeqDatabaseProvider  with Logging {

  def getSeqDatabases(seqDBIds: Seq[Int]): Array[Option[SeqDatabase]] = { null }

  def getSeqDatabase( seqDBName: String,fastaPath : String ): Option[SeqDatabase] = {
    return  None    
  }
  
}