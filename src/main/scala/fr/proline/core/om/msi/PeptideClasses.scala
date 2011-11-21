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
  
  class Peptide ( // Required fields
                  var id: Int, 
                  val sequence: String, 
                  val ptmString: String, 
                  val ptms: Array[LocatedPtm], 
                  val calculatedMass: Double,
                  
                  // Mutable optional fields
                  var missedCleavage: Int = 0, 
                  var properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                  ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( sequence ) && calculatedMass > 0 )
    
    // Lazy values
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
    
    lazy val uniqueKey : String = sequence + "%" + ptmString
    
  }
  
  class PeptideMatch ( // Required fields
                       var id: Int, 
                       val rank: Int,
                       val score: Float,
                       val scoreType: String,
                       val deltaMz: Float,
                       val isDecoy: Boolean,
                       val peptide: Peptide,
                       
                       private var msQueryId: Int =0,
                       val msQuery: MsQuery = null,
                       
                       // Immutable optional fields
                       private val childrenIds: Array[Int] = null,
                       val children: Array[PeptideMatch] = null,
                       
                       private val bestChildId: Int = 0,
                       val bestChild : PeptideMatch = null, 
                       
                       val fragmentMatchCount: Int = 0,
                       
                       // Mutable optional fields
                       var isValidated: Boolean = false, // only defined in the model
                       
                       private var resultSetId: Int = 0,
                       var resultSet: ResultSet = null,
                       
                       var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                       ) {
    
    // Requirements
    require( rank > 0 && (msQueryId != 0 || msQuery !=null)  )
    
    def getMs2Query: Ms2Query = { if(msQuery != null) msQuery.asInstanceOf[Ms2Query] else null }
    
    def getMsQueryId : Int = { if(msQuery != null) msQuery.id  else  msQueryId }
    
    def getChildrenIds : Array[Int] = { if(children != null) children.map(_.id)  else childrenIds  }
    
    def getBestChildId : Int = {if(bestChild != null) bestChild.id else bestChildId }
    
    def getResultSetId : Int = {if(resultSet != null) resultSet.id else resultSetId }
    
  }
 
  class PeptideInstance ( // Required fields
                          var id: Int,
                          private val peptideId: Int = 0, //One of these 2 values should be specified
                          val peptide: Peptide = null,
                          
                          private var peptideMatchIds: Array[Int] = null, //One of these 2 values should be specified
                          var peptideMatches: Array[PeptideMatch] = null,
                          
                          // Immutable optional fields
                          val children: Array[PeptideInstance] = null,                          
                          val proteinSetIds: Array[Int] = null,
                          private val unmodifiedPeptideId: Int = 0,
                          val unmodifiedPeptide: Peptide = null,
                          private val unmodifiedPeptideInstanceId: Int = 0,                          
                          val unmodifiedPeptideInstance: PeptideInstance = null,      
                          
                          // Mutable optional fields
                          var proteinMatchCount: Int = 0,
                          var proteinSetCount: Int = 0,
                          var selectionLevel: Int = -1,                          
                          private var resultSummaryId: Int = 0,
                          var resultSummary: ResultSummary = null,
                          var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                          
                          ) {
    
    // Requirements
    require( (peptideMatchIds != null || peptideMatches !=null) && (peptideId != 0 || Peptide !=null)) 
    
    def getPeptideMatchIds : Array[Int] = { if(peptideMatches != null) peptideMatches.map(_.id)  else peptideMatchIds  }
    
    def getUnmodifiedPeptideId : Int = {if(unmodifiedPeptide != null) unmodifiedPeptide.id else unmodifiedPeptideId }
    
    def getUnmodifiedPeptideInstanceId : Int = {if(unmodifiedPeptideInstance != null) unmodifiedPeptideInstance.id else unmodifiedPeptideInstanceId }
    
    def getResultSummaryId : Int = {if(resultSummary != null) resultSummary.id else resultSummaryId }
    
    def isProteinSetSpecific: Boolean = { proteinSetCount == 1 }
    
    def isProteinMatchSpecific: Boolean = { proteinMatchCount == 1 }
    
    def getPeptideMatchesCount: Int = {  peptideMatchIds.length }
  
  }
  
  class PeptideSetItem ( // Required fields
                     var id: Int,
                     private val peptideSetId: Int =0, //One of these 2 values should be set
                     val peptideSet: PeptideSet = null,
                     
                     private var peptideInstanceId: Int =0, //One of these 2 values should be set
                     var peptideInstance: PeptideInstance = null,
                     
                     var isBestPeptideSet: Boolean,
                     var selectionLevel: Int,
                     
                     // Mutable optional fields
                     private var resultSummaryId: Int = 0,
                     var resultSummary: ResultSummary = null,
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
  
      require( (peptideSetId != 0 || peptideSet !=null ) &&  (peptideInstanceId != 0 || peptideInstance !=null ) )
  
	  def getPeptideSetId : Int = {if(peptideSet != null) peptideSet.id else peptideSetId }
	  
	  def getPeptideInstanceId : Int = {if(peptideInstance != null) peptideInstance.id else peptideInstanceId }

	  def getResultSummaryId : Int = {if(resultSummary != null) resultSummary.id else resultSummaryId }
  }
  
  class PeptideSet ( // Required fields
                     var id: Int,
                     val peptideInstances: Array[PeptideInstance],
                     val items: Array[PeptideSetItem],
                     val peptideMatchCount: Int,
                     val isSubset: Boolean,
                     
                     // Immutable optional fields
                     private val proteinMatchIds: Array[Int] = null,
                     val proteinMatches: Array[ProteinMatch] = null,
                     private val resultSummaryId: Int = 0,
                     val resultSummary: ResultSummary= null,
                     
                     // Mutable optional fields
                     var proteinSetId: Int = 0, 
                     private var strictSubsetsIds: Array[Int] = null,
                     var strictSubsets: Array[PeptideSet] = null,
                     private var subsumableSubsetsIds: Array[Int] = null,
                     var subsumableSubsets: Array[PeptideSet] = null,
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
    
    // Requirements
    require( peptideInstances != null && peptideMatchCount >= peptideInstances.length )
    
    def getProteinMatchIds : Array[Int] = { if(proteinMatches != null) proteinMatches.map(_.id)  else proteinMatchIds  }
    
    def getResultSummaryId : Int = {if(resultSummary != null) resultSummary.id else resultSummaryId }
    
    def getStrictSubsetsIds : Array[Int] = { if(strictSubsets != null) strictSubsets.map(_.id)  else strictSubsetsIds  }
      
    def getSubsumableSubsetsIds : Array[Int] = { if(subsumableSubsets != null) subsumableSubsets.map(_.id)  else subsumableSubsetsIds  }
    
    lazy val peptideMatchIds: Array[Int] = {
        
      val peptideMatchIds = new ArrayBuffer[Int]()  
      for (pepInst <- peptideInstances) for( pmId <- pepInst.getPeptideMatchIds ) peptideMatchIds += pmId
      
      peptideMatchIds.toArray
  
    }
    
    lazy val itemByPepInstanceId: Map[Int, PeptideSetItem] = {
      
      val tmpItemByPepInstanceId = Map() ++ items.map { item => ( item.getPeptideInstanceId -> item ) }
      
      // Alternatives :
      // val itemByPepInstanceId1 = items.map( item => (item.peptideInstanceId, item) ).toMap
      //var itemByPepInstanceId2 = new collection.mutable.HashMap[Int, PeptideSetItem]
      //items foreach { item => itemByPepInstanceId2 put( item.peptideInstanceId, item ) }

      //val itemByPepInstanceId3 = Map( items.map( {item => (item.peptideInstanceId, item)} ) : _* )
      
      if( tmpItemByPepInstanceId.size != items.length ) error( "duplicated peptide instance id in the list of peptide set items" )

      tmpItemByPepInstanceId
  
    }
  
  }

}