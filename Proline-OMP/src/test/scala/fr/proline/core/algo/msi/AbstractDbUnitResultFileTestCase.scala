package fr.proline.core.algo.msi

import org.junit.BeforeClass

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.AbstractEmptyDatastoreTestCase
import fr.proline.core.dal.DoJDBCWork
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.parser.msi.DbUnitResultFile
import fr.proline.repository.DriverType

abstract class AbstractDbUnitResultFileTestCase extends AbstractEmptyDatastoreTestCase with DbUnitResultFileLoading {
  
  val useJPA = false
  
  @BeforeClass
  @throws(classOf[Exception])
  override def setUp() {
    super.setUp()
    
    DbUnitResultFileUtils.storeDbUnitResultFile( getResultFile(), executionContext )
  }
  
}

trait DbUnitResultFileLoading extends Logging {

  // Define the interface to be implemented
  val driverType: DriverType
  val dbUnitResultFile: DbUnitResultFileLocation
  val targetRSId: Long
  val decoyRSId: Option[Long]
  
  protected var readRS: ResultSet = null
  protected var loadedRF: DbUnitResultFile = null
  
  def getRS(): ResultSet = {
    this.readRS
  }
  
  def getResultFile(): DbUnitResultFile = {
    this.loadedRF
  }

  @BeforeClass
  protected def loadResultFile() = {
    
    // Load the DbUnit result file
    loadedRF = DbUnitResultFileUtils.loadDbUnitResultFile(dbUnitResultFile)

    logger.info("Result file succesfully loaded !")

    val rs = loadedRF.getResultSet(wantDecoy = false)
    if (decoyRSId.isDefined) rs.decoyResultSet = Some( loadedRF.getResultSet(wantDecoy = true) )
    
    readRS = rs
  }
  
}
