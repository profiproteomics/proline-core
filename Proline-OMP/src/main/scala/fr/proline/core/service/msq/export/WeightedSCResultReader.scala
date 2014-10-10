package fr.proline.core.service.msq.export

import java.sql.Connection

import scala.collection.mutable.ArrayBuffer

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.util.serialization.ProfiJson
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.om.model.msq.MasterQuantChannelProperties
import fr.proline.core.om.model.msq.QuantResultSummary
import fr.proline.core.om.provider.msq.impl.SQLQuantResultSummaryProvider
import fr.proline.core.service.msq.quantify.SpectralCountsJSONProperties
import fr.proline.repository.util.JDBCWork

class WeightedSCResultReader (
    execCtx: IExecutionContext, 
    datasetId : Long ) extends IService with Logging {

  	/**
 	* "{"spectral_count_result":{[ 
  	* { "rsm_id":Long, "proteins_spectral_counts":[ { "protein_accession"=Acc, "prot_match_id"=Long, "prot_set_id"=Long, "prot_status"=String,"pep_nbr"=Int,"bsc"=Float,"ssc"=Float,"wsc"=Float}, {...} ] },
  	*	{ "rsm_id"... } 
  	* ]}}"    
  	*/

  private var _resultAsJSON: String = null
  def getResultAsJSON() = { _resultAsJSON }
  
  private var _refRSMId : Long = -1L
  def getIdfRSMReferenceId() = { _refRSMId }
  
  private var _refDSId : Long = -1L
  def getIdfDatasetReferenceId() = { _refDSId }

  def runService(): Boolean = {
    logger.info("Retrive SpectralCount Result for DataSet "+datasetId)
    
    // Read Quantitation information from UDS
    var mqcSerializedProp : String  = null
    var mqchQuantRSMID : java.lang.Long = null
    var quantChIdsList = new ArrayBuffer[Long]()
    var rsmIdByQChId = Map.newBuilder[Long,Long]
     
    logger.debug(" Get UDS Quant information ")    
    val jdbcWork = new JDBCWork() {
      override def execute(con: Connection) {
        val getMQChQuery = "SELECT id, quant_result_summary_id, serialized_properties from master_quant_channel WHERE quantitation_id = ?"
        val pStmt = con.prepareStatement(getMQChQuery)
        pStmt.setLong(1, datasetId)
        val sqlResultSet = pStmt.executeQuery()
        if (sqlResultSet.next){ //Should be One ! 
          val mqchId = sqlResultSet.getLong("id")
          mqcSerializedProp = sqlResultSet.getString("serialized_properties")
          mqchQuantRSMID = sqlResultSet.getLong("quant_result_summary_id")
          
          
          val getQChListQuery = "SELECT id, ident_result_summary_id from quant_channel WHERE quantitation_id = ? AND master_quant_channel_id = ?"
          val pStmt2 = con.prepareStatement(getQChListQuery)
          pStmt2.setLong(1, datasetId)
          pStmt2.setLong(2, mqchId)  
          val sqlResultSet2 = pStmt2.executeQuery()
       	  while (sqlResultSet2.next){
       	    val qChId = sqlResultSet2.getLong("id")
       	    val idfRsmOd = sqlResultSet2.getLong("ident_result_summary_id")
       	    quantChIdsList += qChId
       	    rsmIdByQChId += qChId -> idfRsmOd
       	  }
        }
        pStmt.close()               
        
      }      
    } // End of jdbcWork anonymous inner class    
    execCtx.getUDSDbConnectionContext().doWork(jdbcWork, false)
          
    require(mqchQuantRSMID != null && mqchQuantRSMID!=0, "undefined quantitation result summary for dataset id=" + datasetId)

    // Read reference Dataset and RSM idf ID from master quant channel    
    try {
	   if(mqcSerializedProp != null){
	     val mqChProp =  ProfiJson.deserialize[MasterQuantChannelProperties](mqcSerializedProp)
	     _refRSMId = if(mqChProp.getIdentResultSummaryId.isDefined) mqChProp.getIdentResultSummaryId.get else -1l
	     _refDSId = if(mqChProp.getIdentDatasetId.isDefined) mqChProp.getIdentDatasetId.get else -1l
	     logger.info("Retrive SpectralCount Result : _refRSMId "+_refRSMId+" _refDSId "+_refDSId)
	   }
	  
    } catch {
    	case e: Exception=> {
    	  _refRSMId = -1l
    	  _refDSId = -1l
    	  logger.error(" Read reference Dataset and RSM Failed "+e)
    	  e.printStackTrace()
    	  
    	}
    }
    
    
    //Read quantitation RSM and associated QuantData 
    val quantRsmProvider = new SQLQuantResultSummaryProvider(
      execCtx.getMSIDbConnectionContext,
      execCtx.getPSDbConnectionContext,
      execCtx.getUDSDbConnectionContext
    )
    
    logger.debug(" Read UDS Quant Data ")
    val quantRSMOpt = quantRsmProvider.getQuantResultSummary(mqchQuantRSMID, quantChIdsList.toArray, true).get
    
    logger.debug(" createJSonOutputResult for Quant Data ")
    _resultAsJSON = createJSonOutputResult(quantRSMOpt, rsmIdByQChId.result)
    true
  }

  private def createJSonOutputResult(msiQuantRSM: QuantResultSummary, rsmIdByQChId: Map[Long, Long]): String = {

    val jsonBuilder: StringBuilder = new StringBuilder(" \"{")
    jsonBuilder.append(SpectralCountsJSONProperties.rootPropName).append(":{[")

    var firstQChOcc = true

    //------  Go Through each QuantChannel (RSM)
    msiQuantRSM.quantChannelIds.foreach(qtChId => { 

      val rsmId = rsmIdByQChId(qtChId)
      if (!firstQChOcc) { jsonBuilder.append(",") } else { firstQChOcc = false }
      jsonBuilder.append("{").append(SpectralCountsJSONProperties.rsmIDPropName).append(":").append(rsmId).append(",") //save current RSM Id

      // -- Save prots SC for current RSM          	
      var firstPACOcc = true
      jsonBuilder.append(SpectralCountsJSONProperties.protSCsListPropName).append(":[")

      //-- Get all proteinSets Typical Accession
      val typicalAccByProtSetIdBuiler = Map.newBuilder[Long, String]
      val allTypicalId = msiQuantRSM.masterQuantProteinSets.map(_.proteinSet.getTypicalProteinMatchId)
      val accJdbcWork = new JDBCWork() {
        override def execute(con: Connection) {
          val getTypAcc = "SELECT accession, id from protein_match WHERE id IN (" + allTypicalId.mkString(",") + ")"
          val stmt = con.createStatement()
          val sqlResultSet = stmt.executeQuery(getTypAcc)
          while (sqlResultSet.next) {
            val typAcc = sqlResultSet.getString("accession")
            val typId = sqlResultSet.getLong("id")
            typicalAccByProtSetIdBuiler += typId -> typAcc
          }
          stmt.close()
        }
      } // End of jdbcWork anonymous inner class    
      execCtx.getMSIDbConnectionContext().doWork(accJdbcWork, false)
      val typicalAccByProtSetId = typicalAccByProtSetIdBuiler.result

      val quantProteinSetInfoByProtSetMatchTupleIdBuilder = Map.newBuilder[(Long,Long), (Boolean, Long)]
      val protMatchPepNbrByProtMatchIdBuilder = Map.newBuilder[Long, Int]      
      //Read Prot Status and Nbr Peptides from DBs
        val protStatJdbcWork = new JDBCWork() {
          override def execute(con: Connection) {
                //---- Read Prot Status

                val getProtStatus = "SELECT protein_set_id, protein_match_id, is_in_subset, typical_protein_match_id FROM protein_set_protein_match_item, protein_set " +
                  " WHERE protein_set.id = protein_set_protein_match_item.protein_set_id " +
                  " AND protein_set_protein_match_item.result_summary_id = ? "
                val pStmt = con.prepareStatement(getProtStatus)
                pStmt.setLong(1, rsmId)

                val sqlResultSet = pStmt.executeQuery()
                while (sqlResultSet.next) {  
                  val isInSubset = sqlResultSet.getBoolean("is_in_subset")
                  val protSetTypID = sqlResultSet.getLong("typical_protein_match_id")
                  val protMatchId = sqlResultSet.getLong("protein_match_id")
                  val protSetId = sqlResultSet.getLong("protein_set_id")
                  quantProteinSetInfoByProtSetMatchTupleIdBuilder += (protSetId,protMatchId) -> (isInSubset,protSetTypID)
                }
                pStmt.close()

                //---- Read Prot Status
                val getPepCount = "SELECT peptide_count, protein_match_id FROM peptide_set_protein_match_map pspmm, peptide_set " +
                  "WHERE  pspmm.result_summary_id = ?  AND peptide_set.id = pspmm.peptide_set_id"
                val pStmt2 = con.prepareStatement(getPepCount)
                pStmt2.setLong(1, rsmId)
                val sqlResultSet2 = pStmt2.executeQuery()
                while (sqlResultSet2.next) {
                  val protMatchPepNbr = sqlResultSet2.getInt("peptide_count")
                  val protMatchId = sqlResultSet2.getLong("protein_match_id")
                  protMatchPepNbrByProtMatchIdBuilder +=protMatchId-> protMatchPepNbr
                }
                pStmt2.close()
              }
            } // End of jdbcWork anonymous inner class    
            execCtx.getMSIDbConnectionContext().doWork(protStatJdbcWork, false)
            
            val quantProteinSetInfoByProtSetMatchTupleId = quantProteinSetInfoByProtSetMatchTupleIdBuilder.result
    		val protMatchPepNbrByProtMatchId =protMatchPepNbrByProtMatchIdBuilder.result
            
          //---Go through All ProteinSets and extract only data for current QuantChanel
      msiQuantRSM.masterQuantProteinSets.foreach(mqProtSet => {

        //Get Typical Prot Accession
        var typicalAcc: String = typicalAccByProtSetId(mqProtSet.proteinSet.getTypicalProteinMatchId)

        //Get QuantProteinSet for current ProteinSet in current QuantChannel
        val protQuant = mqProtSet.quantProteinSetMap.get(qtChId)
        if (protQuant.isDefined) {
          // This prot is quantified un current QuantChannel
          if (!firstPACOcc) {
            jsonBuilder.append(",")
          } else {
            firstPACOcc = false
          }

          //Properties to ge
          var protSetId: Long = -1
          var protMatchId: Long = -1
          var protMatchStatus: String = null
          var protMatchPepNbr = -1
          if (protQuant.get.proteinSetId.isDefined) {
            protSetId = protQuant.get.proteinSetId.get
            protMatchId = protQuant.get.proteinMatchId.getOrElse(-1)
            val (isInSubset,protSetTypID) = quantProteinSetInfoByProtSetMatchTupleId.getOrElse((protSetId,protMatchId),(false,-1))
            if (isInSubset) {
              protMatchStatus = "Subset"
            } else if (protSetTypID.equals(protMatchId)) { //is the typical 
              protMatchStatus = "Typical"
            } else
              protMatchStatus = "Sameset"

              protMatchPepNbr = protMatchPepNbrByProtMatchId.getOrElse(protMatchId, -1)

          } // End protQuant.get.proteinSetId.isDefined
          jsonBuilder.append("{").append(SpectralCountsJSONProperties.protACPropName).append("=").append(typicalAcc).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.protMatchId).append("=").append(protMatchId).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.protSetId).append("=").append(protSetId).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.protMatchStatus).append("=").append(protMatchStatus).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.pepNbr).append("=").append(protMatchPepNbr).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.bscPropName).append("=").append(protQuant.get.peptideMatchesCount).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.sscPropName).append("=").append(protQuant.get.rawAbundance).append(",")
          jsonBuilder.append(SpectralCountsJSONProperties.wscPropName).append("=").append(protQuant.get.abundance).append("}")
        } //End if Prot quantified in QCh
      }) //End Go through All ProteinSets

      jsonBuilder.append("]") //End protAC list for current RSM
      jsonBuilder.append("}") //End current RSM properties

    }) //End GoThrough RSMs/QChs
    jsonBuilder.append("]}}\"") //End SpectralCountResult array properties
    jsonBuilder.result

  }
}