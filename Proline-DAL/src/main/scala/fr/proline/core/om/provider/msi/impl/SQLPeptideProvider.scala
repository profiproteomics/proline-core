package fr.proline.core.om.provider.msi.impl

import com.weiglewilczek.slf4s.Logging
import fr.profi.jdbc.easy._
import fr.profi.jdbc.SQLQueryExecution
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.util.StringUtils
import fr.proline.repository.DatabaseContext

class SQLPeptideProvider( psDbCtx: DatabaseContext, sqlExec: SQLQueryExecution ) extends SQLPTMProvider( psDbCtx, sqlExec ) with IPeptideProvider with Logging {
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
  
  /** Returns a map of peptide PTMs grouped by the peptide id */
  def getPeptidePtmRecordsByPepId( peptideIds: Seq[Int] ): Map[Int,Array[Map[String,Any]]] = {
    
    val mapBuilder = scala.collection.immutable.Map.newBuilder[Int,Map[String,Any]]
    
    val maxNbIters = sqlExec.getInExpressionCountLimit
    var colNames: Seq[String] = null
    val pepPtmRecords = new ArrayBuffer[Map[String,Any]] // TODO: replace by an array
    
    // Iterate over groups of peptide ids
    peptideIds.grouped(maxNbIters).foreach( tmpPepIds => {      
      
      // Retrieve peptide PTMs for the current group of peptide ids
      sqlExec.selectAndProcess( "SELECT * FROM peptide_ptm WHERE peptide_id IN ("+ tmpPepIds.mkString(",") +")" ) { r =>
          
        if( colNames == null ) { colNames = r.columnNames }
        
        // Build the peptide PTM record
        val peptidePtmRecord = colNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
        pepPtmRecords += peptidePtmRecord
        
        }
      }
      
    )
    
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
    if( peptideIds.length == 0 ) return Array.empty[Peptide]
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    val maxNbIters = sqlExec.getInExpressionCountLimit
    
    // Declare some vars
    var pepColNames: Seq[String] = null
    val pepRecords = new ArrayBuffer[Map[String,Any]](0)
    var modifiedPepIdSet = new scala.collection.mutable.HashSet[Int]
    
    // Iterate over groups of peptide ids
    peptideIds.grouped(maxNbIters).foreach( tmpPepIds => {      
      
      // Retrieve peptide PTMs for the current group of peptide ids
      sqlExec.selectAndProcess( "SELECT * FROM peptide WHERE id IN ("+ tmpPepIds.mkString(",") +")" ) { r =>
          
        if( pepColNames == null ) { pepColNames = r.columnNames }
        
        // Build the peptide PTM record
        val peptideRecord = pepColNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
        pepRecords += peptideRecord
        
        // Map the record by its id    
        if( peptideRecord("ptm_string").asInstanceOf[String] != null ) {
          modifiedPepIdSet += peptideRecord("id").asInstanceOf[AnyVal]
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
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    val maxNbIters = sqlExec.getInExpressionCountLimit
    
    // Declare some vars
    var pepColNames: Seq[String] = null
    val pepRecords = new ArrayBuffer[Map[String,Any]](0)
    var modifiedPepIdSet = new scala.collection.mutable.HashSet[Int]
    
    // Iterate over groups of peptide ids
    peptideSeqs.grouped(maxNbIters).foreach( tmpPepSeqs => {
      
      val quotedSeqs = tmpPepSeqs.map { "'"+_+"'"}
      
      // Retrieve peptide PTMs for the current group of peptide ids
      sqlExec.selectAndProcess( "SELECT * FROM peptide WHERE sequence IN ("+ quotedSeqs.mkString(",") +")" ) { r =>
          
        if( pepColNames == null ) { pepColNames = r.columnNames }
        
        // Build the peptide PTM record
        val peptideRecord = pepColNames.map( colName => ( colName -> r.nextAnyRefOrElse(null) ) ).toMap
        pepRecords += peptideRecord
        
        // Map the record by its id    
        if( peptideRecord("ptm_string").asInstanceOf[String] != null ) {
          modifiedPepIdSet += peptideRecord("id").asInstanceOf[AnyVal]
        }
        
      }
    })
    
    // Load peptide PTM map corresponding to the modified peptides
    val locatedPtmsByPepId = this.getLocatedPtmsByPepId( modifiedPepIdSet.toArray[Int] )
    
    val peptides = this._buildPeptides( pepRecords, locatedPtmsByPepId )
    
    peptides
  }
  
  private def _buildPeptides( pepRecords: Seq[Map[String,Any]],
                              locatedPtmsByPepId: Map[Int,Array[LocatedPtm]] ): Array[Peptide] = {
    
    import fr.proline.util.primitives.LongOrIntAsInt._
    
    // Iterate over peptide records to convert them into peptide objects
    var peptides = new ArrayBuffer[Peptide](pepRecords.length)
    
    for( pepRecord <- pepRecords ) {

      val pepId: Int = pepRecord("id").asInstanceOf[AnyVal]
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
    
    var resultPepIds: Seq[Int] = null
    
    if( StringUtils.isEmpty(tmpPep.ptmString ) ) {
      resultPepIds = sqlExec.select(
                               "SELECT id FROM peptide WHERE sequence = ? AND ptm_string IS NULL ",
                               tmpPep.sequence, tmpPep.ptmString ) { _.nextInt }
    }
    else {
      resultPepIds = sqlExec.select( 
                              "SELECT id FROM peptide WHERE sequence = ? AND ptm_string = ?",
                              tmpPep.sequence, tmpPep.ptmString ) { _.nextInt }
    }    
                 
    if( resultPepIds.length == 0 ) None
    else {
      tmpPep.id = resultPepIds(0)
      Some(tmpPep)
    }
  }
  
  def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[Pair[String, Array[LocatedPtm]]] ) : Array[Option[Peptide]] = {
    
    val maxNbIters = sqlExec.getInExpressionCountLimit
    
    // Retrieve peptide sequences and map peptides by their unique key
    val quotedSeqs = new Array[String](peptideSeqsAndPtms.length)
    val pepKeys = new Array[String](peptideSeqsAndPtms.length)
    val peptideByUniqueKey = new HashMap[String,Peptide]()
    
    var pepIdx = 0
    for( (pepSeq, locPtms) <- peptideSeqsAndPtms ) {
      
      val peptide = new Peptide( sequence = pepSeq, ptms = locPtms )
      val pepKey = peptide.uniqueKey
      peptideByUniqueKey += (pepKey -> peptide)
      
      quotedSeqs(pepIdx) = sqlExec.dialect.quoteString(pepSeq)
      
      pepKeys(pepIdx) = pepKey
      
      pepIdx += 1
    }
    
    // Query peptides based on a distinct list of peptide sequences
    // Queries are performed using successive groups of sequences (maximum length is driver dependant)
    quotedSeqs.distinct.grouped(maxNbIters).foreach { tmpQuotedSeqs =>
      this.logger.trace( "search for peptides in the database using %d sequences".format(tmpQuotedSeqs.length) )
      
      sqlExec.select( "SELECT id, sequence, ptm_string FROM peptide WHERE sequence IN ("+tmpQuotedSeqs.mkString(",") +")") { r =>
        val( id, sequence, ptmString ) = (r.nextInt, r.nextString, r.nextStringOrElse("") )        
        val uniqueKey = sequence + "%" + ptmString
        
        if( peptideByUniqueKey.contains(uniqueKey) ) {
          peptideByUniqueKey(uniqueKey).id = id
        }
      }
    }
    
    val peptidesAsOpt = pepKeys.map { pepKey =>
      val peptide = peptideByUniqueKey(pepKey)
      if( peptide.id > 0 ) Some(peptide)
      else None
    }
    
    peptidesAsOpt                      
  }
  

  
}
