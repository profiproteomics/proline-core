package fr.proline.core.om.provider.msi

import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.context.DatabaseConnectionContext
import scala.collection.mutable.HashMap
import fr.proline.core.om.model.msi.Peptide

trait IPeptideProvider {
  
  /**
   *  Get Peptides (wrapped in Option) with specified Ids.
   *  If no Peptides is defined for a specified id, Option.None will be returned.
   *  Returned Array will contains Option[Peptide] in the same order as their specified ids.
   *  @param peptideIds: Sequence of ids of Peptide to search for
   *  @return Array of Option[Peptide] corresponding to found Peptide
   */
  def getPeptidesAsOptions( peptideIds: Seq[Long] ): Array[Option[Peptide]]
  
  /**
   *  Get Peptides with specified Ids.
   *  @param peptideIds: Sequence of ids of Peptide to search for
   *  @return Array of Peptide corresponding to found Peptide
   */
  def getPeptides( peptideIds: Seq[Long] ): Array[Peptide]
  
  //def getPeptidesForSequences( peptideSeqs: Seq[String] ): Array[Peptide]
  
  def getPeptide( peptideId:Long ): Option[Peptide] = getPeptidesAsOptions( Array(peptideId) )(0)
  
  /**
   *  Get Peptide (wrapped in Option) with specified sequence and LocatedPtms.
   *  If no Peptide is defined for specified parameters, Option.None will be returned.
   *  
   *  @param peptideSeq: sequence of Peptide to search for
   *  @param pepPtms: Array of LocatedPtm of Peptide to search for
   *  @return Option[Peptide] corresponding to found Peptide
   */
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm] ) : Option[Peptide]
  
  def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[(String, Array[LocatedPtm])] ) : Array[Option[Peptide]]
}


object PeptideFakeProvider extends IPeptideProvider {
  
  val psDbCtx = null

  var pepByID:HashMap[Long, Peptide] = new HashMap[Long, Peptide]()
  var pepBySeqPtm:HashMap[String, Peptide] = new HashMap[String, Peptide]()
  
  /**
   * Get peptides with specified Ids if already created using this Provider  
   * or create a new fake one with specified ID + seq = "X", no ptm, calcularedMass = Double.MaxValue
   *  
   */
  def getPeptidesAsOptions(peptideIds: Seq[Long] ): Array[Option[Peptide]] = {    
    var peptidesLst = List[Option[Peptide]]()
        
	peptideIds foreach (id => {
	  if(pepByID.contains(id))
	    peptidesLst :+ pepByID.get(id)
	  else {
	    var pep =  new Peptide (id=id, sequence="X", ptmString=null, ptms=null, calculatedMass =Double.MaxValue)
		peptidesLst :+ Some(pep)
		pepByID += (id -> pep)
	  }
	})
    return peptidesLst.toArray 
  }
  
  def getPeptides(peptideIds: Seq[Long] ): Array[Peptide] = {
    this.getPeptidesAsOptions( peptideIds ).filter { _ != None }.map { _.get }
  }
  
  /**
   * Get peptide with specified sequence and ptms if already created using this Provider  
   * or create a new fake one with id=Int.MinValue, calculatedMass = Double.MaxValue and specifed properties
   *  
   */
 
  def getPeptide(peptideSeq:String, pepPtms:Array[LocatedPtm]) : Option[Peptide]  = {
    var keyBuilder :StringBuilder = new StringBuilder(peptideSeq) 
    for (ptm <- pepPtms)( keyBuilder.append(ptm.definition.names.fullName).append(ptm.seqPosition))  
      
      if(pepBySeqPtm.contains(keyBuilder.toString()))
	    return pepBySeqPtm.get(keyBuilder.toString())
	  else {
	    var pep =  new Peptide ( sequence=peptideSeq, ptms=pepPtms)		
  		pepBySeqPtm += (keyBuilder.toString() -> pep)
  		return Some(pep)
	  }
  }
   
	def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[(String, Array[LocatedPtm])]) : Array[Option[Peptide]] = {
	    var result = Array.newBuilder[Option[Peptide]]
       peptideSeqsAndPtms.foreach( entry =>  {
         result += this.getPeptide(entry._1,entry._2)
       })
       result.result
	}
	

  
}