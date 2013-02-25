package fr.proline.core.om.model.msi
 
import java.util.Date
import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

import fr.proline.util.misc.InMemoryIdGen
  
object MSISearch extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MSISearch (
          
  //Required fields
  var id: Int,
  val resultFileName: String,
  val submittedQueriesCount: Int,
  var searchSettings: SearchSettings,
  var peakList: Peaklist,
  val date: Date,
  
  // Immutable optional fields
  val title: String = "",
  val resultFileDirectory: String = "",
  val jobNumber: Int = 0,
  val userName: String = "",
  val userEmail: String = "",
  
  // Mutable optional fields
  var queriesCount: Int = 0,
  var searchedSequencesCount: Int = 0,
  var properties: Option[MSISearchProperties] = None
)
        
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MSISearchProperties


object SearchSettings extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
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
  
  // Mutable required fields
  var usedEnzymes: Array[Enzyme],
  var variablePtmDefs: Array[PtmDefinition],
  var fixedPtmDefs: Array[PtmDefinition],
  var seqDatabases: Array[SeqDatabase],
  var instrumentConfig: InstrumentConfig,
  
  // Mutable optional fields
  var msmsSearchSettings: Option[MSMSSearchSettings] = None,
  var pmfSearchSettings: Option[PMFSearchSettings] = None,
  var quantitation: String = "",
  var properties: Option[SearchSettingsProperties] = None
  
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SearchSettingsProperties {
  @BeanProperty var targetDecoyMode: Option[String] = None // CONCATENATED | SEPARATED
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MSMSSearchSettings(
  // MS/MS search settings
  val ms2ChargeStates: String,
  val ms2ErrorTol: Double,
  val ms2ErrorTolUnit: String
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PMFSearchSettings(  
  // PMF search settings
  val maxProteinMass: Option[Double] = None,
  val minProteinMass: Option[Double] = None,
  val proteinPI: Option[Float] = None
)

object Enzyme extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class Enzyme(
    
  // Required fields
  var id: Int,
  val name: String,
  val cleavageRegexp: Option[String] = None,
  val isIndependant: Boolean = false,
  val isSemiSpecific: Boolean = false
   
) {
  
  def this( name: String ) = {
    this( Enzyme.generateNewId, name)
  }
}

object SeqDatabase extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SeqDatabase(
    
  // Required fields
  var id: Int,
  val name: String,
  val filePath: String,
  val sequencesCount: Int,
  val releaseDate: Date,
   
  // Immutable optional fields
  val version: String = "",
  
  // Mutable optional fields
  var searchedSequencesCount: Int = 0,
  
  var properties: Option[SeqDatabaseProperties] = None,
  var searchProperties: Option[SeqDatabaseSearchProperties] = None
   
)

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SeqDatabaseProperties

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SeqDatabaseSearchProperties

