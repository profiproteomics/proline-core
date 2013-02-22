package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.codahale.jerkson.Json.parse
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.msi.MsiDbMasterQuantComponentTable
import fr.proline.core.dal.tables.msi.MsiDbObjectTreeTable
import fr.proline.core.dal.tables.msi.MsiDbPeptideInstanceTable
import fr.proline.core.om.model.msi.PeptideInstance
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.om.provider.msq.IQuantResultSummaryProvider
import fr.proline.util.primitives.LongOrIntAsInt._

class SQLQuantResultSummaryProvider(
  override val msiDbCtx: SQLConnectionContext,
  override val psDbCtx: SQLConnectionContext,
  override val udsDbCtx: SQLConnectionContext
) extends SQLResultSummaryProvider(msiDbCtx,psDbCtx,udsDbCtx) with IQuantResultSummaryProvider {
  
  
  protected val mqPepIonsProvider = new SQLMasterQuantPeptideIonProvider(msiDbCtx)
  val MQComponentTable = MsiDbMasterQuantComponentTable
  val MQCompCols = MQComponentTable.columns
  val ObjectTreeTable = MsiDbObjectTreeTable
  
  /*case class QuantResultSummary( 
                               var masterQuantProteinSets: Array[MasterQuantProteinSet],
                               var masterQuantPeptides: Array[MasterQuantProteinSet],
                               var masterQuantPeptideIons: Array[MasterQuantProteinSet],
                               
                               var resultSummary: ResultSummary*/

  def getQuantResultSummariesAsOptions( quantRsmIds: Seq[Int], loadResultSet: Boolean ): Array[Option[QuantResultSummary]] = {
    val rsms = this.getQuantResultSummaries(quantRsmIds, loadResultSet)
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;
    quantRsmIds.map { rsmById.get(_) } toArray
  }
  
  // TODO: find a way to handle master quant reporter ions
  def getQuantResultSummaries( quantRsmIds: Seq[Int], loadResultSet: Boolean ): Array[QuantResultSummary] = {
    
    val rsms = this.getResultSummaries(quantRsmIds, loadResultSet)
    
    // Map peptide instances bey thier id
    val pepInstById = Map() ++ rsms.flatMap( _.peptideInstances.map( pi => pi.id -> pi ) )
    
    // Retrieve the mapping of ids between peptide instances and master quant peptides
    val mqPepIdByPepInstIdQuery = new SelectQueryBuilder1(MsiDbPeptideInstanceTable).mkSelectQuery( (t1,c1) => 
      List(t1.ID,t1.MASTER_QUANT_COMPONENT_ID) -> 
      "WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIds.mkString(",") ~")"
    )
    
    val pepInstByMqPepIdBuilder = Map.newBuilder[Int,PeptideInstance]
    msiDbCtx.ezDBC.selectAndProcess(mqPepIdByPepInstIdQuery) { r =>
      val pepInstId = r.nextInt
      r.nextIntOption.foreach { mqPepId =>
        pepInstByMqPepIdBuilder += mqPepId -> pepInstById(pepInstId)
      }
    }
    
    // Load master quant peptide ions
    val mqPepIons = mqPepIonsProvider.getQuantResultSummariesMQPeptideIons(quantRsmIds)
    
    // FIXME: map by master_quant_peptide_id field when it is added to master_quant_peptide_ion table
    val mqPepIonsByPepInstId = new HashMap[Int,ArrayBuffer[MasterQuantPeptideIon]]
    mqPepIons.foreach { mqPepIon => 
      val pepInstId = mqPepIon.getBestQuantPeptideIon.get.peptideInstanceId.get
      mqPepIonsByPepInstId.getOrElseUpdate(pepInstId, new ArrayBuffer[MasterQuantPeptideIon]) += mqPepIon
    }
    
    // Load master quant peptides
    val mqPeps = this.getMasterQuantPeptides( quantRsmIds, pepInstByMqPepIdBuilder.result, mqPepIonsByPepInstId.toMap )
    
    // Load master quant protein sets

    null
  }
  
  protected def getMasterQuantPeptides(
    quantRsmIds: Seq[Int],
    pepInstByMqPepId: Map[Int,PeptideInstance],
    mqPepIonsByPepInstId: Map[Int,ArrayBuffer[MasterQuantPeptideIon]]
  ): Array[MasterQuantPeptide] = {
    
    val mqPepCompQueryBuilder = new SelectQueryBuilder2(MQComponentTable,ObjectTreeTable)
    val mqPepCompQuery = mqPepCompQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2) => List(t1.ID,t1.SELECTION_LEVEL,t1.SERIALIZED_PROPERTIES,t2.SERIALIZED_DATA) -> 
      " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIds.mkString(",") ~")" ~
      " AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID
    )
    
    msiDbCtx.ezDBC.select(mqPepCompQuery) { r =>

      val mqPepId: Int = r.getAnyVal(MQCompCols.ID)
      val pepInst = pepInstByMqPepId.get(mqPepId)
      val quantPeptides = parse[Array[QuantPeptide]]( r.getString(ObjectTreeTable.columns.SERIALIZED_DATA) )
      
      var quantPeptideMap = Map.empty[Int,QuantPeptide]
      var mqPepIons= Array.empty[MasterQuantPeptideIon]
      if( pepInst != None ) {
        val quantPeptides = parse[Array[QuantPeptide]]( r.getString(ObjectTreeTable.columns.SERIALIZED_DATA) )
        quantPeptideMap = Map() ++ quantPeptides.map( qp => qp.quantChannelId -> qp )
        
        // TODO: use master_quant_peptide_id field when it is added to master_quant_peptide_ion table
        mqPepIons = mqPepIonsByPepInstId(pepInst.get.id).toArray
      }
      
      // Build the master quant peptide ion
      new MasterQuantPeptide(
        id = mqPepId,
        peptideInstance = pepInst,
        quantPeptideMap = quantPeptideMap,
        masterQuantPeptideIons = mqPepIons,
        selectionLevel = r.getInt(MQCompCols.SELECTION_LEVEL),
        properties = r.getStringOption(MQCompCols.SERIALIZED_PROPERTIES).map(parse[MasterQuantPeptideProperties](_))
      )
      
    } toArray
    
  }
  
  protected def getMasterQuantProteinSets(
    quantRsmIds: Seq[Int],
    protSetById: Map[Int,ProteinSet],
    mqPepByPepInstId: Map[Int,MasterQuantPeptide]
    ): Array[MasterQuantProteinSet] = {
    
    val mqProtSetCompQueryBuilder = new SelectQueryBuilder2(MQComponentTable,ObjectTreeTable)
    val mqProtSetCompQuery = mqProtSetCompQueryBuilder.mkSelectQuery(
      (t1,c1,t2,c2) => List(t1.ID,t1.SELECTION_LEVEL,t1.SERIALIZED_PROPERTIES,t2.SERIALIZED_DATA) -> 
      " WHERE "~ t1.RESULT_SUMMARY_ID ~" IN("~ quantRsmIds.mkString(",") ~")" ~
      " AND "~ t1.OBJECT_TREE_ID ~" = "~ t2.ID
    )
    
    msiDbCtx.ezDBC.select(mqProtSetCompQuery) { r =>

      val mqProtSetId: Int = r.getAnyVal(MQCompCols.ID)
      
      /*val pepInst = pepInstByMqPepId.get(mqPepId)
      val quantPeptides = parse[Array[QuantPeptide]]( r.getString(ObjectTreeTable.columns.SERIALIZED_DATA) )
      
      var quantPeptideMap = Map.empty[Int,QuantPeptide]
      var mqPepIons= Array.empty[MasterQuantPeptideIon]
      if( pepInst != None ) {
        val quantPeptides = parse[Array[QuantPeptide]]( r.getString(ObjectTreeTable.columns.SERIALIZED_DATA) )
        quantPeptideMap = Map() ++ quantPeptides.map( qp => qp.quantChannelId -> qp )
        
        // TODO: use master_quant_peptide_id field when it is added to master_quant_peptide_ion table
        mqPepIons = mqPepIonsByPepInstId(pepInst.get.id).toArray
      }
      
      // Build the master quant peptide ion
      new MasterQuantPeptide(
        id = mqPepId,
        peptideInstance = pepInst,
        quantPeptideMap = quantPeptideMap,
        masterQuantPeptideIons = mqPepIons,
        selectionLevel = r.getInt(MQCompCols.SELECTION_LEVEL),
        properties = r.getStringOption(MQCompCols.SERIALIZED_PROPERTIES).map(parse[MasterQuantPeptideProperties](_))
      )*/
      
      null
      
    } toArray
    
    null
  }
  
  // Instantiate a MSIdb helper
  
  /*val msiDbHelper = new MsiDbHelper(msiDbCtx.ezDBC)

  val RSMCols = MsiDbResultSummaryTable.columns

  def getQuantResultSummaries(rsmIds: Seq[Int], loadResultSet: Boolean): Array[ResultSummary] = {

    import fr.proline.util.primitives.LongOrIntAsInt._
    import fr.proline.util.sql.StringOrBoolAsBool._

    // Load peptide sets
    val pepSetProvider = new SQLPeptideSetProvider(msiDbCtx, psDbCtx)
    val pepSets = pepSetProvider.getResultSummariesPeptideSets(rsmIds)
    val inMemPepSetProvider = new InMemoryPeptideSetProvider(pepSets)

    // Load protein sets
    val protSetProvider = new SQLProteinSetProvider(msiDbCtx, psDbCtx, Some(pepSetProvider))
    val protSets = protSetProvider.getResultSummariesProteinSets(rsmIds)
    val protSetsByRsmId = protSets.groupBy(_.resultSummaryId)

    // Execute SQL query to load result sets
    val rsmQuery = new SelectQueryBuilder1(MsiDbResultSummaryTable).mkSelectQuery( (t,c) =>
      List(t.*) -> "WHERE "~ t.ID ~" IN("~ rsmIds.mkString(",") ~")"
    )

    val rsms = msiDbCtx.ezDBC.select(rsmQuery) { r =>

      // Retrieve some vars
      val rsmId: Int = r.getAnyVal(RSMCols.ID)
      val rsmPepSets = inMemPepSetProvider.getResultSummaryPeptideSets(rsmId)
      val rsmPepInsts = rsmPepSets.flatMap(_.getPeptideInstances).distinct
      val rsmProtSets = protSetsByRsmId.getOrElse(rsmId, Array.empty[ProteinSet])

      val decoyRsmId = r.getIntOrElse(RSMCols.DECOY_RESULT_SUMMARY_ID, 0)
      //val decoyRsmId = if( decoyRsmIdField != null ) decoyRsmIdField.asInstanceOf[Int] else 0
      
      val isQuantified = r.getBooleanOrElse(RSMCols.IS_QUANTIFIED,false)
      
      // Decode JSON properties
      val propertiesAsJSON = r.getString(RSMCols.SERIALIZED_PROPERTIES)
      val properties = if (propertiesAsJSON != null) Some(parse[ResultSummaryProperties](propertiesAsJSON)) else None
      
      val rsId = r.getInt(RSMCols.RESULT_SET_ID)
      
      var rsAsOpt = Option.empty[ResultSet]
      if (loadResultSet) {

        val pepMatchProvider = new SQLPeptideMatchProvider(msiDbCtx, psDbCtx)
        val protMatchProvider = new SQLProteinMatchProvider(msiDbCtx)

        val pepMatches = pepMatchProvider.getResultSummaryPeptideMatches(rsmId)
        val protMatches = protMatchProvider.getResultSummariesProteinMatches(Array(rsmId))

        // Remove objects which are not linked to result summary
        // TODO: remove when the provider is fully implemented
        val protMatchIdSet = rsmPepSets.flatMap(_.proteinMatchIds) toSet
        val rsmProtMatches = protMatches.filter { p => protMatchIdSet.contains(p.id) }

        rsAsOpt = Some(this.getResultSet(rsId, pepMatches, rsmProtMatches))
      }

      new ResultSummary(
        id = rsmId,
        description = r.getString(RSMCols.DESCRIPTION),
        isQuantified = isQuantified,
        modificationTimestamp = r.getDate(RSMCols.MODIFICATION_TIMESTAMP),
        peptideInstances = rsmPepInsts,
        peptideSets = rsmPepSets,
        proteinSets = rsmProtSets,
        resultSetId = rsId,
        resultSet = rsAsOpt,
        decoyResultSummaryId = decoyRsmId,
        properties = properties
      )

    }

    rsms.toArray

  }

  def getResultSummariesAsOptions(rsmIds: Seq[Int], loadResultSet: Boolean): Array[Option[ResultSummary]] = {
    val rsms = this.getResultSummaries(rsmIds, loadResultSet)
    val rsmById = rsms.map { rsm => rsm.id -> rsm } toMap;
    rsmIds.map { rsmById.get(_) } toArray
  }

  def getResultSetsResultSummaries(rsIds: Seq[Int], loadResultSet: Boolean): Array[ResultSummary] = {
    throw new Exception("NYI")
    null
  }*/

}