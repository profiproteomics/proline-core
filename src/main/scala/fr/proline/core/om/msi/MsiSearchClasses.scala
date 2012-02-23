package fr.proline.core.om.msi

package MsiSearchClasses {
  
import fr.proline.core.om.msi.PtmClasses.PtmDefinition
import fr.proline.core.om.msi.InstrumentClasses.Instrument
import fr.proline.core.om.msi.MsAnalysisClasses.Peaklist
import java.util.Date
import fr.proline.core.om.helper.MiscUtils.InMemoryIdGen
  
  object SeqDatabase extends InMemoryIdGen {
    
  }
  
  class SeqDatabase(
      
          // Required fields
          val id: Int,
          val name: String,
          val filePath: String,
          val sequencesCount: Int,
           
          // Immutable optional fields
          val version: String = null,
          val releaseDate: String = null
           
          ) {
      
  }
 
  
  class SearchSettings(
      
          // Required fields
          val softwareName: String,
          val softwareVersion: String,
          val taxonomy: String,
          val maxMissedCleavages: Int,
          val ms1ChargeStates: String,
          val ms1ErrorTol: Double,
          val ms1ErrorTolUnit: String,
          val isDecoy: Boolean,
          val usedEnzymes: Array[String], // TODO: create an enzyme class
          val variablePtmDefs: Array[PtmDefinition],
          val fixedPtmDefs: Array[PtmDefinition],
          val seqDatabases: Array[SeqDatabase],
          val instrument: Instrument,
           
          // Mutable optional fields
          var quantitation: String = null
           
) {
      
  }
  
  class MSISearch (
      
    		  //Required fields
    		  val resultFileName: String,
    		  val submittedQueriesCount: Int,
    		  val searchSettings: SearchSettings,
    		  val peakList: Peaklist,
    		  
    		  // Immutable optional fields
    		  val title: String,
    		  val date: Date,
    		  val resultFilePath: String,	  
    		  
    		  // Mutable optional fields		  
    		  var queriesCount: Int
          ){
    
  }
	
}