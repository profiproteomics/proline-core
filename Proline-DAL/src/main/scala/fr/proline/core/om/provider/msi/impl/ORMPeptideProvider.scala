package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPeptideProvider
import scala.collection.Seq
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.LocatedPtm
import scala.collection.mutable.HashSet
import javax.persistence.EntityManager
import fr.proline.core.orm.ps.repository.PeptideRepository
import fr.proline.core.om.utils.OMComparatorUtil
import fr.proline.core.om.utils.OMConverterUtil
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConversions
import javax.persistence.PersistenceException
import com.weiglewilczek.slf4s.Logging


class ORMPeptideProvider (val em:EntityManager ) extends IPeptideProvider with Logging {
  
  var pepRepo : PeptideRepository = new PeptideRepository(em) //Created by constructor
  val converter : OMConverterUtil= new OMConverterUtil()

  def getPeptides(peptideIds: Seq[Int] ): Array[Option[Peptide]] = {
	var foundOMPepBuilder = Array.newBuilder[Option[Peptide]]
	peptideIds foreach( id => {
		val psPep : fr.proline.core.orm.ps.Peptide  = em.find(classOf[fr.proline.core.orm.ps.Peptide], id);
		if(psPep != null){
			foundOMPepBuilder += Some(converter.convertPeptidePsORM2OM(psPep))
		} else 
			foundOMPepBuilder += None		
  	})
	return foundOMPepBuilder.result() 
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm]): Option[Peptide]  = {
    var ptms : HashSet[LocatedPtm] = new HashSet[LocatedPtm]
    if(pepPtms != null)
		ptms ++= pepPtms
	try {
		val foundORMPeps: scala.collection.mutable.Buffer[fr.proline.core.orm.ps.Peptide]= JavaConversions.asScalaBuffer(pepRepo.findPeptidesBySequence(peptideSeq))
		foundORMPeps foreach (nextORMPep => {
			if(OMComparatorUtil.comparePeptidePtmSet(JavaConversions.mutableSetAsJavaSet(ptms), nextORMPep.getPtms())){
				return Some(converter.convertPeptidePsORM2OM(nextORMPep))
			}
		})
	} catch {
	  case e:PersistenceException => {
	    logger.warn(" Error while requiering Peptide "+e.getMessage)
	    return None
	  }
	} 
	    
	return None        
  }

}