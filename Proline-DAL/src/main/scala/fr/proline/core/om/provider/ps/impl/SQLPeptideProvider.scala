package fr.proline.core.om.provider.ps.impl

import net.noerd.prequel.SQLFormatterImplicits._

import fr.proline.core.dal.{MsiDb,PsDb}
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.PtmSpecificity
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide

class SQLPeptideProvider( val psDb: PsDb ) {
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
  
  /** Returns a map */
  lazy val ptmSpecificityMap : Map[Int,PtmSpecificity] = {
      
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
    
  }
  
  /** Returns a map */
  lazy val ptmDefinitionMap : Map[Int,PtmDefinition] = {
    
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
  
  /*private var ptmDefinitionMap : HashMap[Int, PtmDefinition] = new collection.mutable.HashMap[Int, PtmDefinition]
  
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
  
  
  /** Returns a map of peptide PTMs grouped by the peptide id */
  def getPeptidePtmRecordsByPepId( peptideIds: Seq[Int] ): Map[Int,Array[Map[String,Any]]] = {
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Map[String,Any]]
    
    // TODO: Check if database driver is SQLite
    //my $max_items_by_iter = $msi_rdb->driver eq 'sqlite' ? 998 : 50000;
    val maxNbIters = psDb.maxVariableNumber
    var colNames: Seq[String] = null
    val pepPtmRecords = new ArrayBuffer[Map[String,Any]] // TODO: replace by an array
    val psDbTx = psDb.getOrCreateTransaction
    
    // Iterate over groups of peptide ids
    peptideIds.grouped(maxNbIters).foreach( tmpPepIds => {      
      
      // Retrieve peptide PTMs for the current group of peptide ids
      psDbTx.selectAndProcess( "SELECT * FROM peptide_ptm WHERE peptide_id IN ("+ tmpPepIds.mkString(",") +")" ) { r =>
          
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the peptide PTM record
        val peptidePtmRecord = colNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        pepPtmRecords += peptidePtmRecord
        
        // Map the record by its id
        //mapBuilder += ( peptidePtmRecord("id").asInstanceOf[Int] -> peptidePtmRecord )
        
        }
      }
      
    )
    
    //mapBuilder.result()
    
    pepPtmRecords.toArray.groupBy( _.get("peptide_id").get.asInstanceOf[Int] )
    
  }
  
  def getLocatedPtmsByPepId( peptideIds: Seq[Int] ): Map[Int,Array[LocatedPtm]] = {
    
    // Retrieve PTM definition map
    val ptmDefMap = this.ptmDefinitionMap
    
    val locatedPtmMapBuilder = scala.collection.immutable.Map.newBuilder[Int,Array[LocatedPtm]]
    
    for( (pepId, pepPtmRecords) <- this.getPeptidePtmRecordsByPepId( peptideIds ) ) {
      
      var locatedPtms = new ArrayBuffer[LocatedPtm]
      for( pepPtmRecord <- pepPtmRecords ) {
        
        // Retrieve PTM definition
        val ptmSpecifId = pepPtmRecord("ptm_specificity_id").asInstanceOf[Int]
        
        // FIXME: remove this check when peptide_ptm insertion is fixed
        if( ptmSpecifId > 0 ) {
          val ptmDef = ptmDefMap( ptmSpecifId )
                  
          // Build located PTM
          val locatedPtm = PtmDefinitionBuilder.buildLocatedPtm( ptmDef, pepPtmRecord("seq_position").asInstanceOf[Int] )
          locatedPtms += locatedPtm
        }
        
      }
      
      locatedPtmMapBuilder += ( pepId -> locatedPtms.toArray )
      
    }
        
    locatedPtmMapBuilder.result()
      
  }
  
  def getPeptides( peptideIds: Seq[Int] ): Array[Peptide] = {
    
    val maxNbIters = psDb.maxVariableNumber
    
    // Declare some vars
    var pepColNames: Seq[String] = null
    val pepRecords = new ArrayBuffer[Map[String,Any]](0)
    var modifiedPepIdSet = new scala.collection.mutable.HashSet[Int]
    val psDbTx = psDb.getOrCreateTransaction
    
    // Iterate over groups of peptide ids
    peptideIds.grouped(maxNbIters).foreach( tmpPepIds => {      
      
      // Retrieve peptide PTMs for the current group of peptide ids
      psDbTx.selectAndProcess( "SELECT * FROM peptide WHERE id IN ("+ tmpPepIds.mkString(",") +")" ) { r =>
          
        if( pepColNames == null ) { pepColNames = r.columnNames }
        
        // Build the peptide PTM record
        val peptideRecord = pepColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        pepRecords += peptideRecord
        
        // Map the record by its id    
        if( peptideRecord("ptm_string").asInstanceOf[String] != null ) {
          modifiedPepIdSet += peptideRecord("id").asInstanceOf[Int]
        }
        
      }
    })
    
    // Load peptide PTM map corresponding to the modified peptides
    val locatedPtmsByPepId = this.getLocatedPtmsByPepId( modifiedPepIdSet.toArray[Int] )
    
    this._buildPeptides( pepRecords, locatedPtmsByPepId )

  }
  
  def getPeptidesForSequences( peptideSeqs: Seq[String] ): Array[Peptide] = {
    
    val maxNbIters = psDb.maxVariableNumber
    
    // Declare some vars
    var pepColNames: Seq[String] = null
    val pepRecords = new ArrayBuffer[Map[String,Any]](0)
    var modifiedPepIdSet = new scala.collection.mutable.HashSet[Int]
    val psDbTx = psDb.getOrCreateTransaction
    
    // Iterate over groups of peptide ids
    peptideSeqs.grouped(maxNbIters).foreach( tmpPepSeqs => {
      
      val quotedSeqs = tmpPepSeqs.map { "'"+_+"'"}
      
      // Retrieve peptide PTMs for the current group of peptide ids
      psDbTx.selectAndProcess( "SELECT * FROM peptide WHERE sequence IN ("+ quotedSeqs.mkString(",") +")" ) { r =>
          
        if( pepColNames == null ) { pepColNames = r.columnNames }
        
        // Build the peptide PTM record
        val peptideRecord = pepColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        pepRecords += peptideRecord
        
        // Map the record by its id    
        if( peptideRecord("ptm_string").asInstanceOf[String] != null ) {
          modifiedPepIdSet += peptideRecord("id").asInstanceOf[Int]
        }
        
      }
    })
    
    // Load peptide PTM map corresponding to the modified peptides
    val locatedPtmsByPepId = this.getLocatedPtmsByPepId( modifiedPepIdSet.toArray[Int] )
    
    this._buildPeptides( pepRecords, locatedPtmsByPepId )
    
  }
  
  private def _buildPeptides( pepRecords: Seq[Map[String,Any]],
                              locatedPtmsByPepId: Map[Int,Array[LocatedPtm]] ): Array[Peptide] = {
    
    // Iterate over peptide records to convert them into peptide objects
    var peptides = new ArrayBuffer[Peptide](pepRecords.length)
    
    for( pepRecord <- pepRecords ) {

      val pepId = pepRecord("id").asInstanceOf[Int]
      val sequence = pepRecord("sequence").asInstanceOf[String]
      val locatedPtms = locatedPtmsByPepId.getOrElse(pepId, Array.empty[LocatedPtm] )
      
      // Build peptide object
      val peptide = new Peptide( id = pepId,
                                 sequence = sequence,
                                 ptmString = pepRecord("ptm_string").asInstanceOf[String],
                                 ptms = locatedPtms,
                                 calculatedMass = pepRecord("calculated_mass").asInstanceOf[Double]
                                 )
      peptides += peptide

    }
    
    peptides.toArray
    
  }
 
  def getPeptide( peptideSeq: String, pepPtms: Array[LocatedPtm] ): Option[Peptide] = {
    
    val tmpPep = new Peptide( sequence = peptideSeq, ptms = pepPtms )
    
    val psDbTx = psDb.getOrCreateTransaction()
    val pepId = psDbTx.select( "SELECT id FROM peptide WHERE sequence = ? AND ptm_string = ?",
                                tmpPep.sequence, tmpPep.ptmString ) { _.nextInt } (0)
    
    if( pepId == None ) None
    else {
      tmpPep.id = pepId.get
      Some(tmpPep)
    }
  }
  
}
