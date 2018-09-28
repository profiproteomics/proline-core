package fr.proline.core.om.provider.msi
import fr.proline.core.om.model.msi.Protein
import fr.proline.core.om.model.msi.SeqDatabase
import fr.proline.context.DatabaseConnectionContext
import scala.collection.mutable.HashMap



trait IProteinProvider {
  
  /**
   * Get Protein (wrapped in Option) with specified Ids.
   *  If no Proteins is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[Protein] in the same order as their specified ids.
   *  
   *  @param protIds: Sequence of ids of Protein to search for
   *  @return Array of Option[Protein] corresponding to found Protein
   */
  def getProteinsAsOptions( protIds: Seq[Long] ): Array[Option[Protein]]
  
  /**
   * Get Protein (wrapped in Option) with specified Id.
   *  If no Proteins is defined for specified id, Option.None will be returned.
   *  
   *  @param protId: id of Protein to search for
   *  @return Option[Protein] corresponding to found Protein
   */
  def getProtein( protId:Long ): Option[Protein] = getProteinsAsOptions( Array(protId) )(0) 
  
  /**
   * Get Protein (wrapped in Option) with specified sequence.
   *  If no Proteins is defined for specified sequence, Option.None will be returned.
   *    
   *  @param seq: sequence of Protein to search for
   *  @return Option[Protein] corresponding to found Protein
   */
  def getProtein( seq:String ): Option[Protein]
  
   /**
   * Get Protein (wrapped in Option) with specified accession and belonging to specified SeqDatabase.
   *  If no Protein is defined for specified parameters, Option.None will be returned.
   *  
   *  @param accession: accession of Protein to search for
   *  @param seqDb: SeqDatabase to which searched Protein belongs to 
   *  @return Option[Protein] corresponding to found Protein
   */
  def getProtein( accession:String, seqDb: SeqDatabase ): Option[Protein]
}


/**
 * Return only no value (Option.empty) 
 */
class ProteinEmptyFakeProvider extends IProteinProvider {
  
   def getProteinsAsOptions( protIds: Seq[Long] ): Array[Option[Protein]] = {
   val retArray =  new Array[Option[Protein]](1)
	retArray.update(0, Option.empty[Protein])	
	return retArray    
  }
 
  def getProtein( seq:String): Option[Protein] =  Option.empty[Protein]
  
  def getProtein(accession:String, seqDb: SeqDatabase): Option[Protein] =  {
    return None
  }

}

/**
 * Return a Fake Protein :  sequence="AACCCMMM"
 */
object ProteinFakeProvider extends ProteinEmptyFakeProvider {
  
   
private var protByAcc:HashMap[String, Protein] = new HashMap[String, Protein]()
  
  
  override def getProtein(accession:String, seqDb: SeqDatabase): Option[Protein] =  {
    var retVal= protByAcc.get(accession.concat(seqDb.name))
    if(retVal == None){
      val p:Protein = new Protein(sequence="AACCCMMM",id = Protein.generateNewId  )
      protByAcc += accession.concat(seqDb.name) -> p
      retVal = Some(p)
    }
    retVal
  }

}