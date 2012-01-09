package fr.proline.core.om.factory

import net.noerd.prequel.DatabaseConfig

class PeptideLoader( val psDb: DatabaseConfig ) {
  
  import fr.proline.core.om.factory.PtmDefinitionBuilder
  import _root_.fr.proline.core.om.msi.PeptideClasses._
  import _root_.fr.proline.core.om.msi.PtmClasses._
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
  
  /** Returns a map */
  lazy val ptmSpecificityMap : Map[Int,PtmSpecificity] = {
      
    // Load PTM specificities
    val ptmSpecifs = psDb.transaction { tx =>       
      tx.select( "SELECT * FROM ptm_specificity" ) { r => 
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
    
    // Load PTM records
    psDb.transaction { tx =>       
      tx.select( "SELECT * FROM ptm" ) { r => 
        
        if( ptmColNames == null ) { ptmColNames = r.columnNames }
        
        // Build the PTM record
        val ptmRecord = ptmColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
        ptmMapBuilder += ( ptmRecord("id").asInstanceOf[Int] -> ptmRecord )
        
        ()
      }
    }    
    val ptmRecordById = ptmMapBuilder.result()
    
    // Load PTM evidence records   
    var ptmEvidColNames: Seq[String] = null
    
    // Execute SQL query to load PTM evidence records
    val ptmEvidRecords = psDb.transaction { tx =>       
      tx.select( "SELECT * FROM ptm_evidence" ) { r => 
        
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
    }
    
    // Group PTM evidences by PTM id
    val ptmEvidRecordsByPtmId = ptmEvidRecords.groupBy( _.get("ptm_id").get.asInstanceOf[Int] )
    
    var ptmSpecifColNames: Seq[String] = null
    val ptmDefMapBuilder = scala.collection.immutable.Map.newBuilder[Int,PtmDefinition]
    
    // Load PTM specificity records
    psDb.transaction { tx =>
      tx.select( "SELECT * FROM ptm_specificity" ) { r => 
        
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
        
        ()
      }
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
    val maxNbIters = 998;
    var colNames: Seq[String] = null
    val pepPtmRecords = new ArrayBuffer[Map[String,Any]] // TODO: replace by an array
    
    // Iterate over groups of peptide ids
    peptideIds.grouped(maxNbIters).foreach( tmpPepIds => {      
      
      // Retrieve peptide PTMs for the current group of peptide ids
      psDb.transaction { tx =>       
        tx.select( "SELECT * FROM peptide_ptm WHERE peptide_id IN (" +
                   tmpPepIds.mkString(",") + ")" ) { r =>
          
          if( colNames == null ) { colNames = r.columnNames }
          
          // Build the peptide PTM record
          val peptidePtmRecord = colNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
          pepPtmRecords += peptidePtmRecord
          
          // Map the record by its id
          //mapBuilder += ( peptidePtmRecord("id").asInstanceOf[Int] -> peptidePtmRecord )
          
          ()
          }
        }
      }
    )
    
    //mapBuilder.result()
    
    pepPtmRecords.toArray.groupBy( _.get("peptide_id").get.asInstanceOf[Int] )
    
  }
  
  def getPeptides( peptideIds: Seq[Int] ): Seq[Peptide] = {

    // TODO: Check if database driver is SQLite
    //my $max_items_by_iter = $msi_rdb->driver eq 'sqlite' ? 998 : 50000;
    val maxNbIters = 998
    
    // Declare some vars
    var pepColNames: Seq[String] = null
    val pepRecords = new Array[Map[String,Any]](peptideIds.length)
    var modifiedPepIdSet = new scala.collection.mutable.HashSet[Int]
    
    // Initialize a peptide counter
    var pepCount = 0
    
    // Iterate over groups of peptide ids
    peptideIds.grouped(maxNbIters).foreach( tmpPepIds => {      
      
      // Retrieve peptide PTMs for the current group of peptide ids
      psDb.transaction { tx =>       
        tx.select( "SELECT * FROM peptide WHERE id IN (" +
                   tmpPepIds.mkString(",") + ")" ) { r =>
          
          if( pepColNames == null ) { pepColNames = r.columnNames }
          
          // Build the peptide PTM record
          val peptideRecord = pepColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
          pepRecords(pepCount) = peptideRecord
          
          // Map the record by its id
          //pepRecordMapBuilder += ( peptideRecord("id").asInstanceOf[Int] -> peptideRecord )         
          if( peptideRecord("ptm_string").asInstanceOf[String] != null ) {
            modifiedPepIdSet += peptideRecord("id").asInstanceOf[Int]
          }
          
          pepCount += 1
          
          ()
          }
        }
      }
    )
    
    // Retrieve PTM definition map
    val ptmDefMap = ptmDefinitionMap;
    
    // Load peptide PTM map corresponding to the modified peptides
    val pepPtmRecordsByPepId = getPeptidePtmRecordsByPepId( modifiedPepIdSet.toArray[Int] )
    
    // Iterate over peptide records to convert them into peptide objects
    var peptides = new Array[Peptide](pepCount)
    
    pepCount = 0
    for( pepRecord <- pepRecords ) {

      val pepId = pepRecord("id").asInstanceOf[Int]
      val sequence = pepRecord("sequence").asInstanceOf[String]
      val pepPtmRecords = pepPtmRecordsByPepId.getOrElse(pepId, new Array[Map[String,Any]](0) )
      
      var locatedPtms = new ArrayBuffer[LocatedPtm]
      for( pepPtmRecord <- pepPtmRecords ) {
        
        // Retrieve PTM definition
        val ptmSpecifId = pepPtmRecord("ptm_specificity_id").asInstanceOf[Int]
        val ptmDef = ptmDefMap( ptmSpecifId )
                
        // Build located PTM
        val locatedPtm = PtmDefinitionBuilder.buildLocatedPtm( sequence, ptmDef, pepPtmRecord("seq_position").asInstanceOf[Int] )
        locatedPtms += locatedPtm
        
      }
      
      // Build peptide object
      val peptide = new Peptide( id = pepId,
                                 sequence = sequence,
                                 ptmString = pepRecord("ptm_string").asInstanceOf[String],
                                 ptms = locatedPtms.toArray,
                                 calculatedMass = pepRecord("calculated_mass").asInstanceOf[Double]
                                 )
      peptides(pepCount) = peptide

      pepCount += 1
    }
    
    peptides
  }
  
}
