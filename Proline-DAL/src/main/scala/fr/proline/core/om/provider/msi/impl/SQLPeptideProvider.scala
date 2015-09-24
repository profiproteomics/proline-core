package fr.proline.core.om.provider.msi.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap

import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.util.StringUtils
import fr.profi.util.primitives._
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.ps.PsDbPeptideColumns
import fr.proline.core.dal.tables.ps.PsDbPeptidePtmColumns
import fr.proline.core.dal.tables.ps.PsDbPeptidePtmTable
import fr.proline.core.dal.tables.ps.PsDbPeptideTable
import fr.proline.core.om.builder.PeptideBuilder
import fr.proline.core.om.builder.PtmDefinitionBuilder
import fr.proline.core.om.model.msi.LocatedPtm
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.provider.msi.IPeptideProvider
import fr.proline.repository.ProlineDatabaseType

// TODO: move the cache into the peptide builder object ???
object SQLPeptideProvider extends LazyLogging {

  // Create a static HashMap to cache loaded peptides
  // TODO: use cache for other queries than getPeptides(peptideIds)
  // TODO: implement cache at context level

  val GIGA = 1024 * 1024 * 1024L

  val INITIAL_CAPACITY = 16 // Default Java Map  initial capacity and load factor
  val LOAD_FACTOR = 0.75f

  /* Max distinct Peptides per ResultSet estimated : 137 145 */
  val INITIAL_CACHE_SIZE = 200000
  val CACHE_SIZE_INCREMENT = 100000
  /* Max distinct Peptides per MSI / Project estimated : 3 129 096 */
  val MAXIMUM_CACHE_SIZE = 4000000

  val CACHE_SIZE = calculateCacheSize()

  /* Use a Java LinkedHashMap configured as LRU cache (with access-order) ; @GuardedBy("itself") */
  private val _peptideCache = new java.util.LinkedHashMap[Long, Peptide](INITIAL_CAPACITY, LOAD_FACTOR, true) {

    override def removeEldestEntry(eldest: java.util.Map.Entry[Long, Peptide]): Boolean = {
      (size > CACHE_SIZE)
    }

  }

  /**
   * Clear (purge all entries) this static cache.
   */
  def clear() {

    _peptideCache.synchronized {
      _peptideCache.clear()
    }

    logger.info("SQLPeptideProvider cache cleared")
  }

  /* Private methods */
  private def calculateCacheSize(): Int = {
    val maxMemory = Runtime.getRuntime.maxMemory

    val cacheSize = if (maxMemory > (4 * GIGA)) {
      /* Big cacheSize = 200000 + (100000 for each GiB over 4 GiB) */
      val extendedMemory = maxMemory - 4 * GIGA

      val nBlocks = (extendedMemory + GIGA - 1) / GIGA // rounding up

      val bigCacheSize = (INITIAL_CACHE_SIZE + nBlocks * CACHE_SIZE_INCREMENT).asInstanceOf[Int]

      logger.trace("MaxMemory: " + maxMemory + "  NBlocks over 4 Gib : " + nBlocks + "  bigCacheSize: " + bigCacheSize)

      bigCacheSize.min(MAXIMUM_CACHE_SIZE)
    } else {
      INITIAL_CACHE_SIZE
    }

    logger.info("SQLPeptideProvider cacheSize : " + cacheSize)

    cacheSize
  }

}

class SQLPeptideProvider(psDbCtx: DatabaseConnectionContext) extends SQLPTMProvider(psDbCtx) with IPeptideProvider with LazyLogging {

  require( psDbCtx.getProlineDatabaseType == ProlineDatabaseType.PS, "PsDb connection required")
  
  import scala.collection.mutable.ArrayBuffer
  import scala.collection.mutable.HashMap
  import SQLPeptideProvider._

  /** Returns a map of peptide PTMs grouped by the peptide id */
  def getPeptidePtmRecordsByPepId(peptideIds: Seq[Long]): Map[Long, Seq[AnyMap]] = {
    if( peptideIds.isEmpty ) return Map()

    DoJDBCReturningWork.withEzDBC(psDbCtx, { psEzDBC =>

      val mapBuilder = scala.collection.immutable.Map.newBuilder[Long, AnyMap]
      val maxNbIters = psEzDBC.getInExpressionCountLimit
      val pepPtmRecords = new ArrayBuffer[AnyMap]()

      // Iterate over groups of peptide ids
      peptideIds.grouped(maxNbIters).foreach(tmpPepIds => {

        // Retrieve peptide PTMs for the current group of peptide ids
        val pepPtmQuery = new SelectQueryBuilder1(PsDbPeptidePtmTable).mkSelectQuery((t, c) =>
          List(t.*) -> "WHERE " ~ t.PEPTIDE_ID ~ " IN(" ~ tmpPepIds.mkString(",") ~ ")"
        )

        psEzDBC.selectAndProcess(pepPtmQuery) { row =>
          pepPtmRecords += row.toAnyMap()
        }
        
      })

      pepPtmRecords.groupBy(r => r.getLong(PsDbPeptidePtmColumns.PEPTIDE_ID) )

    })

  }

  def getLocatedPtmsByPepId(peptideIds: Seq[Long]): Map[Long, Array[LocatedPtm]] = {
    PtmDefinitionBuilder.buildLocatedPtmsGroupedByPepId(getPeptidePtmRecordsByPepId(peptideIds),ptmDefinitionById)
  }

  def getPeptides(peptideIds: Seq[Long]): Array[Peptide] = {
    if (peptideIds.isEmpty) return Array.empty[Peptide]

    var uncachedPepIds: Seq[Long] = null
    var cachedPeps: Seq[Peptide] = null

    _peptideCache.synchronized {
      val (cachedPepIds, localUncachedPepIds) = peptideIds.partition(_peptideCache.containsKey(_))
      uncachedPepIds = localUncachedPepIds
      cachedPeps = cachedPepIds.map(_peptideCache.get(_))
    } // End of synchronized block on _peptideCache

    cachedPeps.toArray ++ DoJDBCReturningWork.withEzDBC(psDbCtx, { psEzDBC =>

      val maxNbIters = psEzDBC.getInExpressionCountLimit

      // Declare some vars
      val pepRecords = new ArrayBuffer[AnyMap](0)
      var modifiedPepIdSet = new scala.collection.mutable.HashSet[Long]

      // Iterate over groups of peptide ids
      uncachedPepIds.grouped(maxNbIters).foreach(tmpPepIds => {

        val pepQuery = new SelectQueryBuilder1(PsDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.*) -> "WHERE " ~ t.ID ~ " IN(" ~ tmpPepIds.mkString(",") ~ ")"
        )

        // Retrieve peptides for the current group of peptide ids
        psEzDBC.selectAndProcess(pepQuery) { row =>
          
          val peptideRecord = row.toAnyMap()
          pepRecords += peptideRecord

          // Map the record by its id
          if(peptideRecord.get(PsDbPeptideColumns.PTM_STRING).isDefined) {
            modifiedPepIdSet += peptideRecord.getLong(PsDbPeptideColumns.ID)
          }

        }
      })

      // Load peptide PTM map corresponding to the modified peptides
      val locatedPtmsByPepId = this.getLocatedPtmsByPepId(modifiedPepIdSet.toArray[Long])

      this._buildPeptides(pepRecords, locatedPtmsByPepId)

    })

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

    DoJDBCReturningWork.withEzDBC(psDbCtx, { psEzDBC =>

      val maxNbIters = psEzDBC.getInExpressionCountLimit

      // Declare some vars
      val pepRecords = new ArrayBuffer[AnyMap](0)
      var modifiedPepIdSet = new scala.collection.mutable.HashSet[Long]

      // Iterate over groups of peptide ids
      peptideSeqs.grouped(maxNbIters).foreach(tmpPepSeqs => {

        val quotedSeqs = tmpPepSeqs.map { "'" + _ + "'" }

        val pepQuery = new SelectQueryBuilder1(PsDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.*) -> "WHERE " ~ t.SEQUENCE ~ " IN(" ~ quotedSeqs.mkString(",") ~ ")"
        )

        // Retrieve peptide PTMs for the current group of peptide ids
        psEzDBC.selectAndProcess(pepQuery) { row =>

          // Build the peptide PTM record
          val peptideRecord = row.toAnyMap()
          pepRecords += peptideRecord

          // Map the record by its id
          if ( peptideRecord.isDefined(PsDbPeptideColumns.PTM_STRING) ) {
            modifiedPepIdSet += toLong(peptideRecord(PsDbPeptideColumns.ID))
          }

        }
      })

      // Load peptide PTM map corresponding to the modified peptides
      val locatedPtmsByPepId = this.getLocatedPtmsByPepId(modifiedPepIdSet.toArray[Long])

      val peptides = this._buildPeptides(pepRecords, locatedPtmsByPepId)

      peptides

    })

  }

  private def _buildPeptides(
    pepRecords: Seq[IValueContainer],
    locatedPtmsByPepId: Map[Long, Array[LocatedPtm]]
  ): Array[Peptide] = {

    // Iterate over peptide records to convert them into peptide objects
    val peptides = new ArrayBuffer[Peptide](pepRecords.length)

    for (pepRecord <- pepRecords) {

      val pepId = pepRecord.getLong("id")
      var peptide: Peptide = null

      _peptideCache.synchronized {

        /* putIfAbsent holding _peptideCache intrinsec lock */
        val cachedPeptide = _peptideCache.get(pepId)

        if (cachedPeptide == null) {
          /* Build new peptide object */
          peptide = PeptideBuilder.buildPeptide(pepRecord, locatedPtmsByPepId.get(pepId))

          /* Cache this new built peptide */
          _peptideCache.put(pepId, peptide)
        } else {
          peptide = cachedPeptide
        }

      } // End of synchronized block on _peptideCache

      peptides += peptide
    }

    /* Trace if _peptideCache is full */
    var currentCacheSize: Int = -1

    _peptideCache.synchronized {
      currentCacheSize = _peptideCache.size
    } // End of synchronized block on _peptideCache

    if (currentCacheSize >= CACHE_SIZE) {
      logger.info("SQLPeptideProvider._peptideCache is full : " + currentCacheSize)
    }

    peptides.toArray
  }

  def getPeptide(peptideSeq: String, pepPtms: Array[LocatedPtm]): Option[Peptide] = {

    DoJDBCReturningWork.withEzDBC(psDbCtx, { psEzDBC =>

      val tmpPep = new Peptide(sequence = peptideSeq, ptms = pepPtms)

      val resultPepIds: Seq[Long] = if (StringUtils.isEmpty(tmpPep.ptmString)) {
        val pepIdQuery = new SelectQueryBuilder1(PsDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.ID) -> "WHERE " ~ t.SEQUENCE ~ " = ? AND " ~ t.PTM_STRING ~ " IS NULL"
        )
        psEzDBC.select(pepIdQuery, tmpPep.sequence) { v => toLong(v.nextAny) }
      } else {
        val pepIdQuery = new SelectQueryBuilder1(PsDbPeptideTable).mkSelectQuery((t, c) =>
          List(t.ID) -> "WHERE " ~ t.SEQUENCE ~ " = ? AND " ~ t.PTM_STRING ~ " = ?"
        )
        psEzDBC.select(pepIdQuery, tmpPep.sequence, tmpPep.ptmString) { v => toLong(v.nextAny) }
      }

      if (resultPepIds.length == 0) None
      else {
        tmpPep.id = resultPepIds(0)
        Some(tmpPep)
      }

    })
  }

  def getPeptidesAsOptionsBySeqAndPtms(peptideSeqsAndPtms: Seq[Pair[String, Array[LocatedPtm]]]): Array[Option[Peptide]] = {
    if (peptideSeqsAndPtms.isEmpty) return Array()
    
    DoJDBCReturningWork.withEzDBC(psDbCtx, { psEzDBC =>

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

        val pepQuery = new SelectQueryBuilder1(PsDbPeptideTable).mkSelectQuery((t, c) =>
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

    })

  }

}
