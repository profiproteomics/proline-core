package fr.proline.core.om.provider.msi.impl

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

import com.typesafe.scalalogging.LazyLogging

import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.{ LocatedPtm, Peptide }
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.util.PeptidesOMConverterUtil
import fr.proline.core.orm.msi.repository.{ MsiPeptideRepository => MsiPepRepo }

/**
 * ORMPeptideProvider provides access to Peptide stored in MSI database.
 *
 * Specified EntityManager should be a MSIdb EntityManager
 */
class ORMPeptideProvider(val msiDbCtx: MsiDbConnectionContext) extends IPeptideProvider with LazyLogging {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptidesAsOptions(peptideIds: Seq[Long]): Array[Option[Peptide]] = {
    
    val foundOmPeps = new ArrayBuffer[Option[Peptide]](peptideIds.length)
    for (pepId <- peptideIds ) {
      val msiPep = msiDbCtx.getEntityManager.find(classOf[fr.proline.core.orm.msi.Peptide], pepId)
      val foundOmPep = if (msiPep == null) None else Some(converter.convertPeptideORM2OM(msiPep))
      foundOmPeps += foundOmPep
    }
    
    foundOmPeps.toArray
  }

  def getPeptides(peptideIds: Seq[Long]): Array[Peptide] = {
    this.getPeptidesAsOptions(peptideIds).filter(_.isDefined).map(_.get)
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm]): Option[Peptide] = {
    var ptmStr: String = null

    if ((pepPtms != null) && !pepPtms.isEmpty) {
      ptmStr = Peptide.makePtmString(pepPtms)
    }

    val foundORMPep = MsiPepRepo.findPeptideForSequenceAndPtmStr(msiDbCtx.getEntityManager, peptideSeq, ptmStr)

    if (foundORMPep == null) {
      None
    } else {
      Some(converter.convertPeptideORM2OM(foundORMPep))
    }

  }

}