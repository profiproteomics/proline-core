package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.utils.OMConverterUtil
import fr.proline.core.orm.ps.repository.PtmRepository
import fr.proline.core.orm.ps.PtmSpecificity
import javax.persistence.EntityManager
import javax.persistence.PersistenceException
import com.weiglewilczek.slf4s.Logging



class ORMPTMProvider (val em:EntityManager )  extends IPTMProvider with Logging {

  var ptmRepo = new PtmRepository(em) //Created by constructor

  def getPtmDefinitions(ptmDefIds: Seq[Int]): Array[Option[PtmDefinition]] = {
    
	val converter = new OMConverterUtil()
	var foundPtmDefBuilder = Array.newBuilder[Option[PtmDefinition]]
	  
    ptmDefIds foreach (id => {
    	val ptmSpecificityORM : fr.proline.core.orm.ps.PtmSpecificity = em.find(classOf[fr.proline.core.orm.ps.PtmSpecificity], id)
		if(ptmSpecificityORM != null){
			foundPtmDefBuilder += Some(converter.convertPtmSpecificityORM2OM(ptmSpecificityORM))
		} else
			foundPtmDefBuilder += None		
    })
    
    foundPtmDefBuilder.result
  }

  def getPtmDefinition(ptmName: String, ptmResidu: Char, ptmLocation: PtmLocation.Location ): Option[PtmDefinition] = {
    val converter = new OMConverterUtil()
    try {
    	//VDS TEMP  TODO : Use same values as PtmLocation.Location in DB ! 
    	val locationStr = ptmLocation match {
    		case PtmLocation.ANYWHERE => "ANYWHERE"
    		case PtmLocation.ANY_C_TERM => "ANY_CTERM"
    		case PtmLocation.ANY_N_TERM => "ANY_NTERM"
    		case PtmLocation.C_TERM => "CTERM"
    		case PtmLocation.N_TERM => "NTERM"
    		case PtmLocation.PROT_C_TERM => "PROTEIN_CTERM"
    		case PtmLocation.PROT_N_TERM=> "PROTEIN_NTERM"
    	}
      
    	var ptmSpecificity : PtmSpecificity = null 
		if( ptmResidu.equals('\0'))
			ptmSpecificity =ptmRepo.findPtmSpecificityWithNoResiduByNameLoc(ptmName, locationStr)
		else 
			ptmSpecificity =ptmRepo.findPtmSpecificityByNameLocResidu(ptmName,locationStr,ptmResidu)
        
		if(ptmSpecificity == null)
			return None
		else 
			return Some(converter.convertPtmSpecificityORM2OM(ptmSpecificity))
    } catch {
	  case e:PersistenceException => {
	    logger.warn(" Error while requiering PtmSpecificity (name: "+ptmName+" residu: "+ptmResidu+" location: "+ptmLocation.toString+") : "+e.getMessage)
	    return None
	  }  
    }
  }

  def getPtmId( shortName: String):  Option[Int] = {
    try {
    	val ptm = em.createQuery("FROM Ptm ptm WHERE ptm.shortName = :shortName",  classOf[fr.proline.core.orm.ps.Ptm]).setParameter("shortName", shortName).getSingleResult
    	if(ptm == null)
			return None
		else 
		  return Some(ptm.getId) 
    } catch {
      case e:PersistenceException => {
        logger.warn(" Error while requiering Ptm "+shortName+" : "+e.getMessage)
        return None      
      }
    }
    return None 
  }

}