package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.HashMap
import com.codahale.jerkson.Json.generate
import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.core.dal.tables.msi._
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.IRsmStorer
import fr.proline.context.IExecutionContext

private[msi] class SQLiteRsmStorer() extends IRsmStorer {
  
  def storeRsmPeptideInstances( rsm: ResultSummary, execCtx: IExecutionContext ): Int = {
    
    val rsmId = rsm.id
    //val( pepInstanceIdByPepId, pepInstanceIdMap ) = ( new HashMap[Int,Int], new HashMap[Int,Int] )
    
    val insertPepInstance = ( stmt: PreparedStatementWrapper, pepInstance: PeptideInstance ) => {
      
      val pepId = pepInstance.peptide.id
      val unmodPepId = pepInstance.getUnmodifiedPeptideId
      
      // Insert peptide instance
      stmt.executeWith(
            pepInstance.peptideMatchesCount,
            pepInstance.proteinMatchesCount,
            pepInstance.proteinSetsCount,
            0,
            pepInstance.selectionLevel,
            pepInstance.elutionTime,
            pepInstance.properties.map(generate(_)),
            pepInstance.bestPeptideMatchId,
            pepId,
            if( unmodPepId > 0 ) Some(unmodPepId) else Option.empty[Int],
            Option.empty[Int],
            rsmId
          )
      
      // Update peptide instance id
      pepInstance.id = stmt.generatedInt
      pepInstance.resultSummaryId = rsmId
      
    }
    
    logger.info( "storing peptide instances..." )
    
    val pepInstInsertQuery = MsiDbPeptideInstanceTable.mkInsertQuery { (c,colsList) => 
                               colsList.filter( _ != c.ID)
                             }
    
    DoJDBCWork.withEzDBC( execCtx.getMSIDbConnectionContext, { msiEzDBC =>
      // Insert peptide instances
      msiEzDBC.executePrepared( pepInstInsertQuery, true ) { stmt =>
        
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
      
      msiEzDBC.executePrepared( pepInstPepMatchMapInsertQuery ) { stmt =>
            
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
    }) // End of JDBC work
    
    rsm.peptideInstances.length
  }
  
  def storeRsmPeptideSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int = {
    
    val rsmId = rsm.id
    
    // Iterate over peptide sets to initialize the creation of RDB protein/peptide sets
    //val rdbPeptideSetsByTmpId // map RDB peptide set with temporary ids
    val peptideSetIdByTmpId = new HashMap[Int,Int]()
    
    // Retrieve peptide_set columns then remove id column
    val pepSetInsertQuery = MsiDbPeptideSetTable.mkInsertQuery { (c,colsList) => 
                              colsList.filter( _ != c.ID)
                            }
    DoJDBCWork.withEzDBC( execCtx.getMSIDbConnectionContext, { msiEzDBC =>
      
      // Insert peptide sets
      msiEzDBC.executePrepared( pepSetInsertQuery, true ) { stmt =>      
        for( peptideSet <- rsm.peptideSets ) {
          
          val protSetId = peptideSet.getProteinSetId
          
          // Insert peptide set
          stmt.executeWith(
            peptideSet.isSubset,
            peptideSet.items.length,
            peptideSet.peptideMatchesCount,
            peptideSet.properties.map(generate(_)),
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
      msiEzDBC.executePrepared( MsiDbPeptideSetPeptideInstanceItemTable.mkInsertQuery ) { stmt =>
        for( peptideSet <- rsm.peptideSets ) {
          for( peptideSetItem <- peptideSet.items ) {
            
            // Update peptide instance id
            //pepSetItem._setPeptideInstanceId( pepInstanceIdMap(pepSetItem.peptideInstanceId) )
            
            // Insert peptide set item
            stmt.executeWith(
              peptideSet.id,
              peptideSetItem.peptideInstance.id,
              peptideSetItem.isBestPeptideSet,
              peptideSetItem.selectionLevel,
              peptideSetItem.properties.map(generate(_)),
              rsmId
            )
            
            // Update peptide set item
            peptideSetItem.resultSummaryId = rsmId
            
          }
          
  
        }
      }
      
      // Link peptide sets to protein matches
      msiEzDBC.executePrepared( MsiDbPeptideSetProteinMatchMapTable.mkInsertQuery ) { stmt =>
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
      msiEzDBC.executePrepared( MsiDbPeptideSetRelationTable.mkInsertQuery ) { stmt =>
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
    })
    
    rsm.peptideSets.length
  }
  
  def storeRsmProteinSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int = {
    
    val rsmId = rsm.id
    val proteinMatchById = rsm.resultSet.get.proteinMatchById
    
    // Retrieve protein_set column list then remove id and master_quant_component_id columns
    val protSetInsertQuery = MsiDbProteinSetTable.mkInsertQuery { (c,colsList) => 
                               colsList.filter( Set(c.ID,c.MASTER_QUANT_COMPONENT_ID).contains(_) == false )
                             }
    val msiDbCtx = execCtx.getMSIDbConnectionContext
    
    DoJDBCWork.withEzDBC( msiDbCtx, { msiEzDBC =>
      
      val scoringIdByType = new MsiDbHelper( msiDbCtx ).getScoringIdByType
    
      // Insert protein sets
      msiEzDBC.executePrepared( protSetInsertQuery, true ) { stmt =>
        
        for( proteinSet <- rsm.proteinSets ) {
          
          val peptideSet = proteinSet.peptideSet
          var proteinSetScore = Option.empty[Float]
          var proteinSetScoringId = Option.empty[Int]
          
          // Set scoring id if the protein set has a defined score
          if( proteinSet.scoreType != null ) {
            val scoreType = proteinSet.scoreType
            proteinSetScore = Some( proteinSet.score )
            proteinSetScoringId = scoringIdByType.get( scoreType )
            
            assert( proteinSetScoringId != None, "can't find a scoring id for the score type '"+scoreType+"'" )
          }
          
          // Determine the typical protein match id using the sequence coverage
          var typicalProtMatchId = proteinSet.getTypicalProteinMatchId
          if( typicalProtMatchId == 0 ) {
            /*if( proteinMatchById.contains(proteinSet.proteinMatchIds(0)) == false ) {
              println("searched id="+proteinSet.proteinMatchIds(0))
              println("first id is="+proteinMatchById.keys.head)
            }*/
            typicalProtMatchId = proteinSet.proteinMatchIds.reduce { (a,b) => 
              if( proteinMatchById(a).coverage > proteinMatchById(b).coverage ) a else b
            }
          }
          
          // Insert protein set
          stmt.executeWith(
            proteinSetScore,
            proteinSet.isValidated,
            proteinSet.selectionLevel,
            proteinSet.properties.map(generate(_)),
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
      msiEzDBC.executePrepared( MsiDbProteinSetProteinMatchItemTable.mkInsertQuery ) { stmt =>
        
        rsm.proteinSets.foreach { protSet =>
          
          val protMatchRsmPropsById = protSet.proteinMatchPropertiesById
          
          protSet.getProteinMatchIds.foreach { protMatchId =>
            
            val protMatchPropsAsJSON = if( protMatchRsmPropsById != null ) Some(generate( protMatchRsmPropsById(protMatchId) )) else None
          
            // Insert protein match mapping
            stmt.executeWith(
              protSet.id,
              protMatchId,
              protMatchPropsAsJSON,
              rsmId
            )
          }
        }
      }
    }) // End of JDBC work
        
    rsm.proteinSets.length
    
  }
  
  
}