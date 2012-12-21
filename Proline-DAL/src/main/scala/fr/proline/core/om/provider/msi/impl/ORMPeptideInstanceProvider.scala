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

class ORMPeptideInstanceProvider() extends IPeptideInstanceProvider {

  val converter: PeptidesOMConverterUtil = new PeptidesOMConverterUtil()

  def getPeptideInstancesAsOptions(pepInstIds: Seq[Int], msiDb: DatabaseContext): Array[Option[PeptideInstance]] = {

    var foundPepInstBuilder = Array.newBuilder[Option[PeptideInstance]]
    pepInstIds foreach (id => {
      val pepInstance: fr.proline.core.orm.msi.PeptideInstance = msiDb.getEntityManager.find(classOf[fr.proline.core.orm.msi.PeptideInstance], id)
      if (pepInstance != null) {
        foundPepInstBuilder += Some(converter.convertPeptideInstanceORM2OM(pepInstance, true, msiDb.getEntityManager))
      } else
        foundPepInstBuilder += None
    })
    return foundPepInstBuilder.result()
  }

  def getPeptideInstances(pepInstIds: Seq[Int], msiDb: DatabaseContext): Array[PeptideInstance] = {
    this.getPeptideInstancesAsOptions(pepInstIds, msiDb).filter(_ != None).map(_.get)
  }

  def getResultSummariesPeptideInstances(resultSummaryIds: Seq[Int]): Array[PeptideInstance] = { throw new Exception("NYI") }

}