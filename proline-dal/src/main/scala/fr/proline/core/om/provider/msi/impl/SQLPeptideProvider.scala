package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.LongMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.util.StringUtils
import fr.profi.util.collection._
import fr.profi.util.primitives._
import fr.proline.context.MsiDbConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.msi._
import fr.proline.core.om.builder.PeptideBuilder
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.core.om.provider.msi.cache.IPeptideCache

class SQLPeptideProvider(
  override val msiDbCtx: MsiDbConnectionContext,
  val peptideCache: IPeptideCache
) extends SQLPTMProvider(msiDbCtx) with IPeptideProvider with LazyLogging {

  def this(peptideCacheExecutionContext: PeptideCacheExecutionContext) = {
    this(peptideCacheExecutionContext.getMSIDbConnectionContext, peptideCacheExecutionContext.getPeptideCache())
  }
    
  /** Returns a map of peptide PTMs grouped by the peptide id */
  def getPeptidePtmRecordsByPepId(peptideIds: Seq[Long]): LongMap[Seq[AnyMap]] = {
    if( peptideIds.isEmpty ) return LongMap()

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { psEzDBC =>

      val maxNbIters = psEzDBC.getInExpressionCountLimit
      val pepPtmRecords = new ArrayBuffer[AnyMap]()

      // Iterate over groups of peptide ids
      peptideIds.grouped(maxNbIters).foreach { tmpPepIds =>

        // Retrieve peptide PTMs for the current group of peptide ids
        val pepPtmQuery = new SelectQueryBuilder1(MsiDbPeptidePtmTable).mkSelectQuery((t, c) =>
          List(t.*) -> "WHERE " ~ t.PEPTIDE_ID ~ " IN(" ~ tmpPepIds.mkString(",") ~ ")"
        )

        psEzDBC.selectAndProcess(pepPtmQuery) { row =>
          pepPtmRecords += row.toAnyMap()
        }
        
      }

      pepPtmRecords.toSeq.groupByLong(r => r.getLong(MsiDbPeptidePtmColumns.PEPTIDE_ID) )
    }

  }

  def getLocatedPtmsByPepId(peptideIds: Seq[Long]): LongMap[Array[LocatedPtm]] = {
    PtmDefinitionBuilder.buildLocatedPtmsGroupedByPepId(
      getPeptidePtmRecordsByPepId(peptideIds),
      ptmDefinitionById
    )
  }

  def getPeptides(peptideIds: Seq[Long]): Array[Peptide] = {
    if (peptideIds.isEmpty) return Array.empty[Peptide]

    val uncachedPepIds = new ArrayBuffer[Long](peptideIds.length)
    val cachedPeps = new ArrayBuffer[Peptide](peptideIds.length)

    for( peptideId <- peptideIds ) {
      val peptideOpt = peptideCache.get(peptideId)
      if( peptideOpt.isDefined ) cachedPeps += peptideOpt.get
      else uncachedPepIds += peptideId
    }

    cachedPeps ++= DoJDBCReturningWork.withEzDBC(msiDbCtx) { psEzDBC =>

      val maxNbIters = psEzDBC.getInExpressionCountLimit

      // Declare some vars
      val pepRecords = new ArrayBuffer[AnyMap](0)
      var modifiedPepIdSet = new scala.collection.mutable.HashSet[Long]

      // Iterate over groups of peptide ids
      uncachedPepIds.grouped(maxNbIters).foreach { tmpPepIds =>

        val pepQuery = new SelectQueryBuilder1(MsiDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ tmpPepIds.mkString(",") ~ ")"
        )

        // Retrieve peptides for the current group of peptide ids
        psEzDBC.selectAndProcess(pepQuery) { row =>
          
          val peptideRecord = row.toAnyMap()
          pepRecords += peptideRecord

          // Map the record by its id
          if(peptideRecord.get(MsiDbPeptideColumns.PTM_STRING).isDefined) {
            modifiedPepIdSet += peptideRecord.getLong(MsiDbPeptideColumns.ID)
          }

        }
      }

      // Load peptide PTM map corresponding to the modified peptides
      val locatedPtmsByPepId = this.getLocatedPtmsByPepId(modifiedPepIdSet.toArray[Long])

      this._buildPeptides(pepRecords, locatedPtmsByPepId)
    }
    
    cachedPeps.toArray
  }

  def getPeptidesAsOptions(peptideIds: Seq[Long]): Array[Option[Peptide]] = {
    if (peptideIds.isEmpty) return Array()

    val peptides = this.getPeptides(peptideIds)
    val pepById = peptides.map { pep => pep.id -> pep } toMap

    val optPeptidesBuffer = new ArrayBuffer[Option[Peptide]]
    peptideIds.foreach { pepId =>
      optPeptidesBuffer += pepById.get(pepId)
    }

    optPeptidesBuffer.toArray
  }

  def getPeptidesForSequences(peptideSeqs: Seq[String]): Array[Peptide] = {
    if (peptideSeqs.isEmpty) return Array()

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { psEzDBC =>

      val maxNbIters = psEzDBC.getInExpressionCountLimit

      // Declare some vars
      val pepRecords = new ArrayBuffer[AnyMap](0)
      var modifiedPepIdSet = new scala.collection.mutable.HashSet[Long]

      // Iterate over groups of peptide ids
      peptideSeqs.grouped(maxNbIters).foreach { tmpPepSeqs =>

        val quotedSeqs = tmpPepSeqs.map { "'" + _ + "'" }

        val pepQuery = new SelectQueryBuilder1(MsiDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.*) -> "WHERE " ~ t.SEQUENCE ~ " IN(" ~ quotedSeqs.mkString(",") ~ ")"
        )

        // Retrieve peptide PTMs for the current group of peptide ids
        psEzDBC.selectAndProcess(pepQuery) { row =>

          // Build the peptide PTM record
          val peptideRecord = row.toAnyMap()
          pepRecords += peptideRecord

          // Map the record by its id
          if ( peptideRecord.isDefined(MsiDbPeptideColumns.PTM_STRING) ) {
            modifiedPepIdSet += toLong(peptideRecord(MsiDbPeptideColumns.ID))
          }

        }
      }

      // Load peptide PTM map corresponding to the modified peptides
      val locatedPtmsByPepId = this.getLocatedPtmsByPepId(modifiedPepIdSet.toArray[Long])

      val peptides = this._buildPeptides(pepRecords, locatedPtmsByPepId)

      peptides

    }

  }

  private def _buildPeptides(
    pepRecords: Seq[IValueContainer],
    locatedPtmsByPepId: LongMap[Array[LocatedPtm]]
  ): Array[Peptide] = {

    // Iterate over peptide records to convert them into peptide objects
    val peptides = new ArrayBuffer[Peptide](pepRecords.length)

    for (pepRecord <- pepRecords) {

      val pepId = pepRecord.getLong("id")

      val cachedPeptideOpt = peptideCache.get(pepId)

      val peptide = if (cachedPeptideOpt.isDefined) cachedPeptideOpt.get
      else {
        // Build new peptide object
        val newPeptide = PeptideBuilder.buildPeptide(pepRecord, locatedPtmsByPepId.get(pepId))

        // Cache this new built peptide
        peptideCache.put(pepId, newPeptide)
        
        newPeptide
      }
      
      peptides += peptide
    }

    /* Log if peptideCache is full */
    val currentCacheSize = peptideCache.size()
    if (currentCacheSize >= peptideCache.getCacheSize()) {
      logger.info("PeptideCache is full: " + currentCacheSize)
    }

    peptides.toArray
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm]): Option[Peptide] = {

    DoJDBCReturningWork.withEzDBC(msiDbCtx) { psEzDBC =>

      val tmpPep = new Peptide(sequence = peptideSeq, ptms = pepPtms)

      val resultPepIds: Seq[Long] = if (StringUtils.isEmpty(tmpPep.ptmString)) {
        val pepIdQuery = new SelectQueryBuilder1(MsiDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.ID) -> "WHERE " ~ t.SEQUENCE ~ " = ? AND " ~ t.PTM_STRING ~ " IS NULL"
        )
        psEzDBC.select(pepIdQuery, tmpPep.sequence) { v => toLong(v.nextAny) }
      } else {
        val pepIdQuery = new SelectQueryBuilder1(MsiDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.ID) -> "WHERE " ~ t.SEQUENCE ~ " = ? AND " ~ t.PTM_STRING ~ " = ?"
        )
        psEzDBC.select(pepIdQuery, tmpPep.sequence, tmpPep.ptmString) { v => toLong(v.nextAny) }
      }

      if (resultPepIds.isEmpty) None
      else {
        tmpPep.id = resultPepIds.head
        Some(tmpPep)
      }

    }
  }

  override def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[(String, Array[LocatedPtm])]): Array[Option[Peptide]] = {
    if (peptideSeqsAndPtms.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(msiDbCtx) { psEzDBC =>

      val maxNbIters = psEzDBC.getInExpressionCountLimit

      // Retrieve peptide sequences and map peptides by their unique key
      val quotedSeqs = new Array[String](peptideSeqsAndPtms.length)
      val pepKeys = new Array[String](peptideSeqsAndPtms.length)
      val peptideByUniqueKey = new HashMap[String, Peptide]()

      var pepIdx = 0
      for ((pepSeq, locPtms) <- peptideSeqsAndPtms) {

        val peptide = new Peptide(sequence = pepSeq, ptms = locPtms)
        val pepKey = peptide.uniqueKey
        peptideByUniqueKey += (pepKey -> peptide)

        quotedSeqs(pepIdx) = psEzDBC.dialect.quoteString(pepSeq)

        pepKeys(pepIdx) = pepKey

        pepIdx += 1
      }

      // Query peptides based on a distinct list of peptide sequences
      // Queries are performed using successive groups of sequences (maximum length is driver dependant)
      quotedSeqs.distinct.grouped(maxNbIters).foreach { tmpQuotedSeqs =>
        this.logger.trace("search for peptides in the database using %d sequences".format(tmpQuotedSeqs.length))

        val pepQuery = new SelectQueryBuilder1(MsiDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.ID, t.SEQUENCE, t.PTM_STRING) -> "WHERE " ~ t.SEQUENCE ~ " IN (" ~ tmpQuotedSeqs.mkString(",") ~ ")"
        )

        psEzDBC.selectAndProcess(pepQuery) { r =>
          val (id, sequence, ptmString) = (toLong(r.nextAny), r.nextString, r.nextStringOrElse(""))
          val uniqueKey = sequence + "%" + ptmString

          if (peptideByUniqueKey.contains(uniqueKey)) {
            peptideByUniqueKey(uniqueKey).id = id
          }
        }
      }

      val peptidesAsOpt = pepKeys.map { pepKey =>
        val peptide = peptideByUniqueKey(pepKey)
        if (peptide.id > 0) Some(peptide)
        else None
      }

      peptidesAsOpt
    }

  }

}
