package fr.proline.core.om.provider.msi.impl

import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import fr.proline.core.om.util.PeptidesOMConverterUtil

import scala.collection.Seq

class ORMPeptideInstanceProvider( val msiDbCtx: MsiDbConnectionContext) extends IPeptideInstanceProvider {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptideInstancesAsOptions(pepInstIds: Seq[Long]): Array[Option[PeptideInstance]] = {

    var foundPepInstBuilder = Array.newBuilder[Option[PeptideInstance]]
    pepInstIds foreach (id => {
      val pepInstance: fr.proline.core.orm.msi.PeptideInstance = msiDbCtx.getEntityManager.find(classOf[fr.proline.core.orm.msi.PeptideInstance], id)
      if (pepInstance != null) {
        foundPepInstBuilder += Some(converter.convertPeptideInstanceORM2OM(pepInstance, true, msiDbCtx.getEntityManager))
      } else
        foundPepInstBuilder += None
    })
    return foundPepInstBuilder.result()
  }

  def getPeptideInstances(pepInstIds: Seq[Long] ): Array[PeptideInstance] = {
    this.getPeptideInstancesAsOptions(pepInstIds).filter(_.isDefined).map(_.get)
  }

  def getResultSummariesPeptideInstances(resultSummaryIds: Seq[Long] ): Array[PeptideInstance] = { throw new Exception("NYI") }

}