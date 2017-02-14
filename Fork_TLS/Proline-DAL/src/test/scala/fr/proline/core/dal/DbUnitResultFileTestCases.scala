package fr.proline.core.dal

import org.junit.BeforeClass

import com.typesafe.scalalogging.StrictLogging

import fr.profi.jdbc.easy._
import fr.proline.core.dbunit._
import fr.proline.repository.DriverType

object STR_F122817_Mascot_v2_3_TEST_CASE extends AbstractDbUnitResultFileTestCase with StrictLogging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
  this.loadResultFile()
  this.setUp()
  
}

object GRE_F068213_M2_4_TD_EColi_TEST_CASE extends AbstractDbUnitResultFileTestCase with StrictLogging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val dbUnitResultFile = GRE_F068213_M2_4_TD_EColi
  val targetRSId = 2L
  val decoyRSId = Option.empty[Long]
  
  this.loadResultFile()
  this.setUp()
  
}

