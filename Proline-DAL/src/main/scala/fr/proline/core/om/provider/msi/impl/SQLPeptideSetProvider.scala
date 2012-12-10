package fr.proline.core.om.provider.msi.impl

import com.codahale.jerkson.Json.parse

import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.dal.{MsiDbPeptideSetTable,MsiDbPeptideSetRelationTable}
import fr.proline.core.dal.{MsiDbPeptideSetPeptideInstanceItemTable,MsiDbPeptideSetProteinMatchMapTable}
import fr.proline.util.sql.SQLStrToBool
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.core.om.model.msi.PeptideSetItem
import fr.proline.core.om.provider.msi.{IPeptideSetProvider,IPeptideInstanceProvider}

class SQLPeptideSetProvider( val msiDb: SQLQueryExecution, val psDb: SQLQueryExecution ) extends IPeptideSetProvider {
  
  val PepSetCols = MsiDbPeptideSetTable.columns
  val PepSetRelationCols = MsiDbPeptideSetRelationTable.columns
  val PepSetItemCols = MsiDbPeptideSetPeptideInstanceItemTable.columns
  val ProtMatchMappingCols = MsiDbPeptideSetProteinMatchMapTable.columns
  
  private def _getPeptideInstanceProvider(): IPeptideInstanceProvider = {
    //if( this.peptideInstanceProvider != None ) this.peptideProvider.get
    //else new SQLPeptideProvider(this.psDb)
    new SQLPeptideInstanceProvider(this.msiDb,this.psDb)
  }
  
  def getPeptideSetsAsOptions( pepSetIds: Seq[Int] ): Array[Option[PeptideSet]] = {
    
    val pepSets = this.getPeptideSets( pepSetIds )
    val pepSetById = pepSets.map { p => p.id -> p } toMap
    
    pepSetIds.map { pepSetById.get( _ ) } toArray
    
  }
  
  def getPeptideSets( pepSetIds: Seq[Int] ): Array[PeptideSet] = {
    val pepSetItemRecords = this._getPepSetItemRecords( pepSetIds )
    val pepInstIds = pepSetItemRecords.map { _(PepSetItemCols.peptideInstanceId).asInstanceOf[Int] } distinct
    
    this._buildPeptideSets( this._getPepSetRecords( pepSetIds ),
                            this._getPepSetRelationRecords( pepSetIds ),
                            pepSetItemRecords,
                            this._getPeptideInstanceProvider.getPeptideInstances( pepInstIds ),
                            this._getPepSetProtMatchMapRecords( pepSetIds )
                            )
  }
  
  def getResultSummariesPeptideSets( rsmIds: Seq[Int] ): Array[PeptideSet] = {    
    this._buildPeptideSets( this._getRSMsPepSetRecords( rsmIds ),
                            this._getRSMsPepSetRelationRecords( rsmIds ),
                            this._getRSMsPepSetItemRecords( rsmIds ),
                            this._getPeptideInstanceProvider.getResultSummariesPeptideInstances( rsmIds ),
                            this._getRSMsPepSetProtMatchMapRecords( rsmIds )
                            )
  }
    
  private def _getRSMsPepSetRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set WHERE result_summary_id IN (" + rsmIds.mkString(",") +")")
  }
  
  private def _getPepSetRecords( pepSetIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set WHERE id IN (" + pepSetIds.mkString(",") +")")    
  }
  
  private def _getRSMsPepSetRelationRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set_relation WHERE result_summary_id IN (" + rsmIds.mkString(",") +")")
  }
  
  private def _getPepSetRelationRecords( pepSetIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set_relation WHERE peptide_overset_id IN (" + pepSetIds.mkString(",") +")")    
  }
  
  private def _getRSMsPepSetItemRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set_peptide_instance_item WHERE result_summary_id IN (" + rsmIds.mkString(",") +")")
  }
  
  private def _getPepSetItemRecords( pepSetIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set_peptide_instance_item WHERE peptide_set_id IN (" + pepSetIds.mkString(",") +")")    
  }
  
  private def _getRSMsPepSetProtMatchMapRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set_protein_match_map WHERE result_summary_id IN (" + rsmIds.mkString(",") +")")
  }
  
  private def _getPepSetProtMatchMapRecords( pepSetIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_set_protein_match_map WHERE peptide_set_id IN (" + pepSetIds.mkString(",") +")")    
  }
  
  private def _buildPeptideSets( pepSetRecords: Seq[Map[String,Any]],
                                 pepSetRelationRecords: Seq[Map[String,Any]],
                                 pepSetItemRecords: Seq[Map[String,Any]],
                                 pepInstances: Seq[PeptideInstance],
                                 pepSetProtMatchMapRecords: Seq[Map[String,Any]] ): Array[PeptideSet] = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    // Load peptides
    //val uniqPepSetIds = pepSetRecords map { _(PepSetCols.peptideId).asInstanceOf[Int] } distinct
    //val peptides = this._getPeptideProvider().getPeptides(uniqPepIds)
    
    // Map peptide instance by their id
    val pepInstById = Map() ++ pepInstances.map { p => ( p.id -> p ) }
    
    // Group peptide set relations by peptide overset ids
    val pepSetRelRecordsByOversetId = pepSetRelationRecords.groupBy {
                                        _(PepSetRelationCols.peptideOversetId).asInstanceOf[Int]
                                      }
    
    // Group peptide set items mapping by peptide set id
    val pepSetItemRecordsByPepSetId = pepSetItemRecords.groupBy { 
                                        _(PepSetItemCols.peptideSetId).asInstanceOf[Int]
                                      }
    // Group protein matches mapping by peptide set id
    val protMatchMappingByPepSetId = pepSetProtMatchMapRecords.groupBy { 
                                        _(PepSetItemCols.peptideSetId).asInstanceOf[Int]
                                      }
    
    // Build peptide sets
    val pepSets = new Array[PeptideSet](pepSetRecords.length)
    
    for( pepSetIdx <- 0 until pepSetRecords.length ) {
      
      // Retrieve peptide instance record
      val pepSetRecord = pepSetRecords(pepSetIdx)
      val pepSetId: Int = pepSetRecord(PepSetCols.id).asInstanceOf[AnyVal]
      
      // Retrieve peptide set relations
      var strictSubsetIdsBuilder = Array.newBuilder[Int]
      var subsumableSubsetIdsBuilder = Array.newBuilder[Int]
      
      if( pepSetRelRecordsByOversetId.contains(pepSetId) ) {
        pepSetRelRecordsByOversetId(pepSetId).foreach { pepSetRelationRecord =>
          
          val peptideSubsetId = pepSetRelationRecord(PepSetRelationCols.peptideSubsetId).asInstanceOf[Int]
          val isStrictSubset = SQLStrToBool( pepSetRelationRecord(PepSetRelationCols.isStrictSubset).asInstanceOf[String] )
          
          if( isStrictSubset) strictSubsetIdsBuilder += peptideSubsetId
          else subsumableSubsetIdsBuilder += peptideSubsetId
        }
      }
      
      // Retrieve peptide set items      
      val pepSetItems = Array.newBuilder[PeptideSetItem]
      
      pepSetItemRecordsByPepSetId(pepSetId).foreach { pepSetItemRecord =>
        val pepInstId = pepSetItemRecord(PepSetItemCols.peptideInstanceId).asInstanceOf[Int]
        val pepInst = pepInstById(pepInstId)
        val isBestPepSetField = pepSetItemRecord(PepSetItemCols.isBestPeptideSet)        
        val isBestPepSet = if( isBestPepSetField != null ) Some(SQLStrToBool(isBestPepSetField.asInstanceOf[String])) else None
        
        val pepSetItem = new PeptideSetItem(
                               selectionLevel = pepSetItemRecord(PepSetItemCols.selectionLevel).asInstanceOf[Int],
                               peptideInstance = pepInst,
                               peptideSetId = pepSetId,
                               isBestPeptideSet = isBestPepSet,
                               resultSummaryId = pepSetItemRecord(PepSetItemCols.resultSummaryId).asInstanceOf[Int]
                               )
        
        pepSetItems += pepSetItem

      }
      
      var protMatchIds = protMatchMappingByPepSetId(pepSetId).map {
                           _(ProtMatchMappingCols.proteinMatchId).asInstanceOf[Int]
                         } toArray
      
      // Decode JSON properties
      /*val propertiesAsJSON = pepInstRecord(PepInstCols.serializedProperties).asInstanceOf[String]
      var properties = Option.empty[PeptideMatchProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[PeptideMatchProperties](propertiesAsJSON) )
      }*/
      
      // TODO: retrieve peptide match ids
      val pepSet = new PeptideSet(
                         id = pepSetId,
                         items = pepSetItems.result(),
                         isSubset = SQLStrToBool( pepSetRecord(PepSetCols.isSubset).asInstanceOf[String] ),
                         peptideMatchesCount = pepSetRecord(PepSetCols.peptideMatchCount).asInstanceOf[Int],
                         proteinMatchIds = protMatchIds,
                         proteinSetId = pepSetRecord.getOrElse(PepSetCols.proteinSetId,0).asInstanceOf[Int],
                         strictSubsetIds = strictSubsetIdsBuilder.result(),
                         subsumableSubsetIds = subsumableSubsetIdsBuilder.result(),
                         resultSummaryId = pepSetRecord(PepSetCols.resultSummaryId).asInstanceOf[Int]
                        )

      pepSets(pepSetIdx) = pepSet
      
    }
    
    pepSets
    
  }
  
}