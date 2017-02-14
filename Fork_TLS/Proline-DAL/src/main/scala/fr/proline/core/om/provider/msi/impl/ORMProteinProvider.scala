package fr.proline.core.om.provider.msi.impl

import scala.collection.JavaConversions.{ collectionAsScalaIterable, seqAsJavaList }
import scala.collection.Seq

import com.typesafe.scalalogging.LazyLogging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.{ Protein, SeqDatabase }
import fr.proline.core.om.provider.msi.IProteinProvider
import fr.proline.core.om.util.ProteinsOMConverterUtil
import fr.proline.core.orm.pdi.BioSequence
import fr.proline.core.orm.pdi.repository.{ PdiBioSequenceRepository => bioSeqRepo }

/**
 * ORMProteinProvider provides access to Protein stored in PDI database.
 *
 * Specified EntityManager should be a PDIdb EntityManager
 */
class ORMProteinProvider(val pdiDbCtx: DatabaseConnectionContext) extends IProteinProvider with LazyLogging {

  val converter = new ProteinsOMConverterUtil(true)

  def getProteinsAsOptions(protIds: Seq[Long]): Array[Option[Protein]] = {

    var foundOMProtBuilder = Array.newBuilder[Option[Protein]]
    val pdiBioSeqs = pdiDbCtx.getEntityManager.createQuery("FROM fr.proline.core.orm.pdi.BioSequence bioSeq WHERE id IN (:ids)",
      classOf[fr.proline.core.orm.pdi.BioSequence])
      .setParameter("ids", seqAsJavaList(protIds)).getResultList().toList

    var resultIndex = 0
    protIds.foreach(protId => {
      // Current Prot not found. Store None and go to next prot Id
      if (resultIndex >= pdiBioSeqs.length || pdiBioSeqs.apply(resultIndex).getId != protId) {
        foundOMProtBuilder += None
      } else {
        //Current Prot found in Repository. Just save and go to next Prot Id and found Prot
        foundOMProtBuilder += Some(converter.convertPdiBioSeqORM2OM(pdiBioSeqs.apply(resultIndex)))
        resultIndex += 1
      }
    })

    if (resultIndex <= pdiBioSeqs.length - 1) {
      val msg = "Returned Proteins from Repository was not stored in final result ! Some errors occured ! "
      logger.warn(msg)
      throw new Exception(msg)
    }
    foundOMProtBuilder.result
  }

  def getProtein(seq: String): Option[Protein] = {
    try {
      val bioSeq: BioSequence = pdiDbCtx.getEntityManager.createQuery("SELECT bs FROM fr.proline.core.orm.pdi.BioSequence bs where bs.sequence = :seq", classOf[fr.proline.core.orm.pdi.BioSequence])
        .setParameter("seq", seq).getSingleResult()
      Some(converter.convertPdiBioSeqORM2OM(bioSeq))
    } catch {

      case ex: Exception => {
        logger.error("Error while retrieving BioSequence from PDI Db", ex)

        return None
      }

    }

  }

  def getProtein(accession: String, seqDb: SeqDatabase): Option[Protein] = {

    val bioSeq = bioSeqRepo.findBioSequencePerAccessionAndSeqDB(pdiDbCtx.getEntityManager, accession, seqDb.id)
    if (bioSeq == null)
      return None

    Some(converter.convertPdiBioSeqORM2OM(bioSeq))

  }

}