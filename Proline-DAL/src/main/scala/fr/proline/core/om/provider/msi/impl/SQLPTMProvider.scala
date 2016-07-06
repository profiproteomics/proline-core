package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap

import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.provider.msi.IPTMProvider
import fr.proline.repository.ProlineDatabaseType

class SQLPTMProvider(val psDbCtx: DatabaseConnectionContext) extends IPTMProvider {
  
  require( psDbCtx.getProlineDatabaseType == ProlineDatabaseType.PS, "PsDb connection required")
  
  /** Returns a map */
  lazy val ptmDefinitionById: LongMap[PtmDefinition] = {
    
    DoJDBCReturningWork.withEzDBC(psDbCtx) { psEzDBC =>

      val ptmRecordById = new LongMap[AnyMapLike]()
      
      // Load PTM records
      psEzDBC.selectAndProcess("SELECT * FROM ptm") { row =>
  
        // Build the PTM record
        val ptmRecord = row.toAnyMap()
        
        ptmRecordById.put(ptmRecord.getLong("id"), ptmRecord )
      }
  
      // Execute SQL query to load PTM evidence records
      val ptmEvidRecords = psEzDBC.selectAllRecords("SELECT * FROM ptm_evidence") /*{ row =>
  
        // Build the PTM record
        val ptmEvidRecord = row.toAnyMap()
        
        // Fix is_required boolean field
        if (ptmEvidRecord("is_required") == "true") { ptmEvidRecord("is_required") = true }
        else { ptmEvidRecord("is_required") = false }
  
        ptmEvidRecord
      }*/
  
      // Group PTM evidences by PTM id
      val ptmEvidRecordsByPtmId = ptmEvidRecords.groupBy(r => r.getLong("ptm_id") )
      
      val ptmDefById = new scala.collection.mutable.LongMap[PtmDefinition]()
  
      // Load PTM specificity records
      psEzDBC.selectAndProcess("SELECT * FROM ptm_specificity") { row =>
        
        // Build the PTM specificity record
        val ptmSpecifRecord = row
  
        // Retrieve corresponding PTM
        val ptmId = ptmSpecifRecord.getLong("ptm_id")
        val ptmRecord = ptmRecordById(ptmId)
        
        // Retrieve corresponding PTM evidences
        val ptmEvidRecords = ptmEvidRecordsByPtmId(ptmId).filter(ev => {
          !ev.isDefined("specificity_id") || ev.getLong("specificity_id") == ptmSpecifRecord.getLongOrElse("id", 0)
        })
        
        // TODO: load classification
        val ptmDef = PtmDefinitionBuilder.buildPtmDefinition(
          ptmRecord = ptmRecord,
          ptmSpecifRecord = ptmSpecifRecord,
          ptmEvidenceRecords = ptmEvidRecords,
          ptmClassification = ""
        )
  
        ptmDefById.put(ptmDef.id, ptmDef)
  
      }
  
      ptmDefById
    }
    
  }

  lazy val ptmDefByNameAndLocation: Map[Tuple3[String, Char, PtmLocation.Location], PtmDefinition] = {
    this.ptmDefinitionById.values.map { p => (p.names.shortName, p.residue, PtmLocation.withName(p.location)) -> p } toMap
  }

  lazy val ptmIdByName: Map[String, Long] = {
    this.ptmDefinitionById.values.map { p => p.names.shortName -> p.id } toMap
  }

  def getPtmDefinitionsAsOptions(ptmDefIds: Seq[Long]): Array[Option[PtmDefinition]] = {
    if (ptmDefIds.isEmpty) return Array()
    
    val ptmDefById = this.ptmDefinitionById
    ptmDefIds.map { ptmDefById.get(_) } toArray
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
