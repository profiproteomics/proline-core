package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.HashMap
import com.codahale.jerkson.Json.generate

import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.IRsmStorer

private[msi] class SQLiteRsmStorer( val msiDb: SQLQueryHelper ) extends IRsmStorer {
  
  val ezDBC = this.msiDb.ezDBC
  
  def storeRsmPeptideInstances( rsm: ResultSummary ): Int = {
    
    val rsmId = rsm.id
    //val( pepInstanceIdByPepId, pepInstanceIdMap ) = ( new HashMap[Int,Int], new HashMap[Int,Int] )
    
    val insertPepInstance = ( stmt: PreparedStatementWrapper, pepInstance: PeptideInstance ) => {
      
      val pepId = pepInstance.peptide.id
      
      // Insert peptide instance
      stmt.executeWith(
            pepInstance.peptideMatchesCount,
            pepInstance.proteinMatchesCount,
            pepInstance.proteinSetsCount,
            pepInstance.selectionLevel,
            pepInstance.elutionTime,
            Option.empty[String],
            pepInstance.bestPeptideMatchId,
            pepId,
            //FIXME : retrieve the right value
            pepId,//pepInstance.getUnmodifiedPeptideId
            Option.empty[Int],
            rsmId
          )
      
      // Update peptide instance id
      pepInstance.id = stmt.generatedInt
      pepInstance.resultSummaryId = rsmId
      
    }
    
    logger.info( "storing peptide instances..." )
    
    val pepInstInsertQuery = MsiDbPeptideInstanceTable.mkInsertQuery { (c,colsList) => 
                               colsList.filter( _ != c.id)
                             }
    
    // Insert peptide instances
    ezDBC.executePrepared( pepInstInsertQuery, true ) { stmt =>
      
      /*
      // Sort peptide instances to be sure that pepInstanceIdByPepId is fulfilled by orphan peptides first
      val peptideInstances = rsm.peptideInstances
      val pepInstsWithoutUnmodPepFirst = peptideInstances.toList.sort { (a,b) => 
                                           a.getUnmodifiedPeptideId < b.getUnmodifiedPeptideId }*/
      
      rsm.peptideInstances.foreach { pepInst =>
        insertPepInstance( stmt, pepInst )
      }
      
    }
    
    // Link peptide instances to peptide matches
    val pepInstPepMatchMapInsertQuery = MsiDbPeptideInstancePeptideMatchMapTable.mkInsertQuery()
    
    ezDBC.executePrepared( pepInstPepMatchMapInsertQuery ) { stmt =>
          
      rsm.peptideInstances.foreach { pepInst =>
        val pepMatchRsmPropsById = pepInst.peptideMatchPropertiesById       
       
        pepInst.getPeptideMatchIds.foreach { pepMatchId =>
          val pepMatchPropsAsJSON = if( pepMatchRsmPropsById != null ) Some(generate( pepMatchRsmPropsById(pepMatchId) )) else None
         
          // Insert peptide match mapping
          stmt.executeWith(
            pepInst.id,
            pepMatchId,
            pepMatchPropsAsJSON,
            rsmId
          )
        }
      }
    }
    
    rsm.peptideInstances.length
  }
  
  def storeRsmProteinSets( rsm: ResultSummary ): Int = {
    
    val rsmId = rsm.id
    val proteinMatchById = rsm.resultSet.get.proteinMatchById
    
    // Retrieve protein_set column list then remove id and master_quant_component_id columns
    val protSetInsertQuery = MsiDbProteinSetTable.mkInsertQuery { (c,colsList) => 
                               colsList.filter( Set(c.id,c.masterQuantComponentId).contains(_) == false )
                             }
     
    // Insert protein sets
    ezDBC.executePrepared( protSetInsertQuery, true ) { stmt =>
      
      for( proteinSet <- rsm.proteinSets ) {
        
        val peptideSet = proteinSet.peptideSet
        var proteinSetScore = Option.empty[Float]
        var proteinSetScoringId = Option.empty[Int]
        
        // Set scoring id if the protein set has a defined score
        if( proteinSet.scoreType != null ) {
          val scoreType = proteinSet.scoreType
          proteinSetScore = Some( proteinSet.score )
          proteinSetScoringId = this.scoringIdByType.get( scoreType )
          
          assert( proteinSetScoringId != None, "can't find a scoring id for the score type '"+scoreType+"'" )
        }
        
        // Determine the typical protein match id using the sequence coverage
        var typicalProtMatchId = proteinSet.getTypicalProteinMatchId
        if( typicalProtMatchId == 0 ) {
          typicalProtMatchId = peptideSet.proteinMatchIds.reduce { 
                                   (a,b) => if( proteinMatchById(a).coverage > proteinMatchById(b).coverage ) a else b
                                 }
        }
        
        // Insert protein set
        stmt.executeWith(
          proteinSetScore,
          proteinSet.isValidated,
          proteinSet.selectionLevel,
          Option.empty[String],
          typicalProtMatchId,
          proteinSetScoringId,
          rsmId
        )
        
        // Update protein set
        proteinSet.id = stmt.generatedInt
        proteinSet.resultSummaryId = rsmId
        peptideSet.proteinSet = Some(proteinSet)
        
      }
    }

    // Link protein sets to protein matches
    ezDBC.executePrepared( MsiDbProteinSetProteinMatchItemTable.mkInsertQuery ) { stmt =>
      
      rsm.proteinSets.foreach { protSet =>
        protSet.getProteinMatchIds.foreach { protMatchId =>
        
          // Insert protein match mapping
          stmt.executeWith(
            protSet.id,
            protMatchId,
            Option.empty[String],
            rsmId
          )
        }
      }
    }
        
    rsm.proteinSets.length
    
  }
  
  def storeRsmPeptideSets( rsm: ResultSummary ): Int = {
    
    val rsmId = rsm.id
    
    // Iterate over peptide sets to initialize the creation of RDB protein/peptide sets
    //val rdbPeptideSetsByTmpId // map RDB peptide set with temporary ids
    val peptideSetIdByTmpId = new HashMap[Int,Int]()
    
    // Retrieve peptide_set columns then remove id column
    val pepSetInsertQuery = MsiDbPeptideSetTable.mkInsertQuery { (c,colsList) => 
                              colsList.filter( _ != c.id)
                            }
     
    // Insert peptide sets
    ezDBC.executePrepared( pepSetInsertQuery, true ) { stmt =>      
      for( peptideSet <- rsm.peptideSets ) {
        
        val protSetId = peptideSet.getProteinSetId
        
        //print(peptideSet.id+".")
        // Insert peptide set
        stmt.executeWith(
          peptideSet.isSubset,
          peptideSet.items.length,
          peptideSet.peptideMatchesCount,
          Option.empty[String],
          if( protSetId > 0 ) Some(protSetId) else Option.empty[Int],
          rsmId
        )
        
        val peptideSetId = stmt.generatedInt
        peptideSetIdByTmpId(peptideSet.id) = peptideSetId
        
        // Update protein set id
        peptideSet.id = peptideSetId
        peptideSet.resultSummaryId = rsmId
        
        // Update some vars
        //rdbPeptideSetsByTmpId( peptideSet.id ) = rdbPeptideSet  
      }
    }

    //val samesetPeptideInstances = peptideSet.getPeptideInstances
    
    // Link peptide sets to peptide instances
    ezDBC.executePrepared( MsiDbPeptideSetPeptideInstanceItemTable.mkInsertQuery ) { stmt =>
      for( peptideSet <- rsm.peptideSets ) {
        for( peptideSetItem <- peptideSet.items ) {
          
          // Update peptide instance id
          //pepSetItem._setPeptideInstanceId( pepInstanceIdMap(pepSetItem.peptideInstanceId) )
          
          // Insert peptide set item
          stmt.executeWith(
            peptideSet.id,
            peptideSetItem.peptideInstance.id,
            peptideSetItem.isBestPeptideSet, //.map { BoolToSQLStr(_) },
            peptideSetItem.selectionLevel,
            Option.empty[String],
            rsmId
          )
          
          // Update peptide set item
          peptideSetItem.resultSummaryId = rsmId
          
        }
        

      }
    }
    
    // Link peptide sets to protein matches
    ezDBC.executePrepared( MsiDbPeptideSetProteinMatchMapTable.mkInsertQuery ) { stmt =>
      rsm.peptideSets.foreach { pepSet => pepSet.proteinMatchIds.foreach { proteinMatchId => 
          stmt.executeWith(
            pepSet.id,
            proteinMatchId,
            rsmId
          )
        }
      }
    }
    
    val storePeptideSetRelation = ( stmt: PreparedStatementWrapper, peptideOverset: PeptideSet, subsetId: Int, isStrictSubset: Boolean ) => {
      stmt.executeWith(
        peptideOverset.id,
        subsetId,
        isStrictSubset,
        rsmId
      )
    }
      
    // Store hierarchical relations between peptide sets
    ezDBC.executePrepared( MsiDbPeptideSetRelationTable.mkInsertQuery ) { stmt =>
      for( peptideSet <- rsm.peptideSets ) {        
        
        // Update and store peptide set relation of type strict subsets
        if( peptideSet.hasStrictSubset ) {
          //peptideSet.getStrictSubsetIds.foreach( i => print(i+";") )
          peptideSet.strictSubsetIds = peptideSet.getStrictSubsetIds.map { peptideSetIdByTmpId(_) } // key not found
          peptideSet.strictSubsetIds.foreach { storePeptideSetRelation( stmt, peptideSet, _, true ) }
        }
        
        // Update and store peptide set relation of type subsumable subset
        if( peptideSet.hasSubsumableSubset ) {
          peptideSet.subsumableSubsetIds = peptideSet.getSubsumableSubsetIds.map { peptideSetIdByTmpId(_) }
          peptideSet.subsumableSubsetIds.foreach { storePeptideSetRelation( stmt, peptideSet, _, false ) }
        }
      }
    }
    
    rsm.peptideSets.length
  }
  
}