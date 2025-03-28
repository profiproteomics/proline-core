package fr.proline.core.om.storer.msi.impl

import scala.collection.mutable.HashMap
import fr.profi.jdbc.easy._
import fr.profi.jdbc.PreparedStatementWrapper
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.helper.MsiDbHelper
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.storer.msi.IRsmStorer
import fr.proline.context.IExecutionContext
import fr.profi.util.primitives._
import scala.collection.mutable.ListBuffer
import scala.collection.immutable.List
import scala.collection.mutable.ArrayBuffer

private[msi] class SQLRsmStorer() extends IRsmStorer {
  
  def storeRsmPeptideInstances( rsm: ResultSummary, execCtx: IExecutionContext ): Int = {
    
    val rsmId = rsm.id
    //val( pepInstanceIdByPepId, pepInstanceIdMap ) = ( new HashMap[Long,Long], new HashMap[Long,Long] )
    /*
    // Sort peptide instances to be sure that pepInstanceIdByPepId is fulfilled by orphan peptides first
    val peptideInstances = rsm.peptideInstances
    val pepInstsWithoutUnmodPepFirst = peptideInstances.toList.sort { (a,b) => 
                                         a.getUnmodifiedPeptideId < b.getUnmodifiedPeptideId }
    */
    
    val insertPepInstance = ( stmt: PreparedStatementWrapper, pepInstance: PeptideInstance) => {
      
      val pepId = pepInstance.peptide.id
      val unmodPepId = pepInstance.getUnmodifiedPeptideId
    
      // Insert peptide instance
      stmt.executeWith(
            pepInstance.peptideMatchesCount,
            pepInstance.proteinMatchesCount,
            pepInstance.proteinSetsCount,
            pepInstance.validatedProteinSetsCount,
            pepInstance.totalLeavesMatchCount,
            pepInstance.selectionLevel,
            pepInstance.elutionTime,
            pepInstance.properties.map(ProfiJson.serialize(_)),
            pepInstance.bestPeptideMatchId,
            pepId,
            if( unmodPepId > 0 ) Some(unmodPepId) else Option.empty[Long],
            Option.empty[Long],
            rsmId
          )
      
      // Update peptide instance id
      pepInstance.id = stmt.generatedLong
      pepInstance.resultSummaryId = rsmId
      
    }
    
    logger.debug( "storing peptide instances..." )
    
    val pepInstInsertQuery = MsiDbPeptideInstanceTable.mkInsertQuery { (c,colsList) => colsList.filter( _ != c.ID) }

    // AW: workaround for issue: #9147 : to allow RT time in RSM/Peptides RT column instead of zero after validation
    ///    get the peaklist ids associated with this single take peptide instance
    
    // VDS : use ResultSet ChildMSI if defined to create spectrumIdElutionTimeMap
     val spectrumIdElutionTimeMap = new HashMap[Long, Float]()
      if (! rsm.peptideInstances.isEmpty )  {
        
        var peaklistIdSqlQuery : String = ""
        if(rsm.getResultSet().isDefined){   
            val rsLike=  rsm.getResultSet().get
            if(rsLike.msiSearch.isDefined) {
              val msiSearchesId =  rsLike.msiSearch.get.id
               peaklistIdSqlQuery =  new SelectQueryBuilder1(MsiDbMsiSearchTable).mkSelectQuery( (msiT,msiC) =>
               List(msiT.PEAKLIST_ID) -> "WHERE "~ msiT.ID ~ " = "~ msiSearchesId );
              
            } else if(rsLike.childMsiSearches != null && rsLike.childMsiSearches.length>0) {
              val msiSearchesIds = rsm.getResultSet().get.childMsiSearches.map(msiS => msiS.id).toSeq
               peaklistIdSqlQuery =  new SelectQueryBuilder1(MsiDbMsiSearchTable).mkSelectQuery( (msiT,msiC) =>
               List(msiT.PEAKLIST_ID) -> "WHERE "~ msiT.ID ~ " IN("~ msiSearchesIds.mkString(",") ~" )");
            }
        } else {             
            val rsId = rsm.getResultSetId
            if ( rsId > 0 ) {    
                peaklistIdSqlQuery = new SelectQueryBuilder2(MsiDbResultSetTable,MsiDbMsiSearchTable).mkSelectQuery( (rsT,rsC,msiT,msiC) =>
                  List(msiT.PEAKLIST_ID) -> "WHERE "~ rsT.MSI_SEARCH_ID ~"="~ msiT.ID ~" AND "~ rsT.ID ~ "="~ rsId );
              } 
        }
            
            
        if(!peaklistIdSqlQuery.isEmpty()) {
            var firstTimeNullErrHappened: Boolean = false // indicates if 1 or more error of this type occurred      
            DoJDBCWork.withEzDBC(execCtx.getMSIDbConnectionContext()) { ezDBC =>
          
            //val sqlQuery = "SELECT id, title, first_time FROM spectrum WHERE peaklist_id in (" + peaklistIdSqlQuery + ")"
            val sqlQuery = new SelectQueryBuilder1(MsiDbSpectrumTable).mkSelectQuery( (t,c) =>
              List(t.ID,t.TITLE,t.FIRST_TIME) -> "WHERE "~ t.PEAKLIST_ID ~" IN("~ peaklistIdSqlQuery ~" )"
            )
            logger.debug("SQL query for spectrumIdElutionTimeMap: " + sqlQuery)
          
            ezDBC.selectAndProcess(sqlQuery) { r =>
  
              val spectrumId = r.nextLong
              val spectrumTitle = r.nextString
  
              val firstTimeOpt = r.nextFloatOption
              if (firstTimeOpt.isDefined) {
                spectrumIdElutionTimeMap += (spectrumId -> firstTimeOpt.get)
              } else {
                if (firstTimeNullErrHappened == false) {
                  firstTimeNullErrHappened = true
                  logger.info(
                    "first time is null for spectrum id: " + spectrumId + " title: " + spectrumTitle
                    + "\nIt might be that you specified a wrong peaklist software when importing the result file"
                    + "\n or that there is no retention time information provided"
                    + "\nNo more messages shown of that error even if it occurs again (for every Title...)")
                }
              }
            }
          } //END DoJDBCWork
        } //END peaklistIdSqlQuery not empty !
     
     } else 
        logger.error("no peptide instance for this rsm!")

      if (spectrumIdElutionTimeMap.isEmpty) {
          this.logger.warn("spectrumIdElutionTimeMap is empty: it means probably this result summary comes from a merged result set")
      } else {
          rsm.peptideInstances.foreach { pepInst =>
  
            val bestPepMatchForThisPepInstOpt = pepInst.peptideMatches.find(_.id == pepInst.bestPeptideMatchId)
            if ( bestPepMatchForThisPepInstOpt.isDefined ) {
              val spectrumId = bestPepMatchForThisPepInstOpt.get.getMs2Query.spectrumId
              if (spectrumIdElutionTimeMap.contains(spectrumId)) {
                pepInst.elutionTime = spectrumIdElutionTimeMap(spectrumId) // modify elution time in peptide instance
              }
            }
          } //END for each pepInst
        }
      
    DoJDBCWork.withEzDBC(execCtx.getMSIDbConnectionContext) { msiEzDBC =>
      
      // Insert peptide instances
      msiEzDBC.executePrepared(pepInstInsertQuery, true) { stmt =>

        rsm.peptideInstances.foreach { pepInst =>
          insertPepInstance(stmt, pepInst)
        }

      }

      // Link peptide instances with peptide matches
      val pepInstPepMatchMapInsertQuery = MsiDbPeptideInstancePeptideMatchMapTable.mkInsertQuery()

      msiEzDBC.executeInBatch(pepInstPepMatchMapInsertQuery) { stmt =>

        rsm.peptideInstances.foreach { pepInst =>
          val pepMatchRsmPropsById = pepInst.peptideMatchPropertiesById

          pepInst.getPeptideMatchIds.foreach { pepMatchId =>
            val pepMatchPropsAsJSON = if (pepMatchRsmPropsById != null) Some(ProfiJson.serialize(pepMatchRsmPropsById(pepMatchId))) else None

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
    } // End of JDBC work
    
    rsm.peptideInstances.length
  }
  
  def storeRsmPeptideSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int = {
    
    val rsmId = rsm.id
    
    val msiDbCtx = execCtx.getMSIDbConnectionContext
    val scoringIdByType = new MsiDbHelper( msiDbCtx ).getScoringIdByType
    
    // Iterate over peptide sets to initialize the creation of RDB protein/peptide sets
    //val rdbPeptideSetsByTmpId // map RDB peptide set with temporary ids
    val peptideSetIdByTmpId = new HashMap[Long,Long]()
    
    // Retrieve peptide_set columns then remove id column
    val pepSetInsertQuery = MsiDbPeptideSetTable.mkInsertQuery { (c,colsList) => colsList.filter( _ != c.ID) }
        
    DoJDBCWork.withEzDBC( msiDbCtx) { msiEzDBC =>
      
      // Insert peptide sets
      msiEzDBC.executePrepared( pepSetInsertQuery, true ) { stmt =>
        for( peptideSet <- rsm.peptideSets ) {
          
          val protSetId = peptideSet.getProteinSetId
          require( peptideSet.scoreType != null, "a score type must be specified for this peptide set" )
          
          val pepSetScoringId = scoringIdByType.get( peptideSet.scoreType )
          require( pepSetScoringId.isDefined, "can't find a scoring id for the score type '"+peptideSet.scoreType+"'" )
          
          // Insert peptide set
          stmt.executeWith(
            peptideSet.isSubset,
            peptideSet.score,
            peptideSet.sequencesCount,
            peptideSet.items.length,
            peptideSet.peptideMatchesCount,
            peptideSet.properties.map(ProfiJson.serialize(_)),
            if( protSetId > 0 ) Some(protSetId) else Option.empty[Long],
            pepSetScoringId,
            rsmId
          )
  
          val peptideSetId = stmt.generatedLong
          peptideSetIdByTmpId(peptideSet.id) = peptideSetId
          
          // Update peptide set id
          peptideSet.id = peptideSetId
          peptideSet.resultSummaryId = rsmId
          
          // Update some vars
          //rdbPeptideSetsByTmpId( peptideSet.id ) = rdbPeptideSet  
        }
      }
  
      //val samesetPeptideInstances = peptideSet.getPeptideInstances
      
      // Link peptide sets to peptide instances
      msiEzDBC.executeInBatch( MsiDbPeptideSetPeptideInstanceItemTable.mkInsertQuery ) { stmt =>
        for( peptideSet <- rsm.peptideSets ) {
          for( peptideSetItem <- peptideSet.items ) {
            
            // Update peptide instance id
            //pepSetItem._setPeptideInstanceId( pepInstanceIdMap(pepSetItem.peptideInstanceId) )
            val isBestPeptideSet: Boolean = peptideSetItem.isBestPeptideSet.getOrElse(false)
            
            // Insert peptide set item
            stmt.executeWith(
              peptideSet.id,
              peptideSetItem.peptideInstance.id,
              isBestPeptideSet,
              peptideSetItem.selectionLevel,
              peptideSetItem.properties.map(ProfiJson.serialize(_)),
              rsmId
            )
            
            // Update peptide set item
            peptideSetItem.resultSummaryId = rsmId
            
          }
          
  
        }
      }
      
      // Link peptide sets to protein matches
      msiEzDBC.executeInBatch( MsiDbPeptideSetProteinMatchMapTable.mkInsertQuery ) { stmt =>
        rsm.peptideSets.foreach { pepSet => pepSet.proteinMatchIds.foreach { proteinMatchId => 
            stmt.executeWith(
              pepSet.id,
              proteinMatchId,
              rsmId
            )
          }
        }
      }
      
      val storePeptideSetRelation = ( stmt: PreparedStatementWrapper, peptideOverset: PeptideSet, subsetId: Long, isStrictSubset: Boolean ) => {
        stmt.executeWith(
          peptideOverset.id,
          subsetId,
          isStrictSubset,
          rsmId
        )
      }
        
      // Store hierarchical relations between peptide sets
      msiEzDBC.executeInBatch( MsiDbPeptideSetRelationTable.mkInsertQuery ) { stmt =>
        for( peptideSet <- rsm.peptideSets ) {
          
          // Update and store peptide set relation of type strict subsets
          if( peptideSet.hasStrictSubset ) {
            //peptideSet.getStrictSubsetIds.foreach( i => print(i+";") )
            peptideSet.strictSubsetIds = peptideSet.strictSubsetIds.map { peptideSetIdByTmpId(_) } // key not found
            peptideSet.strictSubsetIds.foreach { storePeptideSetRelation( stmt, peptideSet, _, true ) }
          }
          
          // Update and store peptide set relation of type subsumable subset
          if( peptideSet.hasSubsumableSubset ) {
            peptideSet.subsumableSubsetIds = peptideSet.subsumableSubsetIds.map { peptideSetIdByTmpId(_) }
            peptideSet.subsumableSubsetIds.foreach { storePeptideSetRelation( stmt, peptideSet, _, false ) }
          }
        }
      }
    }//End DoJDBCReturningWork
    
    rsm.peptideSets.length
  }
  
  def storeRsmProteinSets( rsm: ResultSummary, execCtx: IExecutionContext ): Int = {
    
    val rsmId = rsm.id
    val proteinMatchById = rsm.resultSet.get.getProteinMatchById
    
    // Retrieve protein_set column list then remove id and master_quant_component_id columns
    val protSetInsertQuery = MsiDbProteinSetTable.mkInsertQuery { (c,colsList) => 
                               colsList.filter( Set(c.ID,c.MASTER_QUANT_COMPONENT_ID).contains(_) == false )
                             }
    val msiDbCtx = execCtx.getMSIDbConnectionContext
    
    DoJDBCWork.withEzDBC( msiDbCtx) { msiEzDBC =>
    
      // Insert protein sets
      msiEzDBC.executePrepared( protSetInsertQuery, true ) { stmt =>
        
        for( proteinSet <- rsm.proteinSets ) {
          
          val peptideSet = proteinSet.peptideSet
          
          // Determine the representative protein match id using the accession number
          var reprProtMatchId = proteinSet.getRepresentativeProteinMatchId()
          if( reprProtMatchId == 0 ) {
            reprProtMatchId = proteinSet.getSameSetProteinMatchIds.minBy(proteinMatchById(_).accession)
          }
          
          // Insert protein set
          stmt.executeWith(
            proteinSet.isDecoy,
            proteinSet.isValidated,
            proteinSet.selectionLevel,
            proteinSet.properties.map(ProfiJson.serialize(_)),
            reprProtMatchId,
            rsmId
          )
          
          // Update protein set
          proteinSet.id = stmt.generatedLong
          proteinSet.resultSummaryId = rsmId
          peptideSet.proteinSet = Some(proteinSet)
          
        }
      }
  
      // Link protein sets to protein matches
      msiEzDBC.executeInBatch( MsiDbProteinSetProteinMatchItemTable.mkInsertQuery ) { stmt =>
        
        rsm.proteinSets.foreach { protSet =>
          
          val protMatchRsmPropsById = protSet.proteinMatchPropertiesById
          
          for( (peptideSet,protMatchIds) <- protSet.getAllProteinMatchesIdByPeptideSet; protMatchId <- protMatchIds ) {
            
            val protMatchPropsAsJSON = if( protMatchRsmPropsById != null ) Some(ProfiJson.serialize( protMatchRsmPropsById(protMatchId) )) else None
            val coverage = if( protSet.proteinMatchCoverageById == null ) 0f
            else protSet.proteinMatchCoverageById.get(protMatchId).getOrElse(0f)
            
            // Insert protein match mapping
            stmt.executeWith(
              protSet.id,
              protMatchId,
              peptideSet.isSubset,
              coverage,
              protMatchPropsAsJSON,
              rsmId
            )
          }
        }
      }
    } // End of JDBC work
        
    rsm.proteinSets.length
    
  }
  
  
}
