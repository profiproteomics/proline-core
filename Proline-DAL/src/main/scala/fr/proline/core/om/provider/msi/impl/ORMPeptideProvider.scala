package fr.proline.core.om.provider.msi.impl

import scala.collection.Seq
import scala.collection.mutable.HashSet
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions
import javax.persistence.EntityManager
import javax.persistence.PersistenceException
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.utils.OMComparatorUtil
import fr.proline.core.om.utils.PeptidesOMConverterUtil
import fr.proline.core.orm.ps.repository.{ PsPeptideRepository => pepRepo }
import fr.proline.repository.DatabaseContext

/**
 * ORMPeptideProvider provides access to Peptide stored in PS database.
 *
 * Specified EntityManager should be a PSdb EntityManager
 */
class ORMPeptideProvider() extends IPeptideProvider with Logging {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptidesAsOptions(peptideIds: Seq[Int], psDb: DatabaseContext): Array[Option[Peptide]] = {
    var foundOMPepBuilder = Array.newBuilder[Option[Peptide]]
    peptideIds foreach (id => {
      val psPep = psDb.getEntityManager.find(classOf[fr.proline.core.orm.ps.Peptide], id);
      if (psPep != null) {
        foundOMPepBuilder += Some(converter.convertPeptidePsORM2OM(psPep))
      } else
        foundOMPepBuilder += None
    })
    return foundOMPepBuilder.result()
  }

  def getPeptides(peptideIds: Seq[Int], psDb: DatabaseContext): Array[Peptide] = {
    this.getPeptidesAsOptions(peptideIds, psDb).filter(_ != None).map(_.get)
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm], psDb: DatabaseContext): Option[Peptide] = {
    if (pepPtms == null || pepPtms.isEmpty) {
      try {
        val foundORMPep = pepRepo.findPeptideForSequenceAndPtmStr(psDb.getEntityManager, peptideSeq, null)
        return if (foundORMPep != null) Some(converter.convertPeptidePsORM2OM(foundORMPep)) else None

      } catch {
        case e: PersistenceException => {
          logger.warn(" Error while requiering Peptide ", e)
          return None
        }
      }
      return None
    } else {
      val ptmStr = Peptide.makePtmString(pepPtms)

      try {
        val foundORMPep = pepRepo.findPeptideForSequenceAndPtmStr(psDb.getEntityManager, peptideSeq, ptmStr)
        return if (foundORMPep != null) Some(converter.convertPeptidePsORM2OM(foundORMPep)) else None
      } catch {
        case e: PersistenceException => {
          logger.warn(" Error while requiering Peptide ", e)
          return None
        }
      }
      return None
    }
  }

  def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[Pair[String, Array[LocatedPtm]]], psDb: DatabaseContext): Array[Option[Peptide]] = {
    var result = Array.newBuilder[Option[Peptide]]
    peptideSeqsAndPtms.foreach(entry => {
      result += this.getPeptide(entry._1, entry._2, psDb)
    })
    result.result
  }

}