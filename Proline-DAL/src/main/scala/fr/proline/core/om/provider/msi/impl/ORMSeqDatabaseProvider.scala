package fr.proline.core.om.provider.msi.impl

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConverters.asJavaCollectionConverter

import com.typesafe.scalalogging.slf4j.Logging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.SeqDatabase

import fr.proline.core.om.provider.msi.ISeqDatabaseProvider
import fr.proline.core.om.util.ProteinsOMConverterUtil
import fr.proline.core.orm.pdi.repository.{ PdiSeqDatabaseRepository => seqDBRepo }

class ORMSeqDatabaseProvider( val pdiDbCtx: DatabaseConnectionContext ) extends ISeqDatabaseProvider with Logging {

  val converter = new ProteinsOMConverterUtil()

  def getSeqDatabasesAsOptions(seqDBIds: Seq[Long]): Array[Option[SeqDatabase]] = {

    var foundSeqDBBuilder = Array.newBuilder[Option[SeqDatabase]]

    val seqDBORMs = pdiDbCtx.getEntityManager.createQuery("FROM fr.proline.core.orm.pdi.SequenceDbInstance WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.pdi.SequenceDbInstance])
      .setParameter("ids", seqDBIds.asJavaCollection).getResultList().toList

    var resultIndex = 0
    seqDBIds.foreach(seqDBId => {
      // Current SeqDatabase not found. Store None and go to next ptmDef Id
      if (resultIndex >= seqDBORMs.length || seqDBORMs.apply(resultIndex).getId != seqDBId) {
        foundSeqDBBuilder += None
      } else {
        //Current SeqDatabase found in Repository. Just save and go to next SeqDatabase Id and found SeqDatabase
        foundSeqDBBuilder += Some(converter.convertSeqDbInstanceORM2OM(seqDBORMs.apply(resultIndex)))
        resultIndex += 1
      }
    })

    if (resultIndex <= seqDBORMs.length - 1) {
      val msg = "SeqDatabase retrieve from Repository was not stored in final result ! Some errors occured ! "
      logger.warn(msg)
      throw new Exception(msg)
    }

    foundSeqDBBuilder.result
  }

  def getSeqDatabases(seqDBIds: Seq[Long]): Array[SeqDatabase] = {
    throw new Exception("NYI")
  }

  def getSeqDatabase(seqDBName: String, fastaPath: String): Option[SeqDatabase] = {   
    val pdiSeqdb = seqDBRepo.findSeqDbInstanceWithNameAndFile(pdiDbCtx.getEntityManager, seqDBName, fastaPath)
    if (pdiSeqdb == null)
      return None
    else {
      return Some(converter.convertSeqDbInstanceORM2OM(pdiSeqdb))
    }
  }

}