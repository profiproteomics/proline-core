package fr.proline.core.om.msi

package ResultSetClasses {

import scala.collection.mutable.HashMap
import fr.proline.core.om.msi.PeptideClasses.PeptideSet
import fr.proline.core.om.msi.PeptideClasses.PeptideInstance
import fr.proline.core.om.msi.ProteinClasses.ProteinMatch
import fr.proline.core.om.msi.PeptideClasses.PeptideMatch
import fr.proline.core.om.msi.PeptideClasses.Peptide
import fr.proline.core.om.msi.ProteinClasses.ProteinSet
  
  class ResultSet ( 
                     // Required fields                     
                     val peptides: Array[Peptide],
                     val peptideMatches: Array[PeptideMatch],
                     val proteinMatches: Array[ProteinMatch],
                     val isDecoy: Boolean,
                     val isNative: Boolean,
                     
                     // Immutable optional fields
                     
                     // Mutable optional fields
                     var id: Int = 0,
                     private var decoyResultSetId: Int = 0,
                     var decoyResultSet: ResultSet = null,
                     var msiSearchIds: Array[Int] = null,
                     // var msiSearches: Array[MsiSearch] = null, //TODO
                     var name: String = null,
                     var description: String = null,
                     var isQuantified: Boolean = false,
                     
                     var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     
                     ) {
    
    // Requirements
    require( peptides != null && peptideMatches != null & proteinMatches != null )
    
    def getDecoyResultSetId : Int = {if(decoyResultSet != null) decoyResultSet.id else decoyResultSetId }
    
    lazy val peptideById: Map[Int, Peptide] = {
      
      val tmpPeptideById = Map() ++ peptides.map { pep => ( pep.id -> pep ) }      
      if( tmpPeptideById.size != peptides.length ) 
        error( "duplicated peptide id" )

      tmpPeptideById
  
    }
    
    lazy val peptideMatchById: Map[Int, PeptideMatch] = {
      
      val tmpPeptideMatchById = Map() ++ peptideMatches.map { pepMatch => ( pepMatch.id -> pepMatch ) }      
      if( tmpPeptideMatchById.size != peptideMatches.length ) 
        error( "duplicated peptide match id" )

      tmpPeptideMatchById
  
    }
    
    lazy val proteinMatchById: Map[Int, ProteinMatch] = {
      
      val tmpProtMatchById = Map() ++ proteinMatches.map { protMatch => ( protMatch.id -> protMatch ) }      
      if( tmpProtMatchById.size != proteinMatches.length ) 
        error( "duplicated protein match id" )

      tmpProtMatchById
  
    }
  
  }
  
  class ResultSummary (
                     // Required fields
                     val peptideInstances: Array[PeptideInstance],
                     val peptideSets: Array[PeptideSet],
                     val proteinSets: Array[ProteinSet],
                     val isDecoy: Boolean,
                     val isNative: Boolean,
                     
                     // Immutable optional fields
                     
                     // Mutable optional fields
                     var id: Int = 0,
                     private var resultSetId: Int = 0,
                     var resultSet: ResultSet= null,
                     var decoyResultSummaryId: Int = 0,
                     var decoyResultSummary: ResultSummary = null,
                     var name: String = null,
                     var description: String = null,
                     var isQuantified: Boolean = false,                    
                     var validation_properties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                     ) {
    
    // Requirements
    require( peptideInstances != null && peptideSets != null & proteinSets != null )
    
    def getResultSetId : Int = {if(resultSet != null) resultSet.id else resultSetId }
    
    def getDecoyResultSummaryId : Int = {if(decoyResultSummary != null) decoyResultSummary.id else decoyResultSummaryId }
     
    lazy val peptideInstanceById: Map[Int, PeptideInstance] = {
      
      val tmpPepInstById = Map() ++ peptideInstances.map { pepInst => ( pepInst.id -> pepInst ) }      
      if( tmpPepInstById.size != peptideInstances.length ) 
        error( "duplicated peptide instance id" )

      tmpPepInstById
  
    }
    
    lazy val proteinSetById: Map[Int, ProteinSet] = {
      
      val tmpProtSetById = Map() ++ proteinSets.map { protSet => ( protSet.id -> protSet ) }      
      if( tmpProtSetById.size != proteinSets.length ) 
        error( "duplicated protein match id" )

      tmpProtSetById
  
    }
  
  }
  
}