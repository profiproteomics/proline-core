package fr.proline.core.dal

import com.typesafe.scalalogging.StrictLogging

import org.junit.BeforeClass

import fr.proline.core.dbunit.DbUnitResultFile
import fr.proline.core.dbunit.DbUnitResultFileLocation
import fr.proline.core.dbunit.DbUnitResultFileUtils
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.repository.DriverType

trait DbUnitResultFileLoading extends StrictLogging {

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
