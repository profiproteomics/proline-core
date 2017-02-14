package fr.proline.core.om.storer.msi.impl

import scala.util.Sorting
import org.junit.Assert._
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.dal.AbstractMultipleDBTestCase
import fr.proline.core.util.generator.msi.ResultSetFakeGenerator
import fr.proline.repository.DriverType
import fr.profi.util.MathUtils.EPSILON_HIGH_PRECISION
import fr.profi.util.MathUtils.EPSILON_LOW_PRECISION
import fr.profi.util.StringUtils
import fr.proline.context.DatabaseConnectionContext

@Test
class JPARsStorerTest extends AbstractMultipleDBTestCase with StrictLogging {

  val milliToNanos = 1000000L

  val projectIdForTest = 1

  @Before
  def initTests() = {
    logger.info("Initializing Dbs")

    super.initDBsDBManagement(DriverType.H2)

    //Load Data
    msiDBTestCase.loadDataSet("/dbunit/datasets/msi-db_init_dataset.xml")
    msiDBTestCase.loadDataSet("/fr/proline/core/om/msi/Init_Dataset.xml")
    psDBTestCase.loadDataSet("/dbunit/datasets/ps-db_init_dataset.xml")
    udsDBTestCase.loadCompositeDataSet(Array("/dbunit/datasets/uds-db_init_dataset.xml", "/fr/proline/core/om/uds/UDS_Simple_Dataset.xml"))
    pdiDBTestCase.loadDataSet("/dbunit/datasets/pdi/Proteins_Dataset.xml")

    logger.info("Dbs successfully initialized")
  }

  /**
   * Creates some ResultSets with {{{ResultSetFakeBuilder}}} from Proline-OM ''test'' project
   * and persists them into Msi Db using a {{{JPARsStorer}}} instance.
   */
  //TODO : Creation d'un cas de figure qui leve une exception
  //  @Test
  //  def testRollBack() {
  //    import scala.collection.JavaConversions.collectionAsScalaIterable
  //
  //    var start = System.nanoTime
  //    val rsb = new ResultSetFakeBuilder( 10, 2 )
  //
  //    val resultSet = rsb.toResultSet()
  //    var stop = System.nanoTime
  //    logger.info( "ResultSet creation time: " + ( ( stop - start ) / milliToNanos ) )
  //
  //    start = System.nanoTime
  //    storer.storeResultSet( resultSet, stContext )
  //    stop = System.nanoTime
  //
  //    logger.info( "ResultSet " + resultSet.id + " persisted time: " + ( ( stop - start ) / milliToNanos ) )
  //
  //    start = System.nanoTime
  //    val resultSet2 = new ResultSetFakeBuilder( 10, 2 ).toResultSet
  //    val errMsiSearchPL = new Peaklist(
  //         id= Peaklist.generateNewId,
  //         fileType= resultSet2.msiSearch.peakList.fileType,
  //         path= resultSet2.msiSearch.peakList.path,
  //         rawFileName= resultSet2.msiSearch.peakList.rawFileName,
  //         msLevel=  resultSet2.msiSearch.peakList.msLevel
  //     )
  //
  //    val errMsiSearch = new MSISearch (
  //        id=resultSet.msiSearch.id,
  //        resultFileName=resultSet.msiSearch.resultFileName,
  //        submittedQueriesCount=resultSet.msiSearch.submittedQueriesCount,
  //        searchSettings=resultSet.msiSearch.searchSettings ,
  //        peakList = errMsiSearchPL,
  //        date = resultSet.msiSearch.date
  //     )
  //
  //    resultSet2.msiSearch = errMsiSearch
  //    stop = System.nanoTime
  //    logger.info( "ResultSet 2 creation time: " + ( ( stop - start ) / milliToNanos ) )
  //
  //    start = System.nanoTime
  //    storer.storeResultSet( resultSet2, stContext )
  //    stop = System.nanoTime
  //
  //    val rsList : List[fr.proline.core.orm.msi.ResultSet] = stContext.msiEm.createQuery("FROM fr.proline.core.orm.msi.ResultSet",classOf[fr.proline.core.orm.msi.ResultSet]).getResultList.toList
  //    assertEquals(1, rsList.size)
  //    assertEquals(resultSet.id, rsList(0).getId)
  //  }

  /**
   * Creates some ResultSets with {{{ResultSetFakeBuilder}}} from Proline-OM ''test'' project
   * and persists them into Msi Db using a {{{JPARsStorer}}} instance.
   */
  @Test
  def testRsStorer() {

    for (i <- 1 to 3) {
      //Fait par le Storer: Attentte partage transaction TODO
      //      val msiTransaction = stContext.msiEm.getTransaction
      //      var msiTransacOk: Boolean = false

      //    	try {
      //    		msiTransaction.begin()
      //    		msiTransacOk = false

      logger.info("Creating a new fake Result Set")

      var start = System.nanoTime
      val rsGen = new ResultSetFakeGenerator(10, 2)

      val resultSet = rsGen.toResultSet()

      // FIXME 1: find a way to communicate with a UDSdb fake      
      resultSet.msiSearch.get.peakList.peaklistSoftware.id = 1
      resultSet.msiSearch.get.peakList.id = 1

      var stop = System.nanoTime

      logger.info("ResultSet creation time: " + ((stop - start) / milliToNanos))

      /* Used to add some existing Peptides (#1 .. #6) */
      //      var peptideId = 1
      //      for (peptideMatch <- resultSet.peptideMatches if (peptideId < 7)) {
      //        val peptide = peptideMatch.peptide
      //
      //        if (peptide != null) {
      //          peptide.id = peptideId
      //
      //          peptideId += 1
      //        }
      //
      //      }

      start = System.nanoTime
      val storer = new JPARsStorer()

      storer.storeResultSet(resultSet, dsConnectorFactoryForTest, projectIdForTest)

      stop = System.nanoTime
      ////Fait par le Storer: Attentte partage transaction TODO
      //      	msiTransaction.commit
      //      	msiTransacOk = true

      logger.info("ResultSet persisted time: " + ((stop - start) / milliToNanos))

      val resultSetId = resultSet.id

      logger.info("ResultSet #" + resultSetId + " persisted time: " + ((stop - start) / milliToNanos))

      /* JPA Db Contexts */

      val pdiDb = new DatabaseConnectionContext(dsConnectorFactoryForTest.getPdiDbConnector)

      val psDb = new DatabaseConnectionContext(dsConnectorFactoryForTest.getPsDbConnector)

      val msiDb = new DatabaseConnectionContext(dsConnectorFactoryForTest.getMsiDbConnector(projectIdForTest))

      val provider = new ORMResultSetProvider(msiDb, psDb, pdiDb)

      val loadedResultSet = provider.getResultSet(resultSetId)

      msiDb.close()

      psDb.close()

      msiDb.close()

      assertTrue("Loaded ResultSet #" + resultSetId, loadedResultSet.isDefined)

      compareRs(resultSet, loadedResultSet.get)
      //Fait par le Storer: Attentte partage transaction TODO
      //    	} finally{
      //    	  /* Check msiTransaction integrity */
      //    		if ((msiTransaction != null) && !msiTransacOk) {
      //    			try {
      //    				msiTransaction.rollback()
      //    			} catch {
      //          	case ex => logger.error("Error rollbacking Msi Db transaction", ex)
      //    			}
      //    		}
      //    	}

    } // End fo throw 3 RS

  }

  @After
  override def tearDown() = {
    super.tearDown()
  }

  private def compareRs(src: ResultSet, loaded: ResultSet) {
    assertEquals("ResultSet.name", src.name, loaded.name)

    /* Checking Peptides and PeptideMatches */
    val srcPeptideMatches = src.peptideMatches

    /* Sort by Peptide(Sequence, ptmString) */
    val peptMatchOrdering = new Ordering[PeptideMatch] {

      def compare(a: PeptideMatch, b: PeptideMatch): Int = {
        val peptide1 = a.peptide
        val peptide2 = b.peptide

        val compSequence = peptide1.sequence.compareTo(peptide2.sequence)
        if (compSequence == 0) {
          if (peptide1.ptmString == null) {

            if (peptide2.ptmString == null) {
              0
            } else {
              1 // null < peptide2.ptmString
            }

          } else if (peptide2.ptmString == null) {
            -1 // peptide1.ptmString > null
          } else {
            peptide1.ptmString.compareTo(peptide2.ptmString)
          }
        } else {
          compSequence
        }

      }

    }

    Sorting.quickSort(srcPeptideMatches)(peptMatchOrdering)

    val srcPeptideMatchesLength = srcPeptideMatches.length

    val loadedPeptideMatches = loaded.peptideMatches

    Sorting.quickSort(loadedPeptideMatches)(peptMatchOrdering)

    val loadedPeptideMatchesLength = loadedPeptideMatches.length

    assertEquals("PeptideMatches.length", srcPeptideMatchesLength, loadedPeptideMatchesLength)

    for (index <- 0 to (srcPeptideMatchesLength - 1)) {
      comparePeptideMatch(srcPeptideMatches(index), loadedPeptideMatches(index))
    }

    /* Checking ProteinMatches */
    val srcProteinMatches = src.proteinMatches

    val protMatchOrdering = new Ordering[ProteinMatch] {

      def compare(a: ProteinMatch, b: ProteinMatch): Int = {
        // FIXME handle same accession but diffs ProteinMatch (seqDatabases ?)
        a.accession.compareTo(b.accession)
      }

    }

    Sorting.quickSort(srcProteinMatches)(protMatchOrdering)

    val srcProteinMatchesLength = srcProteinMatches.length

    val loadedProteinMatches = loaded.proteinMatches

    Sorting.quickSort(loadedProteinMatches)(protMatchOrdering)

    val loadedProteinMatchesLength = loadedProteinMatches.length

    assertEquals("proteinMatches.length", srcProteinMatchesLength, loadedProteinMatchesLength)

    for (index <- 0 to (srcProteinMatchesLength - 1)) {
      compareProteinMatch(srcProteinMatches(index), loadedProteinMatches(index))
    }

  }

  private def comparePeptideMatch(src: PeptideMatch, loaded: PeptideMatch) {
    /* Check some fields */
    assertEquals("PeptideMatch.rank", src.rank, loaded.rank)
    assertEquals("PeptideMatch.score", src.score, loaded.score, EPSILON_LOW_PRECISION)
    assertEquals("PeptideMatch.scoreType", src.scoreType, loaded.scoreType)
    /* DeltaMoz are floats in DataBase, for double computations use 1e-14 */
    assertEquals("PeptideMatch.deltaMoz", src.deltaMoz, loaded.deltaMoz, EPSILON_LOW_PRECISION)

    comparePeptide(src.peptide, loaded.peptide)
  }

  private def comparePeptide(src: Peptide, loaded: Peptide) {

    def normalizeString(str: String): String = {

      if (StringUtils.isEmpty(str)) {
        null
      } else {
        str
      }

    }

    /* Check some fields */
    assertEquals("Peptide.sequence", src.sequence, loaded.sequence)
    assertEquals("Peptide.ptmString", normalizeString(src.ptmString), normalizeString(loaded.ptmString))
    assertEquals("Peptide.calculatedMass", src.calculatedMass, loaded.calculatedMass, EPSILON_HIGH_PRECISION)
  }

  private def compareProteinMatch(src: ProteinMatch, loaded: ProteinMatch) {

    def normalizeId(id: Long): Long = {

      if (id < 0L) {
        0L
      } else {
        id
      }

    }

    def normalizeArrayLength(arr: Array[_]): Int = {

      if (arr == null) {
        0
      } else {
        arr.length
      }

    }

    /* Check some fields */
    assertEquals("ProteinMatch.accession", src.accession, loaded.accession)
    assertEquals("ProteinMatch.taxonId", normalizeId(src.taxonId), normalizeId(loaded.taxonId))

    assertEquals("ProteinMatch.proteinId", normalizeId(src.getProteinId), normalizeId(loaded.getProteinId))

    assertEquals("ProteinMatch.seqDatabaseIds", normalizeArrayLength(src.seqDatabaseIds), normalizeArrayLength(loaded.seqDatabaseIds))

    assertEquals("ProteinMatch.sequenceMatches", normalizeArrayLength(src.sequenceMatches), normalizeArrayLength(loaded.sequenceMatches))
  }

}
