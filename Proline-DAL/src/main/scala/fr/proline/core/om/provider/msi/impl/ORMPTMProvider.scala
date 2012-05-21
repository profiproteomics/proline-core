package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPTMProvider
import scala.collection.Seq
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.orm.ps.repository.PtmRepository
import javax.persistence.EntityManager
import fr.proline.core.om.utils.OMConverterUtil

class ORMPTMProvider (val em:EntityManager )  extends IPTMProvider {

  var ptmRepo : PtmRepository = new PtmRepository(em) //Created by constructor
 

  def getPtmDefinitions(ptmDefIds: Seq[Int]): Array[Option[PtmDefinition]] = { 
	val converter : OMConverterUtil= new OMConverterUtil()
    var founfPtmDefBuilder = Array.newBuilder[Option[PtmDefinition]]
    ptmDefIds foreach (id => {
      val ptmSpecificityORM : fr.proline.core.orm.ps.PtmSpecificity = em.find(classOf[fr.proline.core.orm.ps.PtmSpecificity], id)
			if(ptmSpecificityORM != null){
				founfPtmDefBuilder += Some(converter.convertPtmSpecificityORM2OM(ptmSpecificityORM))
			} else
				founfPtmDefBuilder += None		
    })
    
    founfPtmDefBuilder.result
  }

  def getPtmDefinition(ptmName: String, ptmResidu: Char, ptmLocation: String): Option[PtmDefinition] = { throw new Exception("NYI")}

  def getPtmId( shortName: String):  Option[Int] = {  throw new Exception("NYI") }

}