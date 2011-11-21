package fr.proline.core.om.msi

package PtmClasses {

  import org.apache.commons.lang3.StringUtils
  
  class PtmNames( val shortName: String, val fullName: String ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( shortName ) )
    
  }
  
  class UnimodEntry( // Required fields
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
  
  class PtmEvidence( // Required fields
                     val name: String, 
                     val monoMass: Double,
                     val averageMass: Double,
                     
                     // Immutable optional fields
                     val isRequired: Boolean = false
                     ) {
    
    // Requirements
    require( monoMass > 0 && averageMass > 0  )  
    
  }
  
  class PtmSpecificity( // Required fields
                        val location: String, 
      
                        // Immutable optional fields
                        val residue: Char = 0, 
                        val classification: String = null,
                        val id: Int = 0,
                        val ptmId : Int = 0 ) {
    
    // Requirements
    require( StringUtils.isNotEmpty( location ) )  
    
  }
  
  class PtmDefinition( // Required fields
                       override val id: Int,
                       override val location: String,
                       val names: PtmNames,
                       val ptmEvidences: Array[PtmEvidence],
                       
                       // Immutable optional fields
                       override val residue: Char = 0,
                       override val classification: String = null,
                       override val ptmId: Int = 0
                       )
    extends PtmSpecificity( location, residue, classification, id, ptmId ) {
    
    // Requirements
    require( id > 0 && names != null && ptmEvidences != null )
    
    // Lazy values
    lazy val precursorDelta : PtmEvidence = {    
      ptmEvidences.find( { _.name == "Precursor" } ).get;
    }
    
  }
  
  class LocatedPtm( // Required fields
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
    
  }

}