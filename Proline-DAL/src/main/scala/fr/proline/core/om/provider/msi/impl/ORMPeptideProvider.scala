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
import fr.proline.core.orm.ps.repository.PeptideRepository

/**
 * ORMPeptideProvider provides access to Peptide stored in PS database.
 * 
 * Specified EntityManager should be a PSdb EntityManager
 */
class ORMPeptideProvider (val em:EntityManager ) extends IPeptideProvider with Logging {
  
  var pepRepo : PeptideRepository = new PeptideRepository(em) //Created by constructor
  val converter : PeptidesOMConverterUtil= new PeptidesOMConverterUtil()

  def getPeptidesAsOptions( peptideIds: Seq[Int] ): Array[Option[Peptide]] = {
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
  
  def getPeptides( peptideIds: Seq[Int] ): Array[Peptide] = {
    this.getPeptidesAsOptions(peptideIds).filter( _ != None ).map( _.get )
  }

  def getPeptide( peptideSeq: String, pepPtms: Array[LocatedPtm] ): Option[Peptide]  = {
    if(pepPtms == null || pepPtms.isEmpty ){      
    	try {
    		val foundORMPep = pepRepo.findPeptidesBySeqWoPtm(peptideSeq)
			return Some(converter.convertPeptidePsORM2OM(foundORMPep))
			
    	} catch {
  	  		case e:PersistenceException => {
  	  			logger.warn(" Error while requiering Peptide "+e.getMessage)
  	  			return None
  	  		}
    	}
    	return None
    }else  {      
      val ptmStr = Peptide.makePtmString(pepPtms)
      
	  try {
  		val foundORMPep = pepRepo.findPeptidesBySequenceAndPtmStr(peptideSeq,ptmStr)
  		return Some(converter.convertPeptidePsORM2OM(foundORMPep))
	  } catch {
  	  	case e:PersistenceException => {
  	  		logger.warn(" Error while requiering Peptide "+e.getMessage)
  	  		return None
  	  	}
	  }  	    
  	return None
    }
  }

}