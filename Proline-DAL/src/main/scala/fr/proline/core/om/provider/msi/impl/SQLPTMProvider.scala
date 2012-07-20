package fr.proline.core.om.provider.msi.impl

import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.{MsiDb,PsDb}
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.PtmLocation
import fr.proline.core.om.model.msi.PtmSpecificity
import fr.proline.core.om.provider.msi.IPTMProvider


class SQLPTMProvider( val psDb: PsDb ) extends IPTMProvider {
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
 
  /*
  /** Returns a map */
  lazy val ptmSpecificityMap: Map[Int,PtmSpecificity] = {
      
    // Load PTM specificities
    val ptmSpecifs = psDb.getOrCreateTransaction.select( "SELECT * FROM ptm_specificity" ) { r => 
      val rs = r.rs
      val resStr = rs.getString("residue");
      val resChar = if( resStr != null ) resStr.charAt(0) else '\0'
        
      new PtmSpecificity( id = rs.getInt("id"),
                          location = rs.getString("location"), 
                          residue = resChar,
                          ptmId = rs.getInt("ptm_id")
                          )
      
      // TODO: load classification field
    }
    
    // Map ptmSpecifs by their id
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,PtmSpecificity]
    for( ptmSpecif <- ptmSpecifs ) { mapBuilder += ( ptmSpecif.id -> ptmSpecif ) }
    mapBuilder.result()
    
  }*/
  
  /** Returns a map */
  lazy val ptmDefinitionById: Map[Int,PtmDefinition] = {
    
    var ptmColNames: Seq[String] = null
    val ptmMapBuilder = scala.collection.immutable.Map.newBuilder[Int,Map[String,Any]]
    val psDbTx = psDb.getOrCreateTransaction
    
    // Load PTM records
    psDbTx.selectAndProcess( "SELECT * FROM ptm" ) { r => 
        
      if( ptmColNames == null ) { ptmColNames = r.columnNames }
      
      // Build the PTM record
      val ptmRecord = ptmColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      ptmMapBuilder += ( ptmRecord("id").asInstanceOf[Int] -> ptmRecord )
      
    }
    
    val ptmRecordById = ptmMapBuilder.result()
    
    // Load PTM evidence records   
    var ptmEvidColNames: Seq[String] = null
    
    // Execute SQL query to load PTM evidence records
    val ptmEvidRecords = psDbTx.select( "SELECT * FROM ptm_evidence" ) { r => 
        
      if( ptmEvidColNames == null ) { ptmEvidColNames = r.columnNames }
      
      // Build the PTM record
      var ptmEvidRecord = new collection.mutable.HashMap[String, Any]
      ptmEvidColNames foreach { colName => ptmEvidRecord.put( colName, r.nextObject.get ) }
     // var ptmEvidRecord = ptmEvidColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      
      // Fix is_required boolean field
      if( ptmEvidRecord("is_required") == "true" ) { ptmEvidRecord("is_required") = true }
      else { ptmEvidRecord("is_required") = false }
      
      ptmEvidRecord.toMap
      
    }
    
    // Group PTM evidences by PTM id
    val ptmEvidRecordsByPtmId = ptmEvidRecords.groupBy( _.get("ptm_id").get.asInstanceOf[Int] )
    
    var ptmSpecifColNames: Seq[String] = null
    val ptmDefMapBuilder = scala.collection.immutable.Map.newBuilder[Int,PtmDefinition]
    
    // Load PTM specificity records
    psDbTx.selectAndProcess( "SELECT * FROM ptm_specificity" ) { r => 
        
      if( ptmSpecifColNames == null ) { ptmSpecifColNames = r.columnNames }
      
      // Build the PTM specificity record
      val ptmSpecifRecord = ptmSpecifColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
      
      // Retrieve corresponding PTM
      val ptmId = ptmSpecifRecord("ptm_id").asInstanceOf[Int]
      val ptmRecord = ptmRecordById(ptmId)
      
      // Retrieve corresponding PTM evidences
      val ptmEvidRecords = ptmEvidRecordsByPtmId.get(ptmId).get
      
      // TODO : load classification
      val ptmDef = PtmDefinitionBuilder.buildPtmDefinition( ptmRecord = ptmRecord ,
                                                            ptmSpecifRecord = ptmSpecifRecord,
                                                            ptmEvidenceRecords = ptmEvidRecords,
                                                            ptmClassification = "" )
      
      ptmDefMapBuilder += ( ptmDef.id -> ptmDef )
      
    }
    
    ptmDefMapBuilder.result()
    
    
  }
  
  lazy val ptmDefByNameAndLocation: Map[Tuple3[String,Char,PtmLocation.Location],PtmDefinition] = {
    this.ptmDefinitionById.values.map { p => ( p.names.shortName, p.residue, PtmLocation.withName(p.location) ) -> p } toMap
  }
  
  lazy val ptmIdByName: Map[String,Int] = {
    this.ptmDefinitionById.values.map { p => p.names.shortName -> p.id } toMap
  }
  
  /*private var ptmDefinitionMap: HashMap[Int, PtmDefinition] = new collection.mutable.HashMap[Int, PtmDefinition]
  
  /** Extends the map of PTM definitions using a provided list of PTM specificity ids */
  private def extendPtmDefMap( ptmSpecifIds: Seq[Int] ): Unit = {
    if( ptmSpecifIds.length == 0 ) { return () }
    
    val missingPtmSpecifIds = ptmSpecifIds.filter( !ptmDefinitionMap.contains(_) )
    if( missingPtmSpecifIds.length == 0 ) { return () }
    
    // Load PTM records corresponding to the missing PTM specificities
    val ptmSpecifs = psDb.transaction { tx =>       
      tx.select( "SELECT * FROM ptm_specificity, ptm WHERE "+
                 "ptm_specificity.ptm_id = ptm.id AND ptm_specificity.id IN (" +
                 missingPtmSpecifIds.mkString(",") + ")" ) { r => 
        
        val ptmSpecifRecord = 
        
        // TODO: load classification field
      }
    }
    
  }*/
  
  def getPtmDefinitionsAsOptions( ptmDefIds: Seq[Int] ): Array[Option[PtmDefinition]] = {
    val ptmDefById = this.ptmDefinitionById
    ptmDefIds.map { ptmDefById.get(_) } toArray
  }
  
  def getPtmDefinitions( ptmDefIds: Seq[Int] ): Array[PtmDefinition] = {
    this.getPtmDefinitionsAsOptions( ptmDefIds ).filter( _ != None ).map( _.get )
  }
    
  def getPtmDefinition( ptmShortName: String, ptmResidue: Char, ptmLocation: PtmLocation.Location ): Option[PtmDefinition] = {
    this.ptmDefByNameAndLocation.get( ptmShortName, ptmResidue, ptmLocation )
  }
  
  def getPtmId( shortName: String ): Option[Int] = {
    this.ptmIdByName.get( shortName )
  }
  
}
