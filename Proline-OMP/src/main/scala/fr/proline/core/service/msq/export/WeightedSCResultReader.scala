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
    
    val quantRSMOpt = quantRsmProvider.getQuantResultSummary(mqchQuantRSMID, quantChIdsList.toArray, true).get
    _resultAsJSON = createJSonOutputResult(quantRSMOpt, rsmIdByQChId.result)
    true
  }

   private def createJSonOutputResult(msiQuantRSM : QuantResultSummary, rsmIdByQChId : Map[Long,Long]): String = {
          
     val jsonBuilder: StringBuilder = new StringBuilder(" \"{")
	 jsonBuilder.append(SpectralCountsJSONProperties.rootPropName).append(":{[")
	 
	  var firstQChOcc = true
	  	 
	 msiQuantRSM.quantChannelIds.foreach(qtChId =>{ // Go Through each QuantChannel (RSM)
	   
		 val rsmId = rsmIdByQChId(qtChId)
		 if (!firstQChOcc) { jsonBuilder.append(",") } else { firstQChOcc = false }
		 jsonBuilder.append("{").append(SpectralCountsJSONProperties.rsmIDPropName).append(":").append(rsmId).append(",") //save current RSM Id
      
		 // -- Save prots SC for current RSM          	
		 var firstPACOcc = true
		 jsonBuilder.append(SpectralCountsJSONProperties.protSCsListPropName).append(":[")

		 msiQuantRSM.masterQuantProteinSets.foreach(mqProtSet => {
		   //Go through All ProteinSets and extract only data for current QuantChanel
		   
		   //Get Typical Prot Accession
		   var typicalAcc : String = null
		   if( mqProtSet.proteinSet.getTypicalProteinMatch != null && mqProtSet.proteinSet.getTypicalProteinMatch.isDefined) 
			   typicalAcc = mqProtSet.proteinSet.getTypicalProteinMatch.get.accession		         
		   if(typicalAcc == null){
			   // Read it from DBs
			   val accJdbcWork = new JDBCWork() {
				   override def execute(con: Connection) {
					   val getTypAcc = "SELECT accession from protein_match WHERE id = ?"
					   val pStmt = con.prepareStatement(getTypAcc)	
					   pStmt.setLong(1, mqProtSet.proteinSet.getTypicalProteinMatchId)
					   val sqlResultSet = pStmt.executeQuery()
					   if (sqlResultSet.next){ //Should be One ! 
						   typicalAcc =  sqlResultSet.getString("accession")
					   }    		  
					   pStmt.close()
				   }            	         
			   } // End of jdbcWork anonymous inner class    
			   execCtx.getMSIDbConnectionContext().doWork(accJdbcWork, false)
		   }
		   
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
			 var protSetId : Long = -1
			 var protMatchId: Long = -1
			 var protMatchStatus : String = null
			 var protMatchPepNbr = -1
			 if (protQuant.get.proteinSetId.isDefined) {
				 protSetId=protQuant.get.proteinSetId.get
				 protMatchId = protQuant.get.proteinMatchId.getOrElse(-1)       
				 
				//Read Prot Status and Nbr Peptides from DBs
			   val protStatJdbcWork = new JDBCWork() {
				   override def execute(con: Connection) {
				      //---- Read Prot Status
					   val getProtStatus = "SELECT is_in_subset from protein_set_protein_match_item	 WHERE protein_match_id = ? AND protein_set_id = ?"
					   val pStmt = con.prepareStatement(getProtStatus)	
					   pStmt.setLong(1, protMatchId)
					   pStmt.setLong(2, protSetId)
					   val sqlResultSet = pStmt.executeQuery()
					   if (sqlResultSet.next){ //Should be One ! 
						   val isInSubset =  sqlResultSet.getBoolean("is_in_subset")
						   if(isInSubset) {
						     protMatchStatus = "Subset"
						   } else {
							   // Verify if SameSet or Typical 
							   val getProtTypical = "SELECT typical_protein_match_id from protein_set WHERE id = ?"
							   val pStmt2 = con.prepareStatement(getProtTypical)	
							   pStmt2.setLong(1, protSetId)					   
							   val sqlResultSet2 = pStmt2.executeQuery()
							   if (sqlResultSet2.next){ 
							       val protSetTypID = sqlResultSet2.getLong("typical_protein_match_id")
							       if(protSetTypID.equals(protMatchId) )
							    	   protMatchStatus = "Typical"
						    	   else
						    	     protMatchStatus = "Sameset"
							   }
							   pStmt2.close()
						   }
					   } else //NO LINK Between ProtSet and ProtMatch : Error !
						   protMatchStatus ="NOT FOUND !"+protSetId						
					   pStmt.close()		
					   
					      //---- Read Prot Status
					   val getPepCount = "SELECT peptide_count from peptide_set_protein_match_map pspmm, peptide_set "+
					   "WHERE pspmm.protein_match_id = ? and pspmm.result_summary_id = ?  and peptide_set.id = pspmm.peptide_set_id"
					   val pStmt2 = con.prepareStatement(getPepCount)
					   pStmt2.setLong(1, protMatchId)	
					   pStmt2.setLong(2, rsmId)					   
					   val sqlResultSet2 = pStmt2.executeQuery()
					   if (sqlResultSet2.next){ 
					     protMatchPepNbr = sqlResultSet2.getInt("peptide_count")
					   }
				   }            	         
				 } // End of jdbcWork anonymous inner class    
				 execCtx.getMSIDbConnectionContext().doWork(protStatJdbcWork, false)

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