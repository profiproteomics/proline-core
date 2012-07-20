package fr.proline.core.om.provider.msi.impl

import net.noerd.prequel.SQLFormatterImplicits._
import fr.proline.core.dal.{MsiDb,PsDb}
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.PtmSpecificity
import fr.proline.core.om.model.msi.PtmDefinition
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.provider.msi.IPeptideProvider

class SQLPeptideProvider( psDb: PsDb ) extends SQLPTMProvider( psDb ) with IPeptideProvider {
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
  
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
    val ptmDefMap = this.ptmDefinitionById
    
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
  
  def getPeptidesAsOptions( peptideIds: Seq[Int] ): Array[Option[Peptide]] = {
    
    val peptides = this.getPeptides( peptideIds )
    val pepById = peptides.map { pep => pep.id -> pep } toMap
    
    val optPeptidesBuffer = new ArrayBuffer[Option[Peptide]]
    peptideIds.foreach { pepId =>
      optPeptidesBuffer += pepById.get( pepId )
    }
    
    optPeptidesBuffer.toArray
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
    
    val peptides = this._buildPeptides( pepRecords, locatedPtmsByPepId )
    
    peptides
    /*val optPeptidesBuffer = new ArrayBuffer[Option[Peptide]]
    
    val nbPeptides = peptides.length
    var pepIdx = 0
    for( pepSeq <- peptideSeqs ) {
      var foundPep = peptides(pepIdx)
      
      while( foundPep.sequence == pepSeq && pepIdx < nbPeptides  ) {
        pepIdx += 1
        optPeptidesBuffer += Some(foundPep)
        foundPep = peptides(pepIdx)
      }
      
      if( pepIdx < nbPeptides ) optPeptidesBuffer += None
      
    }
    
    optPeptidesBuffer.toArray*/

    
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
