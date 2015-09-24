package fr.proline.core.om.provider.msi.impl

import scala.Array.canBuildFrom
import scala.collection.Seq

import com.typesafe.scalalogging.LazyLogging

import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.msi.{ LocatedPtm, Peptide }
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.util.PeptidesOMConverterUtil
import fr.proline.core.orm.ps.repository.{ PsPeptideRepository => pepRepo }

/**
 * ORMPeptideProvider provides access to Peptide stored in PS database.
 *
 * Specified EntityManager should be a PSdb EntityManager
 */
class ORMPeptideProvider(val psDbCtx: DatabaseConnectionContext) extends IPeptideProvider with LazyLogging {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptidesAsOptions(peptideIds: Seq[Long]): Array[Option[Peptide]] = {
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

  def getPeptides(peptideIds: Seq[Long]): Array[Peptide] = {
    this.getPeptidesAsOptions(peptideIds).filter(_.isDefined).map(_.get)
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm]): Option[Peptide] = {
    var ptmStr: String = null

    if ((pepPtms != null) && !pepPtms.isEmpty) {
      ptmStr = Peptide.makePtmString(pepPtms)
    }

    val foundORMPep = pepRepo.findPeptideForSequenceAndPtmStr(psDbCtx.getEntityManager, peptideSeq, ptmStr)

    if (foundORMPep == null) {
      None
    } else {
      Some(converter.convertPeptidePsORM2OM(foundORMPep))
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