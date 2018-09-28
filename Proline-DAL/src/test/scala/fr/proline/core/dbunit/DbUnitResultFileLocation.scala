package fr.proline.core.dbunit

/**
 * @author David Bouyssie
 *
 */
abstract class DbUnitResultFileLocation {
  
  def datastoreDirPath: String
  lazy val udsDbDatasetPath = datastoreDirPath + "/uds-db.xml"
  lazy val msiDbDatasetPath = datastoreDirPath + "/msi-db.xml"
  lazy val lcmsDbDatasetPath = datastoreDirPath + "/lcms-db.xml"

}

case object Init_Dataset extends DbUnitResultFileLocation {
  val datastoreDirPath = JInit_Dataset.datastoreDirPath
}

case object GRE_F068213_M2_4_TD_EColi extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/GRE_F068213_M2.4_TD_EColi"
}

case object STR_F063442_F122817_MergedRSMs extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/STR_F063442_F122817_MergedRSMs"
}

case object STR_F063442_F122817_MergedRSMs_SC extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/STR_F063442_F122817_MergedRSMs_SC"
}

case object STR_F063442_Mascot_v2_2 extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/STR_F063442_Mascot_v2.2"
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

case object SmallRuns_XIC extends DbUnitResultFileLocation {
  val datastoreDirPath = "/dbunit_samples/SmallRuns_XIC"
}