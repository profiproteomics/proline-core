package fr.proline.core.om.model.msi

import org.apache.commons.lang3.StringUtils
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.util.misc.InMemoryIdGen

object PtmNames extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PtmNames( val shortName: String, val fullName: String ) {
  
  // Requirements
  require( StringUtils.isNotEmpty( shortName ) )
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class UnimodEntry( // Required fields
                   override val shortName: String, 
                   override val fullName: String, 
                   val specificities: Array[Any],
                   
                   // Immutable optional fields
                   val unimodId: Int = 0, 
                   val ptmEvidences: Array[PtmEvidence] = null
                   )
  extends PtmNames( shortName, fullName ) {
  
  // Requirements
  require( specificities != null  )
  
}

object IonTypes extends Enumeration {
  type IonType = Value
  val Precursor = Value("Precursor")
  val Artefact = Value("Artefact")
  val NeutralLoss = Value("NeutralLoss")
  val PepNeutralLoss = Value("PepNeutralLoss")
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PtmEvidence( // Required fields
                   val ionType: IonTypes.IonType,
                   val composition: String,
                   val monoMass: Double,
                   val averageMass: Double,
                   
                   // Immutable optional fields
                   val isRequired: Boolean = false
                   ) {
  // Requirements
  require( ionType != null &&  composition != null )
  
  def ionType_ ( newIonType: IonTypes.IonType ) = { newIonType }
    
}

 
object PtmLocation extends Enumeration {
	  type Location = Value
	  val PROT_N_TERM = Value("Protein N-term")
	  val PROT_C_TERM  = Value("Protein C-term")
	  val N_TERM   = Value("N-term")
	  val C_TERM   = Value("C-term")
	  val ANY_N_TERM   = Value("Any N-term")
	  val ANY_C_TERM   = Value("Any C-term")
	  val ANYWHERE= Value("Anywhere")       
} 

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PtmSpecificity( // Required fields
                      val location: String, 
    
                      // Immutable optional fields
                      val residue: Char = '\0',
                      val classification: String = null,
                      val id: Int = 0,
                      val ptmId : Int = 0 ) {
  
  // Requirements
  require( StringUtils.isNotEmpty( location ) )  
  
}

object PtmDefinition extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class PtmDefinition( // Required fields
                     override val id: Int,
                     override val location: String,
                     val names: PtmNames,
                     val ptmEvidences: Array[PtmEvidence],
                     
                     // Immutable optional fields
                     override val residue: Char = '\0',
                     override val classification: String = null,
                     override val ptmId: Int = 0
                     )
  extends PtmSpecificity( location, residue, classification, id, ptmId ) {
  
  // Requirements
  require(  names != null && ptmEvidences != null )
  
  // Lazy values
  lazy val precursorDelta : PtmEvidence = {    
    ptmEvidences.find( { _.ionType == IonTypes.Precursor } ).get;
  }
  
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class LocatedPtm( // Required fields
                  val definition: PtmDefinition, 
                  val seqPosition: Int, 
                  val monoMass: Double,
                  val averageMass: Double,
                  val composition : String,
                  
                  // Immutable optional fields
                  val isNTerm : Boolean = false, 
                  val isCTerm : Boolean = false
                  ) {
  
  // Requirements
  require( definition != null && seqPosition >= -1 && monoMass > 0 && averageMass > 0 && StringUtils.isNotEmpty( composition ) )
  if( isNTerm ) require( seqPosition == 0 )
  if( isCTerm ) require( seqPosition == -1 )

}

