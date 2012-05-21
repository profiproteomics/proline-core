package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import scala.collection.Seq
import javax.persistence.EntityManager
import fr.proline.core.orm.msi.repository.ProteinSetRepositorty
import fr.proline.core.om.model.msi.PeptideInstance
import scala.collection.mutable.ArrayBuffer
import fr.proline.core.om.utils.OMConverterUtil
import scala.collection.mutable.ArrayBuilder

class ORMPeptideInstanceProvider (val em:EntityManager ) extends IPeptideInstanceProvider {
  
	var protSetRepo : ProteinSetRepositorty = new ProteinSetRepositorty(em) //Created by constructor
 
	def getPeptideInstances(pepInstIds: Seq[Int] ): Array[Option[PeptideInstance]] = { 
	    val converter : OMConverterUtil= new OMConverterUtil()
	    
		var foundPepInstBuilder =Array.newBuilder[Option[PeptideInstance]]
		pepInstIds foreach (id => {		
			val pepInstance : fr.proline.core.orm.msi.PeptideInstance = em.find(classOf[fr.proline.core.orm.msi.PeptideInstance], id)
			if(pepInstance != null){
				foundPepInstBuilder += Some(converter.convertPeptideInstanceORM2OM(pepInstance, true, protSetRepo.getEntityManager()))
			} else
				foundPepInstBuilder += None				
		})
		return foundPepInstBuilder.result()
  }

  def getResultSummariesPeptideInstances(resultSummaryIds: Seq[Int]):  Array[Option[PeptideInstance]] = { null }

}