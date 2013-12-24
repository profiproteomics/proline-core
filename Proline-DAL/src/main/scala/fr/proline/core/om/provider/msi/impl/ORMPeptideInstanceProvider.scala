package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import scala.collection.Seq
import javax.persistence.EntityManager
import fr.proline.core.orm.msi.repository.ProteinSetRepositorty
import fr.proline.core.om.model.msi.PeptideInstance
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.utils.PeptidesOMConverterUtil
import scala.collection.mutable.ArrayBuilder
import fr.proline.context.DatabaseConnectionContext

class ORMPeptideInstanceProvider( val msiDbCtx: DatabaseConnectionContext, val psDbCtx: DatabaseConnectionContext = null ) extends IPeptideInstanceProvider {

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