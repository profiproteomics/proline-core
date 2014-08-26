package fr.proline.core.dbunit

/**
 * @author David Bouyssie
 *
 */
sealed abstract class DbUnitResultFileDatastore {
  
  def datastoreDirPath: String
  val udsDbDatasetPath = datastoreDirPath + "/uds-db.xml"
  val psDbDatasetPath = datastoreDirPath + "/ps-db.xml"
  val msiDbDatasetPath = datastoreDirPath + "/msi-db.xml"

}

case object GRE_F068213_M2_4_TD_EColi extends DbUnitResultFileDatastore {
  val datastoreDirPath = "/dbunit_samples/GRE_F068213_M2.4_TD_EColi"
}

case object STR_F063442_F122817_MergedRSMs extends DbUnitResultFileDatastore {
  val datastoreDirPath = "/dbunit_samples/STR_F063442_F122817_MergedRSMs"
}

case object STR_F122817_Mascot_v2_3 extends DbUnitResultFileDatastore {
  val datastoreDirPath = "/dbunit_samples/STR_F122817_Mascot_v2.3"
}

case object STR_F136482_CTD extends DbUnitResultFileDatastore {
  val datastoreDirPath = "/dbunit_samples/STR_F136482_CTD"
}

case object TLS_F027737_MTD_no_varmod extends DbUnitResultFileDatastore {
  val datastoreDirPath = "/dbunit_samples/TLS_F027737_MTD_no_varmod"
}