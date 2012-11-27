package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.Json.parse
import fr.proline.core.dal.{MsiDb,PsDb,MsiDbPeptideInstanceTable,MsiDbPeptideInstancePeptideMatchMapTable}
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatchValidationProperties
import fr.proline.core.dal.MsiDb
import fr.proline.core.om.provider.msi.IPeptideInstanceProvider
import fr.proline.core.om.provider.msi.IPeptideProvider

class SQLPeptideInstanceProvider( val msiDb: MsiDb,
                                  val psDb: PsDb = null,
                                  var peptideProvider: Option[IPeptideProvider] = None ) extends IPeptideInstanceProvider {
  
  val PepInstCols = MsiDbPeptideInstanceTable.columns
  val PepMatchMappingCols = MsiDbPeptideInstancePeptideMatchMapTable.columns
  
  private def _getPeptideProvider(): IPeptideProvider = {
    if( this.peptideProvider != None ) this.peptideProvider.get
    else new SQLPeptideProvider(this.psDb)
  }
  
  def getPeptideInstancesAsOptions( pepInstIds: Seq[Int] ): Array[Option[PeptideInstance]] = {
    
    val pepInsts = this.getPeptideInstances( pepInstIds )
    val pepInstById = pepInsts.map { p => p.id -> p } toMap
    
    pepInstIds.map { pepInstById.get( _ ) } toArray
    
  }
  
  def getPeptideInstances( pepInstIds: Seq[Int] ): Array[PeptideInstance] = {
    val pepInstRecords = this._getPepInstRecords( pepInstIds )
    val pepInstPepMatchMapRecords = this._getPepInstPepMatchMapRecords( pepInstIds )
    this._buildPeptideInstances( pepInstRecords, pepInstPepMatchMapRecords )
  }
  
  def getResultSummariesPeptideInstances( rsmIds: Seq[Int] ): Array[PeptideInstance] = {
    val pepInstRecords =  this._getRSMsPepInstRecords( rsmIds )
    val pepInstPepMatchMapRecords = this._getRSMsPepInstPepMatchMapRecords( rsmIds )    
    this._buildPeptideInstances( pepInstRecords, pepInstPepMatchMapRecords )    
  }
    
  private def _getRSMsPepInstRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_instance WHERE result_summary_id IN (" + rsmIds.mkString(",") +")")
  }
  
  private def _getPepInstRecords( pepInstIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_instance WHERE id IN (" + pepInstIds.mkString(",") +")")    
  }
  
  private def _getRSMsPepInstPepMatchMapRecords( rsmIds: Seq[Int] ): Array[Map[String,Any]] = {
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_instance_peptide_match_map WHERE result_summary_id IN (" + rsmIds.mkString(",") +")")
  }
  
  private def _getPepInstPepMatchMapRecords( pepInstIds: Seq[Int] ): Array[Map[String,Any]] = {    
    // TODO: use max nb iterations
    this.msiDb.selectRecordsAsMaps("SELECT * FROM peptide_instance_peptide_match_map WHERE peptide_instance_id IN (" + pepInstIds.mkString(",") +")")    
  }
  
  private def _buildPeptideInstances( pepInstRecords: Seq[Map[String,Any]],
                                      pepInstPepMatchMapRecords: Seq[Map[String,Any]] ): Array[PeptideInstance] = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    // Load peptides
    val uniqPepIds = pepInstRecords map { _(PepInstCols.peptideId).asInstanceOf[Int] } distinct
    val peptides = this._getPeptideProvider().getPeptides(uniqPepIds)
    
    // Map peptides by their id
    val peptideById = Map() ++ peptides.map { pep => ( pep.id -> pep ) }
    
    // Group peptide matches mapping by peptide instance id
    val pepMatchesMappingsByPepInstId = pepInstPepMatchMapRecords.groupBy { 
                                          _(PepMatchMappingCols.peptideInstanceId).asInstanceOf[Int]
                                        }
    
    // Build peptide instances
    val pepInsts = new Array[PeptideInstance](pepInstRecords.length)
    
    for( pepInstIdx <- 0 until pepInstRecords.length ) {
      
      // Retrieve peptide instance record
      val pepInstRecord = pepInstRecords(pepInstIdx)
      
      // Retrieve the corresponding peptide
      val pepId: Int = pepInstRecord(PepInstCols.peptideId).asInstanceOf[AnyVal]      
      if( ! peptideById.contains(pepId) ) throw new Exception("undefined peptide with id ='"+pepId+"'")
      val peptide = peptideById(pepId)
      
      // Retrieve peptide match ids and properties
      val pepInstId: Int = pepInstRecord(PepInstCols.id).asInstanceOf[AnyVal]
      val pepMatchIds = new ArrayBuffer[Int]()
      val pepMatchPropertyMapBuilder = Map.newBuilder[Int, PeptideMatchValidationProperties]
      
      pepMatchesMappingsByPepInstId(pepInstId).foreach { pepMatchMapping =>
        val pepMatchId = pepMatchMapping(PepMatchMappingCols.peptideMatchId).asInstanceOf[Int]
        pepMatchIds += pepMatchId
        
        val propertiesAsJSON = pepMatchMapping(PepMatchMappingCols.serializedProperties).asInstanceOf[String]        
        if( propertiesAsJSON != null ) {
          pepMatchPropertyMapBuilder += pepMatchId -> parse[PeptideMatchValidationProperties](propertiesAsJSON)
        }
      }
      
      // Decode JSON properties
      /*val propertiesAsJSON = pepInstRecord(PepInstCols.serializedProperties).asInstanceOf[String]
      var properties = Option.empty[PeptideMatchProperties]
      if( propertiesAsJSON != null ) {
        properties = Some( parse[PeptideMatchProperties](propertiesAsJSON) )
      }*/
      
      // TODO: load properties
      val pepInst = new PeptideInstance( id = pepInstId,
                                         peptide = peptide,
                                         proteinMatchesCount = pepInstRecord(PepInstCols.proteinMatchCount).asInstanceOf[Int],
                                         proteinSetsCount = pepInstRecord(PepInstCols.proteinSetCount).asInstanceOf[Int],
                                         selectionLevel = pepInstRecord(PepInstCols.selectionLevel).asInstanceOf[Int],
                                         elutionTime = pepInstRecord(PepInstCols.elutionTime).asInstanceOf[Double].toFloat,
                                         peptideMatchIds = pepMatchIds.toArray,
                                         bestPeptideMatchId = pepInstRecord(PepInstCols.bestPeptideMatchId).asInstanceOf[Int],
                                         unmodifiedPeptideId = pepInstRecord(PepInstCols.unmodifiedPeptideId).asInstanceOf[Int],
                                         resultSummaryId = pepInstRecord(PepInstCols.resultSummaryId).asInstanceOf[Int],
                                         peptideMatchPropertiesById = pepMatchPropertyMapBuilder.result()
                                        )
      
      pepInsts(pepInstIdx) = pepInst
      
    }
    
    pepInsts
    
  }
  
}