package fr.proline.core.om.builder

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.LongMap
import fr.profi.util.primitives._
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.model.msi._

object PtmDefinitionBuilder {
  
  protected val PepPtmCols = MsiDbPeptidePtmColumns
  protected val PtmCols = MsiDbPtmColumns
  protected val PtmEvCols = MsiDbPtmEvidenceColumns
  protected val PtmSpecifCols = MsiDbPtmSpecificityColumns

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
      unimodId = ptmRecord.getIntOrElse(PtmCols.UNIMOD_ID, 0),
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
  
  def buildLocatedPtmsGroupedByPepId(
    pepPtmRecordsByPepId: LongMap[_ <: Seq[IValueContainer]],
    ptmDefinitionById: LongMap[PtmDefinition]
  ): LongMap[Array[LocatedPtm]] = {

    val locatedPtmLongMap = new LongMap[Array[LocatedPtm]]()

    for ( (pepId, pepPtmRecords) <- pepPtmRecordsByPepId ) {

      val locatedPtms = new ArrayBuffer[LocatedPtm]
      for (pepPtmRecord <- pepPtmRecords) {

        // Retrieve PTM definition
        val ptmSpecifId = pepPtmRecord.getLong(PepPtmCols.PTM_SPECIFICITY_ID)

        val ptmDef = ptmDefinitionById(ptmSpecifId)

        // Build located PTM
        val locatedPtm = LocatedPtm(ptmDef, pepPtmRecord.getInt(PepPtmCols.SEQ_POSITION))
        locatedPtms += locatedPtm
      }

      locatedPtmLongMap.put(pepId, locatedPtms.toArray)
    }

    locatedPtmLongMap
  }
  
}