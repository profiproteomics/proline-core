package fr.proline.core.om.storer.msi.impl

import scala.util.Sorting
import org.junit.Assert._
import org.junit.Test
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.dal.DatabaseManagementTestCase
import fr.proline.core.om.model.msi.Peptide
import fr.proline.core.om.model.msi.PeptideMatch
import fr.proline.core.om.model.msi.ProteinMatch
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.provider.msi.impl.ORMResultSetProvider
import fr.proline.core.orm.utils.JPAUtil
import fr.proline.core.orm.utils.StringUtils
import fr.proline.core.utils.generator.ResultSetFakeBuilder
import fr.proline.repository.utils.DatabaseTestCase
import fr.proline.repository.utils.DatabaseUtils
import org.junit.Before
import org.junit.After
import fr.proline.core.orm.msi.MsiSearch
import fr.proline.core.om.model.msi.MSISearch
import fr.proline.core.om.model.msi.Peaklist


@Test
class JPARsStorerTest extends Logging {

  val milliToNanos = 1000000L

  val epsilon = 1e-6f // DeltaMoz are floats in DataBase, for double computations use 1e-14
  val msiTransaction = null
  var pdiDBTestCase = new PDIDatabaseTestCase()
  var msiDBTestCase = new MSIDatabaseTestCase()
  var psDBTestCase = new PSDatabaseTestCase()
  var udsDBTestCase = new UDSDatabaseTestCase()
  
  var stContext: StorerContext = null
  var dbMgntTest : DatabaseManagementTestCase= null
  var storer : JPARsStorer = null

  @Before
  def initTests()={
    logger.info("Initializing Dbs")    
    msiDBTestCase.initDatabase()
    msiDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.MSI_Key.getPersistenceUnitName())
    msiDBTestCase.loadDataSet("/fr/proline/core/om/msi/Init_Dataset.xml")
    
      /* Init Ps Db connection */    
    psDBTestCase.initDatabase()
    psDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.PS_Key.getPersistenceUnitName())
    psDBTestCase.loadDataSet("/fr/proline/core/om/ps/Unimod_Dataset.xml")

    /* Init Uds Db connection */    
    udsDBTestCase.initDatabase()
    udsDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.UDS_Key.getPersistenceUnitName())
    udsDBTestCase.loadDataSet("/fr/proline/core/om/uds/UDS_Simple_Dataset.xml")

    /* Init Pdi Db connection */    
    pdiDBTestCase.initDatabase()
    pdiDBTestCase.initEntityManager(JPAUtil.PersistenceUnitNames.PDI_Key.getPersistenceUnitName())
    pdiDBTestCase.loadDataSet("/fr/proline/core/om/pdi/Proteins_Dataset.xml")

    logger.info("Dbs succesfully initialized")
    dbMgntTest = new DatabaseManagementTestCase(udsDBTestCase.getConnector, pdiDBTestCase.getConnector, psDBTestCase.getConnector, msiDBTestCase.getConnector)
    storer = new JPARsStorer(dbMgntTest, msiDBTestCase.getConnector)
    stContext = new StorerContext(dbMgntTest, dbMgntTest.getCurrentMsiConnector())
  }
  
  @After
  def tearDown() = {
    if(this.stContext != null)
    	this.stContext.closeOpenedEM()
    	
    pdiDBTestCase.tearDown()
    udsDBTestCase.tearDown()
    psDBTestCase.tearDown()
    msiDBTestCase.tearDown()
    
    logger.info("Dbs succesfully closed")
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
      	val rsb = new ResultSetFakeBuilder(10, 2)

      	val resultSet = rsb.toResultSet()
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
      	storer.storeResultSet(resultSet, stContext)
      	stop = System.nanoTime
////Fait par le Storer: Attentte partage transaction TODO
//      	msiTransaction.commit
//      	msiTransacOk = true
      	
      	logger.info("ResultSet persisted time: " + ((stop - start) / milliToNanos))

      	val resultSetId = resultSet.id

      	logger.info("ResultSet #" + resultSetId + " persisted time: " + ((stop - start) / milliToNanos))
      
      	val provider = new ORMResultSetProvider(stContext.msiEm, stContext.psEm, stContext.pdiEm)

      	val loadedResultSet = provider.getResultSet(resultSetId)

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
    }// End fo throw 3 RS
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
    assertEquals("PeptideMatch.score", src.score, loaded.score, epsilon)
    assertEquals("PeptideMatch.scoreType", src.scoreType, loaded.scoreType)
    assertEquals("PeptideMatch.deltaMoz", src.deltaMoz, loaded.deltaMoz, epsilon)

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
    assertEquals("Peptide.calculatedMass", src.calculatedMass, loaded.calculatedMass, epsilon)
  }

  private def compareProteinMatch(src: ProteinMatch, loaded: ProteinMatch) {

    def normalizeId(id: Int) {

      if (id < 0) {
        0
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

class MSIDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_MSI_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_msi.properties";
  }

}

class PSDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_PS_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_ps.properties";
  }

}

class UDSDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_UDS_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_uds.properties";
  }

}

class PDIDatabaseTestCase extends DatabaseTestCase {

  override def getSQLScriptLocation(): String = {
    DatabaseUtils.H2_DATABASE_PDI_SCRIPT_LOCATION
  }

  override def getPropertiesFilename(): String = {
    return "/db_pdi.properties";
  }

}
