package fr.proline.core.om.builder

import fr.proline.core.om.msi.PtmClasses.PtmDefinition
import fr.proline.core.om.msi.PtmClasses.PtmEvidence
import fr.proline.core.om.msi.PtmClasses.PtmNames
import fr.proline.core.om.msi.PtmClasses.LocatedPtm

object PtmDefinitionBuilder {  

  /**
   * 
   * Create a PtmDefinition using corresponding information
   *  - ptmRecord : contains value for ptm properties "id" for ptm_id, "short_name", "full_name" for Ptm Names : If id == 0, ptm is to be created TODO !!
   *  - ptmSpecifRecord : contains value for ptmDefinition properties "residue", "id", "location" : if id is not specified, a new id will be generated
   *  - ptmEvidenceRecords :List of map:  contains value for properties "type", "composition", "mono_mass", "average_mass","is_required" for each ptmEvidence 
   *  - ptmClassification : name of classification,
   *    
   */
  def buildPtmDefinition( ptmRecord: Map[String,Any],
                          ptmSpecifRecord: Map[String,Any],                          
                          ptmEvidenceRecords: Seq[Map[String,Any]],
                          ptmClassification: String
                         ) : PtmDefinition = {
    
    
    val ptmEvArray = new Array[PtmEvidence](ptmEvidenceRecords.length)

    for (i <- 0 until ptmEvidenceRecords.length ) {
      val ptmEvidenceRecord = ptmEvidenceRecords(i);
            
      val ptmEv = new PtmEvidence( ionType = ptmEvidenceRecord("type").asInstanceOf[String],
                                   composition = ptmEvidenceRecord("composition").asInstanceOf[String],
                                   monoMass = ptmEvidenceRecord("mono_mass").asInstanceOf[Double],
                                   averageMass = ptmEvidenceRecord("average_mass").asInstanceOf[Double],
                                   isRequired = ptmEvidenceRecord.getOrElse("is_required",false).asInstanceOf[Boolean]
                                 )

      ptmEvArray(i) = ptmEv
    }
    
    val residueStr = ptmSpecifRecord("residue").asInstanceOf[String];
    val resChar = if( residueStr != null ) residueStr.charAt(0) else '\0'
      
    var ptmDefId: Int =0 
    if(ptmSpecifRecord.contains("id"))
      ptmDefId = ptmSpecifRecord("id").asInstanceOf[Int]
    else
      ptmDefId =PtmDefinition.generateNewId
      
    return new PtmDefinition(
                          id = ptmDefId,
                          ptmId = ptmRecord("id").asInstanceOf[Int],
                          location = ptmSpecifRecord("location").asInstanceOf[String],
                          residue = resChar,
                          classification = ptmClassification,
                          names = new PtmNames( shortName = ptmRecord("short_name").asInstanceOf[String],
                                                fullName = ptmRecord("full_name").asInstanceOf[String] ),
                          ptmEvidences = ptmEvArray
                          )
  }

  def buildLocatedPtm( ptmDef: PtmDefinition, seqPos: Int ): LocatedPtm = {
    
    var( isNTerm, isCTerm ) = ( false, false )
    
    // N-term locations are: Any N-term or Protein N-term
    if( ptmDef.location matches ".+N-term$" ) {
      if( seqPos != 0 ) {
        throw new IllegalArgumentException( "sequence position must be '0' because it's a N-Term PTM" )
      }
      isNTerm = true  
    }
    // C-term locations are: Any C-term, Protein C-term
    else if( ptmDef.location matches ".+C-term$" ) {
      //my $nb_residues = length($pep_sequence);
      //die "sequence postion must be '$nb_residues' because it's a C-Term PTM" if $seq_pos != $nb_residues;
      if( seqPos != -1 ) {
        throw new IllegalArgumentException( "sequence position must be '-1' because it's a C-Term PTM" )
      }
      isCTerm = true
    }
    
    val precDelta = ptmDef.precursorDelta
    
    new LocatedPtm(  definition = ptmDef, 
                     seqPosition = seqPos,
                     monoMass = precDelta.monoMass,
                     averageMass = precDelta.averageMass,
                     composition = precDelta.composition,
                     isNTerm = isNTerm,
                     isCTerm = isCTerm )

  }
}