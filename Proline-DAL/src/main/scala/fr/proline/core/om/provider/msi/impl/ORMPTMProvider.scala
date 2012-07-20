package fr.proline.core.om.provider.msi.impl

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.core.om.utils.OMConverterUtil
import fr.proline.core.orm.ps.repository.PtmRepository
import fr.proline.core.orm.ps.PtmSpecificity
import javax.persistence.EntityManager
import javax.persistence.PersistenceException

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConverters.asJavaCollectionConverter

class ORMPTMProvider (val em:EntityManager )  extends IPTMProvider with Logging {

  var ptmRepo = new PtmRepository(em) //Created by constructor

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Int]): Array[Option[PtmDefinition]] = {
    
	val converter = new OMConverterUtil()
	var foundPtmDefBuilder = Array.newBuilder[Option[PtmDefinition]]
	  	
	val ptmSpecificityORMs = em.createQuery("FROM fr.proline.core.orm.ps.PtmSpecificity ptm_specificity WHERE id IN (:ids)",
	                                          classOf[fr.proline.core.orm.ps.PtmSpecificity] )
                                          .setParameter("ids", ptmDefIds.asJavaCollection ).getResultList().toList
   
    var resultIndex =0 
	ptmDefIds.foreach( ptmDefId =>{
		// Current PtmDef not found. Store None and go to next ptmDef Id
		if(resultIndex >=ptmSpecificityORMs.length || ptmSpecificityORMs.apply(resultIndex).getId != ptmDefId){
		  foundPtmDefBuilder += None
		} else{	       
		  //Current PtmDef found in Repository. Just save and go to next ptmDef Id and found ptmSpecificity
		  foundPtmDefBuilder += Some(converter.convertPtmSpecificityORM2OM(ptmSpecificityORMs.apply(resultIndex)))
	      resultIndex+=1
		}	       
    })
	     
    if(resultIndex <= ptmSpecificityORMs.length-1){
      val msg = "Return PtmSpecificity from Repository was not stored in final result ! Some errors occured ! "
      logger.warn(msg)
      throw new Exception(msg)
    }
    
    foundPtmDefBuilder.result
  }
  
  def getPtmDefinitions(ptmDefIds: Seq[Int]): Array[PtmDefinition] = {
    this.getPtmDefinitionsAsOptions(ptmDefIds).filter( _ != None ).map( _.get )
  }

  def getPtmDefinition(ptmName: String, ptmResidu: Char, ptmLocation: PtmLocation.Location ): Option[PtmDefinition] = {
    val converter = new OMConverterUtil()
    try {
      
    	var ptmSpecificity : PtmSpecificity = null 
		if( ptmResidu.equals('\0'))
			ptmSpecificity =ptmRepo.findPtmSpecificityWithNoResiduByNameLoc(ptmName, ptmLocation.toString)
		else 
			ptmSpecificity =ptmRepo.findPtmSpecificityByNameLocResidu(ptmName,ptmLocation.toString,ptmResidu)
        
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