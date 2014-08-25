package fr.proline.core.om.builder

import scala.collection.mutable.ArrayBuffer
import fr.profi.util.primitives._
import fr.proline.core.dal.tables.ps._
import fr.proline.core.om.model.msi._

object PtmDefinitionBuilder {
  
  protected val PepPtmCols = PsDbPeptidePtmColumns
  protected val PtmCols = PsDbPtmColumns
  protected val PtmEvCols = PsDbPtmEvidenceColumns
  protected val PtmSpecifCols = PsDbPtmSpecificityColumns

  /**
   * 
   * Create a PtmDefinition using corresponding information
   *  - ptmRecord : contains value for ptm properties "id"(Int) for ptm_id, "short_name" (String), "full_name" (String) for Ptm Names  
   *  - ptmSpecifRecord : contains value for ptmDefinition properties "residue" (String), "id" (Int), "location" (String): if id is not specified, a new id will be generated
   *  - ptmEvidenceRecords :List of map:  contains value for properties "type"(String), "composition"(String), "mono_mass"(Double), "average_mass"(Double),"is_required" (Boolean) for each ptmEvidence 
   *  - ptmClassification : name of classification
   *    
   */
  def buildPtmDefinition(
    ptmRecord: IValueContainer,
    ptmSpecifRecord: IValueContainer,
    ptmEvidenceRecords: Seq[IValueContainer],
    ptmClassification: String
  ): PtmDefinition = {
    
    val ptmEvArray = new Array[PtmEvidence](ptmEvidenceRecords.length)

    for (i <- 0 until ptmEvidenceRecords.length ) {
      val ptmEvidenceRecord = ptmEvidenceRecords(i)
            
      val ptmEv = new PtmEvidence(
        ionType = IonTypes.withName( ptmEvidenceRecord.getString(PtmEvCols.TYPE) ),
        composition = ptmEvidenceRecord.getString(PtmEvCols.COMPOSITION),
        monoMass = ptmEvidenceRecord.getDouble(PtmEvCols.MONO_MASS),
        averageMass = ptmEvidenceRecord.getDouble(PtmEvCols.AVERAGE_MASS),
        isRequired = ptmEvidenceRecord.getBooleanOrElse(PtmEvCols.IS_REQUIRED,false)
      )

      ptmEvArray(i) = ptmEv
    }
    
    val residueStrOpt = ptmSpecifRecord.getStringOption(PtmSpecifCols.RESIDUE)
    val resChar = if( residueStrOpt.isDefined && residueStrOpt.get != null ) residueStrOpt.get.charAt(0) else '\0'
    
    val ptmDefId = ptmSpecifRecord.getLongOrElse(PtmSpecifCols.ID,PtmDefinition.generateNewId)
    require(ptmDefId != 0, "ptmDefId must be different than zero")
      
    return new PtmDefinition(
      id = ptmDefId,
      ptmId = ptmRecord.getLong(PtmCols.ID),
      location = ptmSpecifRecord.getString(PtmSpecifCols.LOCATION),
      residue = resChar,
      classification = ptmClassification,
      names = new PtmNames(
        shortName = ptmRecord.getString(PtmCols.SHORT_NAME),
        fullName = ptmRecord.getString(PtmCols.FULL_NAME)
      ),
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
    
    new LocatedPtm(
      definition = ptmDef, 
      seqPosition = seqPos,
      monoMass = precDelta.monoMass,
      averageMass = precDelta.averageMass,
      composition = precDelta.composition,
      isNTerm = isNTerm,
      isCTerm = isCTerm
    )

  }
  
  def buildLocatedPtmsGroupedByPepId(
    pepPtmRecordsByPepId: Map[Long,Seq[IValueContainer]],
    ptmDefinitionById: Map[Long,PtmDefinition]
  ): Map[Long, Array[LocatedPtm]] = {

    val locatedPtmMapBuilder = scala.collection.immutable.Map.newBuilder[Long, Array[LocatedPtm]]

    for ( (pepId, pepPtmRecords) <- pepPtmRecordsByPepId ) {

      var locatedPtms = new ArrayBuffer[LocatedPtm]
      for (pepPtmRecord <- pepPtmRecords) {

        // Retrieve PTM definition
        val ptmSpecifId = pepPtmRecord.getLong(PepPtmCols.PTM_SPECIFICITY_ID)

        // FIXME: remove this check when peptide_ptm insertion is fixed
        if (ptmSpecifId > 0) {
          val ptmDef = ptmDefinitionById(ptmSpecifId)

          // Build located PTM
          val locatedPtm = PtmDefinitionBuilder.buildLocatedPtm(ptmDef, pepPtmRecord.getInt(PepPtmCols.SEQ_POSITION))
          locatedPtms += locatedPtm
        }

      }

      locatedPtmMapBuilder += (pepId -> locatedPtms.toArray)
    }

    locatedPtmMapBuilder.result()
  }
  
}