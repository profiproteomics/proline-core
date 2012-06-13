package fr.proline.core.om.model.msi
 
import java.util.Date
import com.codahale.jerkson.JsonSnakeCase
import fr.proline.core.utils.misc.InMemoryIdGen
  
object SeqDatabase extends InMemoryIdGen

@JsonSnakeCase
case class SeqDatabase(
    
        // Required fields
        var id: Int,
        val name: String,
        val filePath: String,
        val sequencesCount: Int,
         
        // Immutable optional fields
        val version: String = "",
        val releaseDate: String = ""
         
        ) {
      
  }
 
object SearchSettings extends InMemoryIdGen

@JsonSnakeCase
case class SearchSettings(
    
      // Required fields
      var id: Int,
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
      val instrumentConfig: InstrumentConfig,
      
      // Mutable optional fields
      var quantitation: String = ""
       ) {
    
}

object MSISearch extends InMemoryIdGen

@JsonSnakeCase
case class MSISearch (
        
  		  //Required fields
        var id: Int,
  		  val resultFileName: String,
  		  val submittedQueriesCount: Int,
  		  val searchSettings: SearchSettings,
  		  val peakList: Peaklist,
  		  val date: Date,
  		  
  		  // Immutable optional fields
  		  val title: String = "",
  		  val resultFileDirectory: String = "",
  		  val jobNumber: Int = 0,
  		  val userName: String = "",
  		  val userEmail: String = "",
  		  
  		  // Mutable optional fields  		  
  		  var queriesCount: Int = 0,
  		  var searchedSequencesCount: Int = 0
        ) {
  
}

