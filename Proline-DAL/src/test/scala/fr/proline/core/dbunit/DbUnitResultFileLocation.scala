package fr.proline.core.dbunit

/**
 * @author David Bouyssie
 *
 */
// TODO: rename the file in DbUnitResultFileLocation
sealed abstract class DbUnitResultFileLocation {
  
  def datastoreDirPath: String
  lazy val udsDbDatasetPath = datastoreDirPath + "/uds-db.xml"
  lazy val psDbDatasetPath = datastoreDirPath + "/ps-db.xml"
  lazy val msiDbDatasetPath = datastoreDirPath + "/msi-db.xml"

}

case object GRE_F068213_M2_4_TD_EColi extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/GRE_F068213_M2.4_TD_EColi"
}

case object STR_F063442_F122817_MergedRSMs extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/STR_F063442_F122817_MergedRSMs"
}

case object STR_F122817_Mascot_v2_3 extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/STR_F122817_Mascot_v2.3"
}

case object STR_F136482_CTD extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/STR_F136482_CTD"
}

case object TLS_F027737_MTD_no_varmod extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/TLS_F027737_MTD_no_varmod"
}