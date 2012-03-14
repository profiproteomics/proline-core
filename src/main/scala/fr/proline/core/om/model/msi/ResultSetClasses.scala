package fr.proline.core.om.model.msi

import scala.collection.mutable.HashMap
import fr.proline.core.utils.misc.InMemoryIdGen

object ResultSet extends InMemoryIdGen

case class ResultSet ( 
                   // Required fields
                   val peptides: Array[Peptide],
                   val peptideMatches: Array[PeptideMatch],
                   val proteinMatches: Array[ProteinMatch],
                   val isDecoy: Boolean,
                   val isNative: Boolean,
                   
                   // Immutable optional fields
                   
                   // Mutable optional fields
                   var id: Int = 0,
                   var name: String = null,
                   var description: String = null,
                   var isQuantified: Boolean = false,                    
                   
                   var msiSearch: MSISearch = null,
                  
                   private var decoyResultSetId: Int = 0,
                   var decoyResultSet: Option[ResultSet] = null,
                   
                   var properties : HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                   
                   ) {
  
  // Requirements
  require( peptides != null && peptideMatches != null & proteinMatches != null )
  
  def getDecoyResultSetId : Int = {if(decoyResultSet != null && decoyResultSet != None) decoyResultSet.get.id else decoyResultSetId }
  
  lazy val peptideById: Map[Int, Peptide] = {
    
    val tmpPeptideById = Map() ++ peptides.map { pep => ( pep.id -> pep ) }      
    if( tmpPeptideById.size != peptides.length ) 
      throw new Exception( "duplicated peptide id" )

    tmpPeptideById

  }
  
  lazy val peptideMatchById: Map[Int, PeptideMatch] = {
    
    val tmpPeptideMatchById = Map() ++ peptideMatches.map { pepMatch => ( pepMatch.id -> pepMatch ) }      
    if( tmpPeptideMatchById.size != peptideMatches.length ) 
      throw new Exception( "duplicated peptide match id" )

    tmpPeptideMatchById

  }
  
  lazy val proteinMatchById: Map[Int, ProteinMatch] = {
    
    val tmpProtMatchById = Map() ++ proteinMatches.map { protMatch => ( protMatch.id -> protMatch ) }      
    if( tmpProtMatchById.size != proteinMatches.length ) 
      throw new Exception( "duplicated protein match id" )

    tmpProtMatchById

  }

}

object ResultSummary extends InMemoryIdGen
case class ResultSummary (
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
                   var resultSet: Option[ResultSet] = null,
                   var decoyResultSummaryId: Int = 0,
                   var decoyResultSummary: Option[ResultSummary] = null,
                   var name: String = null,
                   var description: String = null,
                   var isQuantified: Boolean = false,
                   
                   var validationProperties: HashMap[String, Any] = new collection.mutable.HashMap[String, Any]
                   ) {
  
  // Requirements
  require( peptideInstances != null && proteinSets != null )
  
  def getResultSetId : Int = {if(resultSet != null && resultSet != None) resultSet.get.id else resultSetId }
  
  def getDecoyResultSummaryId : Int = { if(decoyResultSummary != null && decoyResultSummary != None) decoyResultSummary.get.id else decoyResultSummaryId }
   
  lazy val peptideInstanceById: Map[Int, PeptideInstance] = {
    
    val tmpPepInstById = Map() ++ peptideInstances.map { pepInst => ( pepInst.id -> pepInst ) }      
    if( tmpPepInstById.size != peptideInstances.length ) 
      throw new Exception( "duplicated peptide instance id" )

    tmpPepInstById

  }
  
  lazy val proteinSetById: Map[Int, ProteinSet] = {
    
    val tmpProtSetById = Map() ++ proteinSets.map { protSet => ( protSet.id -> protSet ) }      
    if( tmpProtSetById.size != proteinSets.length ) 
      throw new Exception( "duplicated protein match id" )

    tmpProtSetById

  }

}

