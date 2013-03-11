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
import fr.proline.context.DatabaseConnectionContext

/**
 * ORMPeptideProvider provides access to Peptide stored in PS database.
 *
 * Specified EntityManager should be a PSdb EntityManager
 */
class ORMPeptideProvider(val psDbCtx: DatabaseConnectionContext) extends IPeptideProvider with Logging {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptidesAsOptions(peptideIds: Seq[Int]): Array[Option[Peptide]] = {
    var foundOMPepBuilder = Array.newBuilder[Option[Peptide]]
    peptideIds foreach (id => {
      val psPep = psDbCtx.getEntityManager.find(classOf[fr.proline.core.orm.ps.Peptide], id);
      if (psPep != null) {
        foundOMPepBuilder += Some(converter.convertPeptidePsORM2OM(psPep))
      } else
        foundOMPepBuilder += None
    })
    return foundOMPepBuilder.result()
  }

  def getPeptides(peptideIds: Seq[Int]): Array[Peptide] = {
    this.getPeptidesAsOptions(peptideIds).filter(_ != None).map(_.get)
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm]): Option[Peptide] = {
    if (pepPtms == null || pepPtms.isEmpty) {
      try {
        val foundORMPep = pepRepo.findPeptideForSequenceAndPtmStr(psDbCtx.getEntityManager, peptideSeq, null)
        return if (foundORMPep != null) Some(converter.convertPeptidePsORM2OM(foundORMPep)) else None

      } catch {

        case pEx: PersistenceException => {
          logger.error("Error while retrieving Peptide from PS Db", pEx)

          return None
        }

      }
      return None
    } else {
      val ptmStr = Peptide.makePtmString(pepPtms)

      try {
        val foundORMPep = pepRepo.findPeptideForSequenceAndPtmStr(psDbCtx.getEntityManager, peptideSeq, ptmStr)
        return if (foundORMPep != null) Some(converter.convertPeptidePsORM2OM(foundORMPep)) else None
      } catch {

        case pEx: PersistenceException => {
          logger.error("Error while retrieving Peptide from PS Db", pEx)

          return None
        }

      }
      return None
    }
  }

  def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[Pair[String, Array[LocatedPtm]]]): Array[Option[Peptide]] = {
    var result = Array.newBuilder[Option[Peptide]]
    peptideSeqsAndPtms.foreach(entry => {
      result += this.getPeptide(entry._1, entry._2)
    })
    result.result
  }

}