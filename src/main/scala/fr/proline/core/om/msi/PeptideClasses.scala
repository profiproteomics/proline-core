package fr.proline.core.om.msi

package PeptideClasses {
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.ListBuffer
  import scala.collection.mutable.HashMap
  import org.apache.commons.lang3.StringUtils
  import fr.proline.core.om.msi.MsQueryClasses.Ms2Query
  import fr.proline.core.om.msi.MsQueryClasses.MsQuery
  import fr.proline.core.om.msi.PtmClasses.LocatedPtm
  import fr.proline.core.om.msi.ResultSetClasses.ResultSet
  import fr.proline.core.om.msi.ResultSetClasses.ResultSummary
  import fr.proline.core.om.msi.ProteinClasses.ProteinMatch
  import fr.proline.core.om.msi.ProteinClasses.ProteinSet
  
  class Peptide ( // Required fields
                  var id: Int,
                  val sequence: String,
                  val ptmString: String,
                  val ptms: Array[LocatedPtm],
                  val calculatedMass: Double,
                  
                  // Mutable optional fields
                  var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                  ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( sequence ) && calculatedMass > 0 )
    
    /** Returns a string representing the peptide PTMs */
    lazy val readablePtmString : String = {
      
      var tmpReadablePtmString : String = null
      if( ptms != null ) {
        
        val ptmStringBuf = new ListBuffer[String]
        
        for( ptm <- ptms ) {
          
          val ptmDef = ptm.definition
          val shortName = ptmDef.names.shortName
          
          var ptmConstraint : String = ""
          if( ptm.isNTerm ) { ptmConstraint = "NTerm" }
          else if( ptm.isCTerm ) { ptmConstraint = "CTerm" }
          else { ptmConstraint = "" + ptmDef.residue + ptm.seqPosition }
          
          val ptmString = "%s (%s)".format(shortName, ptmConstraint)
          ptmStringBuf += ptmString
        }
        
        tmpReadablePtmString = ptmStringBuf.mkString("; ")
  
      }
      
      tmpReadablePtmString
      
    }
    
    /** Returns a string that can be used as a unique key for this peptide */
    lazy val uniqueKey : String = sequence + "%" + ptmString
    
  }
  
  class PeptideMatch ( // Required fields
                       var id: Int, 
                       val rank: Byte,
                       val score: Float,
                       val scoreType: String,
                       val deltaMoz: Double,
                       val isDecoy: Boolean,
                       val peptide: Peptide,
                       
                       // Immutable optional fields
                       val missedCleavage: Byte = 0,
                       val fragmentMatchesCount: Short = 0,
                       
                       val msQuery: MsQuery = null, // TODO: require ?
                       
                       // Mutable optional fields
                       var isValidated: Boolean = false, // only defined in the model
                       var resultSetId: Int = 0,    
                       
                       private var childrenIds: Array[Int] = null,
                       var children: Option[Array[PeptideMatch]] = null,
                       
                       private var bestChildId: Int = 0,
                       var bestChild : Option[PeptideMatch] = null,                                         
                       
                       var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                       ) {
    
    // Requirements
    require( rank > 0 )
    //require( scoreType == "mascot" )
    require( peptide != null )
    
    // Related objects ID getters    
   
    def getChildrenIds : Array[Int] = { if(children != null && children != None) children.get.map(_.id) else childrenIds  }
    
    def getBestChildId : Int = { if(bestChild != null && bestChild != None ) bestChild.get.id else bestChildId }     
    
    /** Returns a MS2 query object. */
    def getMs2Query: Ms2Query = { if(msQuery != null) msQuery.asInstanceOf[Ms2Query] else null }
    
  }
 
  class PeptideInstance ( // Required fields
                          var id: Int,
                          val peptide: Peptide,

                          // Immutable optional fields
                          private val peptideMatchIds: Array[Int] = null, //One of these 2 values should be specified
                          val peptideMatches: Array[PeptideMatch] = null,
                          
                          val children: Array[PeptideInstance] = null,                         
                          
                          private val unmodifiedPeptideId: Int = 0,
                          val unmodifiedPeptide: Option[Peptide] = null,
                          
                          private val unmodifiedPepInstanceId: Int = 0,
                          val unmodifiedPepInstance: Option[PeptideInstance] = null,
                          
                          // Mutable optional fields
                          var proteinMatchesCount: Int = 0,
                          var proteinSetsCount: Int = 0,
                          var selectionLevel: Byte = -1,
                          
                          var peptideSets: Array[PeptideSet] = null,
                          private var resultSummaryId: Int = 0,                          
                          
                          var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                          
                          ) {
    
    // Requirements
    require( peptide != null )
    require( (peptideMatchIds != null || peptideMatches !=null) ) 
    
    // Related objects ID getters
    def getPeptideMatchIds : Array[Int] = { if(peptideMatches != null) peptideMatches.map(_.id)  else peptideMatchIds  }
    
    def getUnmodifiedPeptideId : Int = { if(unmodifiedPeptide != null && unmodifiedPeptide != None) unmodifiedPeptide.get.id else unmodifiedPeptideId }
    
    def getUnmodifiedPeptideInstanceId : Int = { if(unmodifiedPepInstance != null && unmodifiedPepInstance != None) unmodifiedPepInstance.get.id else unmodifiedPepInstanceId }
    
    
    /** Returns a true if the sequence is specific to a protein set. */
    def isProteinSetSpecific: Boolean = { proteinSetsCount == 1 }
    
    /** Returns a true if the sequence is specific to a protein match. */
    def isProteinMatchSpecific: Boolean = { proteinMatchesCount == 1 }
    
    /** Returns a true if the sequence is specific to a protein match. */
    def getPeptideMatchesCount: Int = {  peptideMatchIds.length }
  
  }
  
  class PeptideSetItem (
                     // Required fields
                     var id: Int,
                     var isBestPeptideSet: Boolean,
                     var selectionLevel: Byte,
                     var peptideSet: PeptideSet,                                         
                     var peptideInstance: PeptideInstance,
                     
                     // Mutable optional fields
                     private var resultSummaryId: Int = 0,
                     
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
    
     require( peptideSet != null  && peptideInstance!= null)
  
  }
  
  class PeptideSet ( // Required fields
                     var id: Int,
                     val items: Array[PeptideSetItem],
                     val peptideMatchesCount: Int,
                     val isSubset: Boolean,
                     
                     // Immutable optional fields                                                              
                     private val resultSummaryId: Int = 0,
                     
                     // Mutable optional fields
                     val proteinSet: ProteinSet = null,
                     
                     private var strictSubsetIds: Array[Int] = null,
                     var strictSubsets: Option[Array[PeptideSet]] = null,
                     
                     private var subsumableSubsetIds: Array[Int] = null,
                     var subsumableSubsets: Option[Array[PeptideSet]] = null,
                     
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
    
    // Requirements
    require( items != null )
    require( peptideMatchesCount >= items.length )
    
    // Related objects ID getters    
    def getStrictSubsetIds : Array[Int] = { if(strictSubsets != null && strictSubsets != None) strictSubsets.get.map(_.id)  else strictSubsetIds  }
      
    def getSubsumableSubsetIds : Array[Int] = { if(subsumableSubsets != null && subsumableSubsets != None) subsumableSubsets.get.map(_.id)  else subsumableSubsetIds  }
    
    def getPeptideMatchIds: Array[Int] = {
        
      val peptideMatchIds = new ArrayBuffer[Int]()  
      for (pepSet <- items) {
        for (pepMatchId <- pepSet.peptideInstance.getPeptideMatchIds) {
        	if(!peptideMatchIds.contains(pepMatchId))
        		peptideMatchIds += pepMatchId
        }
      }
      
      peptideMatchIds.toArray
  
    }
    
//    def getItemByPepInstanceId: Map[Int, PeptideSetItem] = {
//      
//      val tmpItemByPepInstanceId = Map() ++ items.map { item => ( item.getPeptideInstanceId -> item ) }
//      
//      // Alternatives syntax :
//      // Two traversals
//      // val itemByPepInstanceId1 = items.map( item => (item.peptideInstanceId, item) ).toMap
//      
//      // Two traversals
//      //val itemByPepInstanceId2 = Map( items.map( {item => (item.peptideInstanceId, item)} ) : _* )
//      
//      // One traversal but mutable Map
//      //var itemByPepInstanceId3 = new collection.mutable.HashMap[Int, PeptideSetItem]
//      //items foreach { item => itemByPepInstanceId2 put( item.peptideInstanceId, item ) }
//      
//      // One traversal but more verbose
//      //val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,PeptideSetItem]
//      //for( item <- items ) { mapBuilder += ( item.peptideInstanceId -> item ) }
//      //itemByPepInstanceId4 = mapBuilder.result()
//      
//      if( tmpItemByPepInstanceId.size != items.length ) throw new Exception( "duplicated peptide instance id in the list of peptide set items" )
//
//      tmpItemByPepInstanceId
//  
//    }
  
  }

}