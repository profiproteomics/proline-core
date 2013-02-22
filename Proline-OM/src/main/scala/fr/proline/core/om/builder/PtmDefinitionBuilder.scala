package fr.proline.core.om.builder
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmEvidence
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.PtmNames
import fr.proline.core.om.model.msi.IonTypes

object PtmDefinitionBuilder {  

  /**
   * 
   * Create a PtmDefinition using corresponding information
   *  - ptmRecord : contains value for ptm properties "id"(Int) for ptm_id, "short_name" (String), "full_name" (String) for Ptm Names  
   *      !! FIXME : To Confirm If id == 0, ptm is to be created FIXME!!
   *  - ptmSpecifRecord : contains value for ptmDefinition properties "residue" (String), "id" (Int), "location" (String): if id is not specified, a new id will be generated
   *  - ptmEvidenceRecords :List of map:  contains value for properties "type"(String), "composition"(String), "mono_mass"(Double), "average_mass"(Double),"is_required" (Boolean) for each ptmEvidence 
   *  - ptmClassification : name of classification,
   *    
   */
  def buildPtmDefinition( ptmRecord: Map[String,Any],
                          ptmSpecifRecord: Map[String,Any],                          
                          ptmEvidenceRecords: Seq[Map[String,Any]],
                          ptmClassification: String
                         ) : PtmDefinition = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    val ptmEvArray = new Array[PtmEvidence](ptmEvidenceRecords.length)

    for (i <- 0 until ptmEvidenceRecords.length ) {
      val ptmEvidenceRecord = ptmEvidenceRecords(i);
            
      val ptmEv = new PtmEvidence( ionType = IonTypes.withName( ptmEvidenceRecord("type").asInstanceOf[String] ),
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
      ptmDefId = ptmSpecifRecord("id").asInstanceOf[AnyVal]
    else
      ptmDefId =PtmDefinition.generateNewId
      
    return new PtmDefinition(
                          id = ptmDefId,
                          ptmId = ptmRecord("id").asInstanceOf[AnyVal],
                          location = ptmSpecifRecord("location").asInstanceOf[String],
                          residue = resChar,
                          classification = ptmClassification,
                          names = new PtmNames( shortName = ptmRecord("short_name").asInstanceOf[String],
                                                fullName = ptmRecord("full_name").asInstanceOf[String] ),
                          ptmEvidences = ptmEvArray
                          )
  }

  /**
   * Create a LocatedPtm using specified PtmDefinition and location on peptide sequence.
   * seqPos == 0 for Nterm and  seqPos == -1 for CTerm
   * 
   */
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