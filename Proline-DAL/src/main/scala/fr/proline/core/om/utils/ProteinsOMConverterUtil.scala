package fr.proline.core.om.utils

import scala.collection.mutable.HashMap
import fr.proline.core.orm.pdi.SequenceDbInstance
import fr.proline.core.om.model.msi.SeqDatabase
import fr.proline.core.om.model.msi.Protein


/**
 * Provides method to convert Proteins and related objects from ORM to OM.
 * If specified in constructor, created objects will be stored in map( referenced by their ID) to be retrieve later if necessary.
 * 
 * @author VD225637
 *
 */ 
class ProteinsOMConverterUtil ( useCachedObject: Boolean = true ){  
  
  val seqDatabaseCache = new HashMap[Int, fr.proline.core.om.model.msi.SeqDatabase]
  val proteinCache = new HashMap[Int, fr.proline.core.om.model.msi.Protein]
  
  type PdiBioSequence = fr.proline.core.orm.pdi.BioSequence
  
  /**
   *  Convert from fr.proline.core.orm.pdi.SequenceDbInstance(ORM) to fr.proline.core.om.model.msi.SeqDatabase (OM).
   *  
   * 
   * @param pdiSedDBInstance : fr.proline.core.orm.pdi.SequenceDbInstance to convert
   * @return created SeqDatabase (with associated objects)
   */
  def convertSeqDbInstanceORM2OM( pdiSeqDBInstance: SequenceDbInstance): SeqDatabase= {
	  if(useCachedObject && seqDatabaseCache.contains( pdiSeqDBInstance.getId ) )
		  return seqDatabaseCache(pdiSeqDBInstance.getId)
	  // TODO: convert to date object
	  //val relDate = pdiSeqDBInstance.getSequenceDbRelease.getDate()
      
	  // TODO: create a SequenceDbInstance OM
	  val seqDB = new SeqDatabase(
	    id = pdiSeqDBInstance.getId,
  		name = pdiSeqDBInstance.getSequenceDbConfig.getName,
  		filePath = pdiSeqDBInstance.getFastaFilePath,
  		sequencesCount = pdiSeqDBInstance.getSequenceCount,
  		releaseDate = pdiSeqDBInstance.getCreationTimestamp,
  		version = pdiSeqDBInstance.getSequenceDbRelease.getVersion
      )
	  
	  if(useCachedObject)
		  seqDatabaseCache.put(pdiSeqDBInstance.getId(),seqDB)
      
      seqDB 
  }
  
  /**
   *  Convert from fr.proline.core.orm.pdi.BioSequence (ORM) to fr.proline.core.om.model.msi.Protein (OM).
   *  
   * 
   * @param pdiSedDBInstance : fr.proline.core.orm.pdi.SequenceDbInstance to convert
   * @return created SeqDatabase (with associated objects)
   */
  def convertPdiBioSeqORM2OM( pdiBioSequence: PdiBioSequence): Protein= {
    
	  if(useCachedObject && proteinCache.contains( pdiBioSequence.getId ) )
		  return proteinCache(pdiBioSequence.getId)
      
	  val protein = new Protein(id=pdiBioSequence.getId,
	      					sequence = pdiBioSequence.getSequence,
	      					mass = pdiBioSequence.getMass,
	      					pi = pdiBioSequence.getPi,
	      					crc64 = pdiBioSequence.getCrc64,
	      					alphabet = pdiBioSequence.getAlphabet
	      )

	  
	  if(useCachedObject)
		  proteinCache.put(pdiBioSequence.getId(),protein)
      
      protein 
  }
  

  
}