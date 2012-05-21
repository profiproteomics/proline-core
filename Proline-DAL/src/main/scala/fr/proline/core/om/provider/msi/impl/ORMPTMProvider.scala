package fr.proline.core.om.provider.msi.impl

import fr.proline.core.om.provider.msi.IPTMProvider
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConverters.asJavaCollectionConverter
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.orm.ps.repository.PtmRepository
import javax.persistence.EntityManager
import fr.proline.core.om.utils.OMConverterUtil

class ORMPTMProvider (val em:EntityManager )  extends IPTMProvider {

  var ptmRepo = new PtmRepository(em) //Created by constructor

  def getPtmDefinitions(ptmDefIds: Seq[Int]): Array[Option[PtmDefinition]] = {
    
	  val converter = new OMConverterUtil()
    var foundPtmDefBuilder = Array.newBuilder[Option[PtmDefinition]]
	  
	  // TODO: find a better syntax
	  val ptmSpecificityORMs = em.createQuery("FROM fr.proline.core.orm.ps.PtmSpecificity ptm_specificity WHERE id IN (:ids)",
	                                          classOf[fr.proline.core.orm.ps.PtmSpecificity] )
	                             .setParameter("ids", ptmDefIds.asJavaCollection ).getResultList().toList
	  
	  ptmSpecificityORMs.foreach ( ptmSpecificityORM => {
      if(ptmSpecificityORM != null){
        foundPtmDefBuilder += Some(converter.convertPtmSpecificityORM2OM(ptmSpecificityORM))
      } else
        foundPtmDefBuilder += None
	  } )
	  
    /*ptmDefIds foreach (id => {
      val ptmSpecificityORM : fr.proline.core.orm.ps.PtmSpecificity = em.find(classOf[fr.proline.core.orm.ps.PtmSpecificity], id)
			if(ptmSpecificityORM != null){
				founfPtmDefBuilder += Some(converter.convertPtmSpecificityORM2OM(ptmSpecificityORM))
			} else
				founfPtmDefBuilder += None		
    })*/
    
    foundPtmDefBuilder.result
  }

  def getPtmDefinition(ptmName: String, ptmResidue: Char, ptmLocation: String): Option[PtmDefinition] = { throw new Exception("NYI")}

  def getPtmId( shortName: String):  Option[Int] = {  throw new Exception("NYI") }

}