package fr.proline.core.dal

import org.junit.BeforeClass

import com.typesafe.scalalogging.StrictLogging

import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.proline.core.dal.tables.msi.MsiDbPeaklistSoftwareTable
import fr.proline.core.dbunit._
import fr.proline.core.om.model.msi.IResultFile
import fr.proline.core.om.model.msi.ResultSet
import fr.proline.core.om.storer.msi.ResultFileStorer
import fr.proline.core.om.storer.msi.RsStorer
import fr.proline.core.om.storer.msi.impl.StorerContext
import fr.proline.core.dbunit.DbUnitResultFile
import fr.proline.repository.DriverType

abstract class AbstractDbUnitResultFileTestCase extends AbstractEmptyDatastoreTestCase with DbUnitResultFileLoading {
  
  val useJPA = true
  
  @BeforeClass
  @throws(classOf[Exception])
  override def setUp() {
    super.setUp()
    
    DbUnitResultFileUtils.storeDbUnitResultFile( getResultFile(), executionContext )
  }
  
}

