package fr.proline.core.service.msq

import java.sql.Connection

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.Map

import com.weiglewilczek.slf4s.Logging

import fr.proline.api.service.IService
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.spectralcount.IPepInstanceSpectralCountUpdater
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.dal.SQLConnectionContext
import fr.proline.core.om.model.msi.ProteinSet
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.provider.msi.IResultSummaryProvider
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.repository.util.JDBCWork

class WeightedSCCalculatorWId (
	execContext: IExecutionContext,
	referenceRSMId: Int,
	rsmIdsToCalculate : Seq[Int]
  
) extends IService with Logging{ 

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
    
    val fullService = new WeightedSCCalculator(
      execContext,
      referenceRSMOpt.get,
      rsmToCalculateBuilder.result
    )
    
    fullService.runService
    
  }

  // TODO Retrieve a ResultSetProvider from a decorated ExecutionContext ?
  private def getResultSummaryProvider(execContext: IExecutionContext): IResultSummaryProvider = {

    new SQLResultSummaryProvider(execContext.getMSIDbConnectionContext.asInstanceOf[SQLConnectionContext],
      execContext.getPSDbConnectionContext.asInstanceOf[SQLConnectionContext],
      execContext.getUDSDbConnectionContext.asInstanceOf[SQLConnectionContext])

  }

}

class WeightedSCCalculator (
  execContext: IExecutionContext,
  referenceRSM: ResultSummary,
  rsmToCalculate : Seq[ResultSummary]
  
) extends IService with Logging{ 

  override protected def  beforeInterruption  = {  }

  def runService(): Boolean = { 

	  // -- Create ProteinPepsWeightStruct from reference RSM
	 val proteinSetWeightStructsById = createProteinPepsWeightStructs()
    
	 val scUpdater : IPepInstanceSpectralCountUpdater = PepInstanceFilteringLeafSCUpdater
	 // Compute SpectralCount for each RSM
	 rsmToCalculate.foreach(rsm => {
	    var wscByProtMatchAccession : Map[String, Float] = scala.collection.mutable.Map[String, Float]()
		 //--- Update RSM SpectralCount
	    scUpdater.updatePepInstanceSC(rsm, execContext)
	    
	    //--- Calculate weight SC
	    
		 
	 })
	 
    false
     
  }
  
  /**
   * Create a Map of ProteinPepsWeightStruct by ProteinSet Id.
   * For each referenceRSM's ProteinSet create a ProteinPepsWeightStruct which contains
   *  - the ProteinSet
   *  - the typicalProteinMatch Accession
   *  - the number of specific peptides
   *  - the list of peptides identifying the ProteinSet with their weight 
   */
  
  private def createProteinPepsWeightStructs() : Map[Int,ProteinPepsWeightStruct] = {

    //ProteinPepsWeightStruct for each RSM ProteinSet referenced by ProteinSet id  
    val proteinPepsWeightStructsByProtSetId = Map.newBuilder[Int,ProteinPepsWeightStruct]
    
    // Map each peptide to the list of identified ProteinSet id 
    val protSetIdByPepId : HashMap[Int, ArrayBuffer[Int]] = new HashMap[Int, ArrayBuffer[Int]]()
    
    //****  For each ProteinSet, initialize a ProteinPepsWeightStruct and create Maps
    referenceRSM.proteinSets.foreach( protSet =>{
      //Map of weight by peptide Id
      val weightByPepId = new HashMap[Int,Float]()
      
      //-- Get Typical Protein Match Accession 
      var pmAccession : String = null
      if(protSet.typicalProteinMatch.isDefined){
    	  pmAccession = protSet.typicalProteinMatch.get.accession 
      } else {    	  
    	  val jdbcWork = new JDBCWork() {
        	  override def execute(con: Connection) {
        		  val getPMQuery = "SELECT accession from protein_match WHERE id = ?"
        		  val pStmt = con.prepareStatement(getPMQuery)
        		  pStmt.setInt(1, protSet.getTypicalProteinMatchId)
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
      protSet.peptideSet.getPeptideInstances.foreach(pepI =>{
    	  val proSetIds = protSetIdByPepId.getOrElseUpdate(pepI.peptideId, new ArrayBuffer[Int])
    	  proSetIds += protSet.id
    	  if (pepI.validatedProteinSetsCount == 1) {
    	    nbrPepSpecif +=1 
    	  }
    	  weightByPepId += pepI.peptideId -> 0.0f
      })

      proteinPepsWeightStructsByProtSetId += protSet.id -> new ProteinPepsWeightStruct(proteinSet = protSet,typicalPMAcc=pmAccession,nbrPepSpecific=nbrPepSpecif, weightByPeptideId = weightByPepId )
      
    }) // End ProteinPepsWeightStruct initialization 
    
    val resultStruct : Map[Int,ProteinPepsWeightStruct]= proteinPepsWeightStructsByProtSetId.result
    //**** Compute Peptides Weight
   computePeptideWeight(resultStruct,protSetIdByPepId)
        
    resultStruct 
  }


  /**
   * Compute Peptide's Weight for each identified ProteinSet
   * 
   *  If peptide is a specific  ProteinSet, the corresponding weight will be 1
   *  Else if peptide is shared between multiple ProteinSets the weight = # specific pep of ProtSet / Sum ( #specific pep of all ProtSet identified by this pep)
   *  
   *      
   */
  def computePeptideWeight(proteinWeightStructByProtSetId : Map[Int,ProteinPepsWeightStruct], protSetIdByPep:Map[Int,ArrayBuffer[Int]]) : Unit = {
    
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
				  currentProteinWeightStruct.weightByPeptideId(pepId) = currentProteinWeightStruct.nbrPepSpecific / sumNbrSpecificPeptides
			  }
		  }) //End go through ProteinSet Peptides
	  })// End go through  ProteinSet (ProteinPepsWeightStruct)      
  }
  
}

case class ProteinPepsWeightStruct(
			val proteinSet: ProteinSet, 
			val typicalPMAcc: String, 
			val nbrPepSpecific: Int, 
			val weightByPeptideId: scala.collection.mutable.Map[Int, Float] = null)

