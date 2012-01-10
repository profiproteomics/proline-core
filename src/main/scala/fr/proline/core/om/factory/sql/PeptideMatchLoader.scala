package fr.proline.core.om.factory.sql

import net.noerd.prequel.DatabaseConfig

class PeptideMatchLoader( val msiDb: DatabaseConfig, val psDb: DatabaseConfig = null ) {
  
  import fr.proline.core.om.msi.PeptideClasses._
  import fr.proline.core.om.helper.MsiDbHelper
  
  def getPeptideMatches( rsIds: Seq[Int], psDb: DatabaseConfig = psDb ): Array[PeptideMatch] = {
    if( psDb == null ) throw new IllegalArgumentException("a psDb has to be provided to the method or to the constructor")

    val pmRecords = _getPepMatchRecords( rsIds )
    
    // Load peptides
    val uniqPepIds = pmRecords map { _("peptide_id").asInstanceOf[Int] } distinct
    val peptides = new PeptideLoader(psDb).getPeptides(uniqPepIds)
    
    _getPeptideMatches( rsIds, pmRecords, peptides )
    
  }
  
  def getPeptideMatches( rsIds: Seq[Int], peptides: Array[Peptide] ): Array[PeptideMatch] = {
    
    val pmRecords = _getPepMatchRecords( rsIds )
    _getPeptideMatches( rsIds, pmRecords, peptides )
  }
  
  private def _getPepMatchRecords( rsIds: Seq[Int] ): Array[Map[String,Any]] = {
       
    var pepMatchColNames: Seq[String] = null
    
    // Execute SQL query to load peptide match records
    val pmRecords = msiDb.transaction { tx =>       
      tx.select( "SELECT * FROM peptide_match WHERE result_set_id IN (" +
                 rsIds.mkString(",") +")" ) { r => 
        
        if( pepMatchColNames == null ) { pepMatchColNames = r.columnNames }
        
        // Build the peptide match record
        pepMatchColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        
      }
    }
    
    pmRecords.toArray
    
  }  
  
  private def _getPeptideMatches( rsIds: Seq[Int], pmRecords: Seq[Map[String,Any]], peptides: Seq[Peptide] ): Array[PeptideMatch] = {
    
    // Instantiate a MSIdb helper
    val msiDbHelper = new MsiDbHelper( msiDb )
    
    // Retrieve score type map
    val scoreTypeById = msiDbHelper.getScoreTypeById
    
    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => ( pep.id -> pep ) }
    
    // Load MS queries
    val msiSearchIds = msiDbHelper.getResultSetsMsiSearchIds( rsIds )
    val msQueries = new MsQueryLoader( msiDb ).getMsQueries( msiSearchIds )
    val msQueryById = Map() ++ msQueries.map { msq => ( msq.id -> msq ) }
    
    // Load peptide matches
    val pepMatches = new Array[PeptideMatch](pmRecords.length)
    
    for( pepMatchIdx <- 0 until pmRecords.length ) {
      
      // Retrieve peptide match record
      val pepMatchRecord = pmRecords(pepMatchIdx)
      
      // Retrieve the corresponding peptide
      val pepId = pepMatchRecord("peptide_id").asInstanceOf[Int]      
      if( ! peptideById.contains(pepId) ) throw new Exception("undefined peptide with id ='"+pepId+"'")
      val peptide = peptideById(pepId)
      
      // Retrieve the corresponding MS query
      val msQuery = msQueryById( pepMatchRecord("ms_query_id").asInstanceOf[Int] )
      
      // Retrieve some vars
      val scoreType = scoreTypeById( pepMatchRecord("scoring_id").asInstanceOf[Int] )
      val isDecoy = if( pepMatchRecord("is_decoy").asInstanceOf[String] == "true" ) true else false
      
      // TODO: load serialized properties
      
      val pepMatch = new PeptideMatch( id = pepMatchRecord("id").asInstanceOf[Int],
                                       rank = pepMatchRecord("rank").asInstanceOf[Int],
                                       score = pepMatchRecord("score").asInstanceOf[Double].toFloat,
                                       scoreType = scoreType,
                                       deltaMz = pepMatchRecord("delta_moz").asInstanceOf[Double],
                                       isDecoy = isDecoy,
                                       peptide = peptide,
                                       missedCleavage = pepMatchRecord("missed_cleavage").asInstanceOf[Int],
                                       fragmentMatchesCount = pepMatchRecord("fragment_match_count").asInstanceOf[Int],
                                       msQuery = msQuery,                                       
                                       resultSetId = pepMatchRecord("result_set_id").asInstanceOf[Int]
                                      )
      
      pepMatches(pepMatchIdx) = pepMatch
      
    }
    
    pepMatches
    
  }
  
}