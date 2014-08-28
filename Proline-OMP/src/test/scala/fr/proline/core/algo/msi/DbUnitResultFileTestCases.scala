package fr.proline.core.algo.msi

import org.junit.BeforeClass

import com.typesafe.scalalogging.slf4j.Logging

import fr.profi.jdbc.easy._
import fr.proline.core.dbunit._
import fr.proline.repository.DriverType

object STR_F122817_Mascot_v2_3_TEST_CASE extends AbstractDbUnitResultFileTestCase with Logging {

  // Define the interface to be implemented
  val driverType = DriverType.H2
  val dbUnitResultFile = STR_F122817_Mascot_v2_3
  val targetRSId = 1L
  val decoyRSId = Option.empty[Long]
  
  this.loadResultFile()
  this.setUp()
  
}

