package fr.proline.core.service.msq

import java.sql.Connection
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import com.weiglewilczek.slf4s.Logging
import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.repository.util.JDBCWork
import fr.proline.core.om.provider.ProviderDecoratedExecutionContext
import fr.proline.core.om.provider.msi.IResultSetProvider
import fr.proline.core.om.model.msi.ResultSet
import scala.collection.mutable.MapBuilder
import fr.proline.core.om.provider.msi.impl.SQLPeptideSetProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideInstanceProvider
import fr.proline.core.om.provider.msi.impl.SQLPeptideProvider

class WeightedSCCalculatorWId (
	execContext: IExecutionContext,
	referenceRSMId: Long,
	rsmIdsToCalculate : Seq[Long]
  
) extends IService with Logging{ 

 var fullService : WeightedSCCalculator = null
 def wscByProtMatchAccessionByRSM : Map[Long, Map[String, SpectralCountsStruct]] = if(fullService!= null) {fullService.wscByProtMatchAccessionByRSM} else Map.empty[Long, Map[String, SpectralCountsStruct]]
 def getResultAsJSON() =  { if(fullService!= null) {fullService.getResultAsJSON} else "" }
  
    
 def runService(): Boolean = { 
    require(rsmIdsToCalculate!=null && referenceRSMId != 0 &&  rsmIdsToCalculate.length > 0)
    
    val rsmProvider = getResultSummaryProvider(execContext)
    val referenceRSMOpt : Option[ResultSummary] = rsmProvider.getResultSummary(referenceRSMId,true)
    if (referenceRSMOpt.isEmpty) {
    	throw new IllegalArgumentException("Unknown ResultSummary Id: " + referenceRSMId)
    }

    val rsmToCalculateBuilder = Seq.newBuilder[ResultSummary]
    rsmIdsToCalculate.foreach(rsmId => {
      val nextRSMOpt = rsmProvider.getResultSummary(rsmId,true)
       if (nextRSMOpt.isEmpty) {
    	throw new IllegalArgumentException("Unknown ResultSummary Id: " + rsmId)
       }
      rsmToCalculateBuilder  += nextRSMOpt.get
    })
    
    fullService = new WeightedSCCalculator(
      execContext,
      referenceRSMOpt.get,
      rsmToCalculateBuilder.result
    )
    
    fullService.runService
    
  }

  // TODO ? Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSummaryProvider(execContext: IExecutionContext): IResultSummaryProvider = {

    new SQLResultSummaryProvider(msiDbCtx = execContext.getMSIDbConnectionContext,
      psDbCtx = execContext.getPSDbConnectionContext,
      udsDbCtx = execContext.getUDSDbConnectionContext)

  }
}

class WeightedSCCalculator (
  execContext: IExecutionContext,
  referenceRSM: ResultSummary,
  rsmToCalculate : Seq[ResultSummary]
  
) extends IService with Logging{ 

  override protected def  beforeInterruption  = {  }

  private var _wscByProtMatchAccessionByRSM : scala.collection.mutable.Map[Long, Map[String, SpectralCountsStruct]] = scala.collection.mutable.Map.empty[Long, Map[String, SpectralCountsStruct]] 
  private var _resultAsJSON : String = null
  
  /** 
   *  Spectral Count Result 
   *  {RSM ID -> { ProteinMatchAccession -> (Basic SC; Specific SC, Weighted SC)} }  
   */
  def wscByProtMatchAccessionByRSM : Map[Long, Map[String, SpectralCountsStruct]] = {_wscByProtMatchAccessionByRSM.toMap }
  
  def getResultAsJSON() =  {_resultAsJSON}
  
  def runService(): Boolean = { 

	  // -- Create ProteinPepsWeightStruct from reference RSM
	 val proteinSetWeightStructsById = createProteinPepsWeightStructs(true)
    
	 // Compute SpectralCount for each RSM
	 rsmToCalculate.foreach(rsm => {
	   
	   //Store result in Map (ProtMatch Accession -> Weight Spectral Count)
	    var wscByProtMatchAccession : scala.collection.mutable.Map[String, SpectralCountsStruct] = scala.collection.mutable.Map[String, SpectralCountsStruct]()
	    
		 //--- Update RSM SpectralCount
	    PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsm, execContext)

	    //--- Get RSM Protein Match information 	     
	    // map   list of ProtMatch accession by PeptideSet
	    val pepSetByPMAccList : Map[PeptideSet, Seq[String]] = createProtMatchesAccByPeptideSet(rsm)
	    
	    //--- Calculate weight SC
	    proteinSetWeightStructsById.foreach(entry =>{
	      val currentProteinSetWeightStruct = entry._2
	      
	       var peptideSetForPM : PeptideSet = null //PeptideSet containing protein in current RSM
	       
	       val pepSetIt  = pepSetByPMAccList.iterator	       
	       while(pepSetIt.hasNext && peptideSetForPM==null){
	         val nextEntry : (PeptideSet, Seq[String]) = pepSetIt.next
	         if(nextEntry._2.contains(currentProteinSetWeightStruct.typicalPMAcc))
	        	 peptideSetForPM = nextEntry._1
	       }
	       
	      // ProteinMatch Spectral Count
	      var protWSC : Float = 0.0f 
	      var protSSC : Float = 0.0f 
	      var protBSC : Float = 0.0f
	       if( peptideSetForPM!=null){	         
	         //Go through peptides and compute SC
	    	 peptideSetForPM.getPeptideInstances.foreach(pepInst =>{
	    	   protBSC += pepInst.totalLeavesMatchCount
	    	   val weight =  currentProteinSetWeightStruct.weightByPeptideId.get(pepInst.peptideId).get
	    	   
	    	   if(weight == 1) //FIXME VDS : OK if we use weight on specificity... Maybe this information (specific or not) should be saved in  ProteinPepsWeightStruct
	    		   protSSC += pepInst.totalLeavesMatchCount
	    		   
	    	   protWSC += (pepInst.totalLeavesMatchCount *weight)
	    	 })
	       } else{ //Protein not identified in current RSM
	         protWSC = Float.NaN
	       }
	      
	      wscByProtMatchAccession +=currentProteinSetWeightStruct.typicalPMAcc -> new SpectralCountsStruct(protBSC, protSSC, protWSC)

	    }) // End go through  proteinSetWeightStructsById
	    
	    _wscByProtMatchAccessionByRSM += rsm.id -> wscByProtMatchAccession.toMap //Save Result
	     
	 }) //End go through RSMs
	 
	_resultAsJSON = generateJSONOutput(_wscByProtMatchAccessionByRSM.result.toMap)
	val printDebug = generateCSVOutput(_wscByProtMatchAccessionByRSM.result.toMap)
	logger.debug(printDebug)
    true
     
  }

       
     /*
      *   "name" : "SpectralCountResult",
  		   "type" : "array",   "items" :  {
               "rsm_id" : {"required" : "true","type" : "number"    },
	            "proteins_spectral_counts": { "required" : "true", "type" : "array", 
	                   "items" : { "name" : protein_spectral_count", "properties" : {
	                    	"protein_accession" : {"required" : "true","type" : "string"},
							"bsc": {"required" : "true","type" : "number" },
							"ssc": { "required" : "true", "type" : "number" },
							"wsc": { "required" : "true", "type" : "number" }			
							}
						}
    			}
  			}
      */
   private def generateJSONOutput( spectralCountsByProtMatchAccessionByRSM:Map[Long, Map[String, SpectralCountsStruct]]) : String = {     
     val jsonBuilder : StringBuilder = new StringBuilder(" \"{")
     jsonBuilder.append(SpectralCountsProperties.rootPropName).append(":{[")
     
     var firstOcc = true
     //-- Go through each RSM Results
     spectralCountsByProtMatchAccessionByRSM.foreach(mapEntry =>{
    	 if(!firstOcc){  jsonBuilder.append(",") } else { firstOcc = false }
       
    	 jsonBuilder.append("{").append(SpectralCountsProperties.rsmIDPropName).append(":").append(mapEntry._1).append(",") //save current RSM Id
        
        // -- Save prots SC for current RSM          	
    	var firstOccPAC = true
        jsonBuilder.append(SpectralCountsProperties.protSCsListPropName).append(":[")   
        
        mapEntry._2.foreach(protSCsList =>{
        	if(!firstOccPAC){  jsonBuilder.append(",") } else { firstOccPAC = false }
        	val protAC = protSCsList._1
			val protSCs = protSCsList._2
			jsonBuilder.append("{").append(SpectralCountsProperties.protACPropName).append("=").append(protAC).append(",")
			jsonBuilder.append(SpectralCountsProperties.bscPropName).append("=").append(protSCs.basicSC).append(",")
			jsonBuilder.append(SpectralCountsProperties.sscPropName).append("=").append(protSCs.specificSC).append(",")
			jsonBuilder.append(SpectralCountsProperties.wscPropName).append("=").append(protSCs.weightedSC).append("}")
        })

        jsonBuilder.append("]") //End protAC list for current RSM
        jsonBuilder.append("}") //End current RSM properties
     }) 
  
     jsonBuilder.append("]}}\"") //End SpectralCountResult array properties
     jsonBuilder.result
     
   }
   
  private def generateCSVOutput( spectralCountsByProtMatchAccessionByRSM:Map[Long, Map[String, SpectralCountsStruct]]) : String = {     
     val jsonBuilder : StringBuilder = new StringBuilder("START OUTPUT \n")
     jsonBuilder.append(SpectralCountsProperties.rootPropName).append("\n")
     
     var firstOcc = true
     //-- Go through each RSM Results
     spectralCountsByProtMatchAccessionByRSM.foreach(mapEntry =>{
    	jsonBuilder.append(SpectralCountsProperties.rsmIDPropName).append("\t").append(mapEntry._1).append("\n")
        jsonBuilder.append(SpectralCountsProperties.protACPropName).append("\t").append(SpectralCountsProperties.bscPropName).append("\t").append(SpectralCountsProperties.sscPropName).append("\t").append(SpectralCountsProperties.wscPropName).append("\n")
        // -- Save prots SC for current RSM          	
    	
        mapEntry._2.foreach(protSCsList =>{        
        	val protAC = protSCsList._1
			val protSCs = protSCsList._2
			jsonBuilder.append(protAC).append("\t")
			jsonBuilder.append(protSCs.basicSC).append("\t")
			jsonBuilder.append(protSCs.specificSC).append("\t")
			jsonBuilder.append(protSCs.weightedSC).append("\n")
        })

        jsonBuilder.append("\n\n") //End protAC list for current RSM
     }) 
  
     jsonBuilder.append("END OUTPUT \n") //End SpectralCountResult array properties
     jsonBuilder.result
     
   }
  
  private def createProtMatchesAccByPeptideSet(rsm: ResultSummary) : Map[PeptideSet, Seq[String]] = {
	  val rs =   if(rsm.resultSet.isDefined){ rsm.resultSet.get } else { loadRS(rsm.getResultSetId)   }
	  val protMById= rs.proteinMatchById
	  val result = scala.collection.mutable.Map[PeptideSet, Seq[String]]()      
     
     var protMatchIdByPepSet : Map[PeptideSet, Array[Long]] = null
     
     rsm.proteinSets.foreach( protSet =>{
       
        try {
    	 protMatchIdByPepSet = protSet.getAllProteinMatchesIdByPeptideSet
        } catch {
          case e:IllegalAccessException =>{
            logger.debug(" Unable to get ProteinMatches from ProtSet. Try using connection")
            protMatchIdByPepSet = getProtMatchesIdByPeptideSet(protSet.peptideSet, execContext)
          } 
        }
        
        //Go through Map : PeptideSet => Array[ProteinMatchId]  
        protMatchIdByPepSet.foreach( nEntry=>{
		   val nPepSet = nEntry._1
    	   var strSeq : Seq[String] = result.getOrElseUpdate(nEntry._1, Seq.empty[String])
    	   
    	   nEntry._2.foreach(pmId =>{ //For each ProtMatchID
    	     val pmAcc = protMById.get(pmId).get.accession
    	     if(!strSeq.contains(pmAcc)){
    	       val seqBuilder = Seq.newBuilder[String]
    	       seqBuilder ++= strSeq
    	       seqBuilder += pmAcc  
    	       strSeq = seqBuilder.result
    	       result.put(nEntry._1,strSeq)
    	     }
    	     
    	   })
    	 }) // End Go through Map : PeptideSet => Array[ProteinMatchId]  
     }) //End go through ProteinSets
     
     result.toMap
   }
   
   private def loadRS( rsID: Long) : ResultSet = {    
      val providerContext = ProviderDecoratedExecutionContext(execContext) // Use Object factory
 
      val rsProvider :IResultSetProvider=  providerContext.getProvider(classOf[IResultSetProvider])
	  val resultRS = rsProvider.getResultSet(rsID)
      if(resultRS.isDefined){
        resultRS.get
      } else{
        logger.warn(" !!! Unable to get search result with id "+rsID)
        null
      }        
     
   }
   
    def getProtMatchesIdByPeptideSet (rootPepSet : PeptideSet, execContext : IExecutionContext) :   Map[PeptideSet,Array[Long]] = {
	  val resultMapBuilder = Map.newBuilder[PeptideSet,Array[Long]]
	  resultMapBuilder += rootPepSet -> rootPepSet.proteinMatchIds
	  	  
	  if(rootPepSet.hasStrictSubset) { //Get PeptideSet strictSubsets
		  var pepSets : ArrayBuffer[PeptideSet] = new ArrayBuffer[PeptideSet]()
		  
		  if(rootPepSet.strictSubsets == null || !rootPepSet.strictSubsets.isDefined){ //Read from ConnectionContext		    
		    val pepInstProv = new SQLPeptideInstanceProvider(msiSqlCtx =execContext.getMSIDbConnectionContext() ,peptideProvider=new SQLPeptideProvider(psDbCtx = execContext.getPSDbConnectionContext())  )
		    val pepSetProvider = new SQLPeptideSetProvider(msiDbCtx = execContext.getMSIDbConnectionContext() , peptideInstanceProvider = pepInstProv)
		    pepSets ++= pepSetProvider.getPeptideSets(rootPepSet.strictSubsetIds)
		  } else {
			  pepSets ++= rootPepSet.strictSubsets.get
		  }
		  
		  pepSets.foreach(pepSet => { //recursively get PeptideSets subsets...  
			  resultMapBuilder ++= getProtMatchesIdByPeptideSet( pepSet, execContext)
		  })        
	  }      
     
	  resultMapBuilder.result   
 }	
 
    
  /**
   * Create a Map of ProteinPepsWeightStruct by ProteinSet Id.
   * For each referenceRSM's ProteinSet create a ProteinPepsWeightStruct which contains
   *  - the ProteinSet
   *  - the typicalProteinMatch Accession
   *  And if referenceRSM is also reference for peptide specificity :
   *  - the number of specific peptides
   *  - the list of peptides identifying the ProteinSet with their weight 
   *  @param referenceForPeptides specifiy if referenceRSM is also reference for peptide specificity
   */
  
  private def createProteinPepsWeightStructs(referenceForPeptides : Boolean) : Map[Long,ProteinPepsWeightStruct] = {

    //ProteinPepsWeightStruct for each RSM ProteinSet referenced by ProteinSet id  
    val proteinPepsWeightStructsByProtSetId = Map.newBuilder[Long,ProteinPepsWeightStruct]
    
    // Map each peptide to the list of identified ProteinSet id 
    val protSetIdByPepId = new HashMap[Long, ArrayBuffer[Long]]()
    
    //****  For each ProteinSet, initialize a ProteinPepsWeightStruct and create Maps
    referenceRSM.proteinSets.foreach( protSet =>{
      //Map of weight by peptide Id
      val weightByPepId = new HashMap[Long,Float]()
      
      //-- Get Typical Protein Match Accession 
      var pmAccession : String = null
      if(protSet.typicalProteinMatch!=null && protSet.typicalProteinMatch.isDefined){
    	  pmAccession = protSet.typicalProteinMatch.get.accession 
      } else {    	  
    	  val jdbcWork = new JDBCWork() {
        	  override def execute(con: Connection) {
        		  val getPMQuery = "SELECT accession from protein_match WHERE id = ?"
        		  val pStmt = con.prepareStatement(getPMQuery)
        		  pStmt.setLong(1, protSet.getTypicalProteinMatchId)
        		  val sqlResultSet = pStmt.executeQuery()        	
        		  if(sqlResultSet.next)
        			 pmAccession= sqlResultSet.getString(1)
    			  pStmt.close()
        	  }
   
          } // End of jdbcWork anonymous inner class    	  
          execContext.getMSIDbConnectionContext().doWork(jdbcWork, false)

      } //END TypicalProteinMatch not defined
     
      //-- Get peptide specific count and create Map : peptide => List ProtSet.Id identified by peptide
      var nbrPepSpecif : Int =0     
      if(referenceForPeptides){
	      protSet.peptideSet.getPeptideInstances.foreach(pepI =>{
	    	  val proSetIds = protSetIdByPepId.getOrElseUpdate(pepI.peptideId, new ArrayBuffer[Long])
	    	  proSetIds += protSet.id
	    	  if (pepI.validatedProteinSetsCount == 1) {
	    	    nbrPepSpecif +=1 
	    	  }
	    	  weightByPepId += pepI.peptideId -> 0.0f
	      })
      }

      proteinPepsWeightStructsByProtSetId += protSet.id -> new ProteinPepsWeightStruct(proteinSet = protSet,typicalPMAcc=pmAccession,nbrPepSpecific=nbrPepSpecif, weightByPeptideId = weightByPepId )
      
    }) // End ProteinPepsWeightStruct initialization 
    
    val resultStruct : Map[Long,ProteinPepsWeightStruct]= proteinPepsWeightStructsByProtSetId.result
    
    //**** Compute Peptides Weight if referenceRSM also used for peptide
   if(referenceForPeptides) computePeptideWeight(resultStruct,protSetIdByPepId)
        
    resultStruct 
  }


  /**
   * Compute Peptide's Weight for each identified ProteinSet
   * 
   *  If peptide is a specific  ProteinSet, the corresponding weight will be 1
   *  Else if peptide is shared between multiple ProteinSets the weight = # specific pep of ProtSet / Sum ( #specific pep of all ProtSet identified by this pep)
   *  
   *  @param  proteinWeightStructByProtSetId Map ProteinPepsWeightStruct by ProteinSetId in peptode reference RSM. ProteinPepsWeightStruct should be updated
   *  @param  protSetIdByPep For each Peptide (id) references list of ProteinSet (Id) identified by this peptide
   */
  def computePeptideWeight(proteinWeightStructByProtSetId : Map[Long,ProteinPepsWeightStruct], protSetIdByPep: HashMap[Long,ArrayBuffer[Long]]) : Unit = {
    
	  proteinWeightStructByProtSetId.foreach( entry =>{
		  val currentProteinWeightStruct =  entry._2
		  //Calculate  weight for each peptide identifying this ProteinSet
		  currentProteinWeightStruct.weightByPeptideId.foreach( weightMapEntry => {
			  val pepId = weightMapEntry._1      
			  if(protSetIdByPep.get(pepId).get.length == 1){ // specific peptide, weight =1
				  currentProteinWeightStruct.weightByPeptideId(pepId) =1.0f
			  } else { 
				  //Not specific peptide,  weight = nbr pepSpecific of current ProtSet / Sum ( nbr pepSpecific of all ProtSet identified by this pep)
				  var sumNbrSpecificPeptides = 0
				  protSetIdByPep.get(pepId).get.foreach( protSetId =>{
					  sumNbrSpecificPeptides += proteinWeightStructByProtSetId.get(protSetId).get.nbrPepSpecific
				  })
				  
				  if(sumNbrSpecificPeptides>0)
					  currentProteinWeightStruct.weightByPeptideId.put(pepId, (currentProteinWeightStruct.nbrPepSpecific.toFloat / sumNbrSpecificPeptides.toFloat))
				  else
				    currentProteinWeightStruct.weightByPeptideId.put(pepId, 0)
			  }
		  }) //End go through ProteinSet Peptides
	  })// End go through  ProteinSet (ProteinPepsWeightStruct)      
  }
  
}

case class SpectralCountsStruct( val basicSC : Float, val specificSC : Float, val weightedSC : Float)

case class ProteinPepsWeightStruct(
			val proteinSet: ProteinSet, 
			val typicalPMAcc: String, 
			val nbrPepSpecific: Int, 
			val weightByPeptideId: scala.collection.mutable.Map[Long, Float] = null)

object SpectralCountsProperties {
    final val rootPropName : String = "\"SpectralCountResult\""
    final val rsmIDPropName : String = "\"rsm_id\""
    final val protSCsListPropName : String = "\"proteins_spectral_counts\""
    final val protACPropName : String = "\"protein_accession\""
    final val bscPropName : String = "\"bsc\""
    final val sscPropName : String = "\"ssc\""
    final val wscPropName : String = "\"wsc\""
           
}