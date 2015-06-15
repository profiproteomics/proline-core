package fr.proline.core.om.model.msi
 
import java.util.Date
import scala.beans.BeanProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import fr.profi.util.misc.InMemoryIdGen
  
object MSISearch extends InMemoryIdGen

case class MSISearch (
          
  //Required fields
  var id: Long,
  val resultFileName: String,
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
        
case class MSISearchProperties()


object SearchSettings extends InMemoryIdGen

case class SearchSettings(
    
  // Required fields
  var id: Long,
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
  var properties: Option[SearchSettingsProperties] = None
  
)

case class SearchSettingsProperties()

case class MSMSSearchSettings(
  // MS/MS search settings
  val ms2ChargeStates: String,
  val ms2ErrorTol: Double,
  val ms2ErrorTolUnit: String
)

case class PMFSearchSettings(  
  // PMF search settings
  val maxProteinMass: Option[Double] = None,
  val minProteinMass: Option[Double] = None,
  val proteinPI: Option[Float] = None
)

object Enzyme extends InMemoryIdGen

case class Enzyme(
    
  // Required fields
  var id: Long,
  val name: String,
  val enzymeCleavages: Array[EnzymeCleavage] = Array(),
  val cleavageRegexp: Option[String] = None,
  val isIndependant: Boolean = false,
  val isSemiSpecific: Boolean = false,
  val properties: Option[EnzymeProperties] = None
  
) {
  
  def this( name: String ) = {
    this( Enzyme.generateNewId, name)
  }

}

case class EnzymeProperties(
  @BeanProperty var ctermGain: Option[String] = None,
  @BeanProperty var ntermGain: Option[String] = None,
  @BeanProperty var minDistance: Option[Int] = None,
  @BeanProperty var maxMissedCleavages: Option[Int] = None
)

object EnzymeCleavage extends InMemoryIdGen

case class EnzymeCleavage(
    
  // Required fields
  var id: Long,
  val site: String,
  val residues: String,
  val restrictiveResidues: Option[String] = None

) {
  override def toString: String = {
    if(restrictiveResidues.isDefined && !restrictiveResidues.get.isEmpty())
      site + ":" + residues + "/" + restrictiveResidues.get
    else
      site + ":" + residues
  }
}

object SeqDatabase extends InMemoryIdGen

case class SeqDatabase(
    
  // Required fields
  var id: Long,
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

case class SeqDatabaseProperties()

case class SeqDatabaseSearchProperties()

