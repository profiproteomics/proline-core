package fr.proline.core.om.provider.msi.impl

import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.provider.msi.IPTMProvider

import scala.collection.mutable.LongMap

class SQLPTMProvider(val msiDbCtx: MsiDbConnectionContext) extends IPTMProvider {
  
  /** Returns a map */
  lazy val ptmDefinitionById: LongMap[PtmDefinition] = {
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { msEzDBC =>

      val ptmRecordById = new LongMap[AnyMapLike]()
      
      // Load PTM records
      msEzDBC.selectAndProcess("SELECT * FROM ptm") { row =>
  
        // Build the PTM record
        val ptmRecord = row.toAnyMap()
        
        ptmRecordById.put(ptmRecord.getLong("id"), ptmRecord )
      }
  
      // Execute SQL query to load PTM evidence records
      val ptmEvidRecords = msEzDBC.selectAllRecords("SELECT * FROM ptm_evidence") /*{ row =>
  
        // Build the PTM record
        val ptmEvidRecord = row.toAnyMap()
        
        // Fix is_required boolean field
        if (ptmEvidRecord("is_required") == "true") { ptmEvidRecord("is_required") = true }
        else { ptmEvidRecord("is_required") = false }
  
        ptmEvidRecord
      }*/
  
      // Group PTM evidences by PTM id
      val ptmEvidRecordsByPtmIdAndSpecifId = ptmEvidRecords.groupBy(r => (r.getLong("ptm_id"), r.getLongOrElse("specificity_id", 0)))
      
      val ptmClassificationRecords = msEzDBC.selectAllRecords("SELECT * FROM ptm_classification")
      val ptmClassificationRecordsById = ptmClassificationRecords.map(r => { r.getLong("id") -> r.getString("name")}).toMap
      
      val ptmDefById = new scala.collection.mutable.LongMap[PtmDefinition]()
  
      // Load PTM specificity records
      msEzDBC.selectAndProcess("SELECT * FROM ptm_specificity") { row =>
        
        // Build the PTM specificity record
        val ptmSpecifRecord = row
  
        // Retrieve corresponding PTM
        val ptmId = ptmSpecifRecord.getLong("ptm_id")
        val ptmRecord = ptmRecordById(ptmId)
        
        // Retrieve corresponding PTM evidences
        val evidencesWithoutSpecificity = ptmEvidRecordsByPtmIdAndSpecifId.get(ptmId, 0)
        val evidencesWithSpecificity = ptmEvidRecordsByPtmIdAndSpecifId.get(ptmId, ptmSpecifRecord.getLong("id"))
        val ptmEvidRecords = evidencesWithoutSpecificity.getOrElse(Array.empty[AnyMap]) ++ evidencesWithSpecificity.getOrElse(Array.empty[AnyMap])
        
        val ptmDef = PtmDefinitionBuilder.buildPtmDefinition(
          ptmRecord = ptmRecord,
          ptmSpecifRecord = ptmSpecifRecord,
          ptmEvidenceRecords = ptmEvidRecords,
          ptmClassification = ptmClassificationRecordsById(ptmSpecifRecord.getLong("classification_id"))
        )
  
        ptmDefById.put(ptmDef.id, ptmDef)
  
      }
  
      ptmDefById
    }
    
  }

  lazy val ptmDefByNameAndLocation: Map[Tuple3[String, Char, PtmLocation.Location], PtmDefinition] = {
    this.ptmDefinitionById.values.map { p => (p.names.shortName, p.residue, PtmLocation.withName(p.location)) -> p }.toMap
  }

  lazy val ptmIdByName: Map[String, Long] = {
    this.ptmDefinitionById.values.map { p => p.names.shortName -> p.id }.toMap
  }

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {
    if (ptmDefIds.isEmpty) return Array()
    
    val ptmDefById = this.ptmDefinitionById
    ptmDefIds.map { ptmDefById.get(_) }.toArray
  }

  def getPtmDefinitions(ptmDefIds: Seq[Long]): Array[PtmDefinition] = {
    if (ptmDefIds.isEmpty) return Array()
    
    this.getPtmDefinitionsAsOptions(ptmDefIds).filter(_.isDefined).map(_.get)
  }

  def getPtmDefinition(ptmShortName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    this.ptmDefByNameAndLocation.get(ptmShortName, ptmResidue, ptmLocation)
  }

  def getPtmDefinition(ptmMonoMass: Double, ptmMonoMassMargin: Double, ptmResidue: Char, ptmLocation: PtmLocation.Location): Option[PtmDefinition] = {
    this.ptmDefinitionById.values.foreach(ptm => {
      ptm.ptmEvidences.foreach(e => {
        if (scala.math.abs(ptmMonoMass - e.monoMass) <= ptmMonoMassMargin
          && ptm.residue == ptmResidue
          && ptm.location == ptmLocation.toString) {
          return Some(ptm)
        }
      })
    })
    None
  }

  def getPtmId(shortName: String): Option[Long] = {
    this.ptmIdByName.get(shortName)
  }

}
