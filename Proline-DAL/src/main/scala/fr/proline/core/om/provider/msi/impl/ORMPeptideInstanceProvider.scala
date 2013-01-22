package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import scala.collection.Seq
import javax.persistence.EntityManager
import fr.proline.core.orm.msi.repository.ProteinSetRepositorty
import fr.proline.core.om.model.msi.PeptideInstance
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.utils.PeptidesOMConverterUtil
import scala.collection.mutable.ArrayBuilder
import fr.proline.repository.DatabaseContext

class ORMPeptideInstanceProvider( val msiDbCtx: DatabaseContext, val psDbCtx: DatabaseContext = null ) extends IPeptideInstanceProvider {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptideInstancesAsOptions(pepInstIds: Seq[Int]): Array[Option[PeptideInstance]] = {

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

  def getPeptideInstances(pepInstIds: Seq[Int] ): Array[PeptideInstance] = {
    this.getPeptideInstancesAsOptions(pepInstIds).filter(_ != None).map(_.get)
  }

  def getResultSummariesPeptideInstances(resultSummaryIds: Seq[Int] ): Array[PeptideInstance] = { throw new Exception("NYI") }

}