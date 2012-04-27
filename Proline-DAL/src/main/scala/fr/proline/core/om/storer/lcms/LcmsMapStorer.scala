package fr.proline.core.om.storer.lcms

import fr.proline.core.dal.LcmsDb
import fr.proline.core.om.storer.lcms.impl._

trait IRunMapStorer {
  
  import fr.proline.core.om.model.lcms.LcmsMap
  import fr.proline.core.om.model.lcms.RunMap
  
  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit
  def insertMap( lcmsMap: LcmsMap, modificationTimestamp: java.util.Date ): Int
  
 }

/*
/** A factory object for implementations of the IRunMapStorer trait */
object RunMapStorer {
  def apply(driver: String ): IRunMapStorer = { driver match {
    case "pg" => new GenericRunMapStorer()
    case "sqlite" => new GenericRunMapStorer()
    case _ => new GenericRunMapStorer()
    }
  }
}
*/

trait IProcessedMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  import fr.proline.core.om.model.lcms.Feature
  
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = false ): Unit
  def storeFeatureClusters( features: Seq[Feature] ): Unit
  
}

/** A factory object for implementations of the IProcessedMapStorer trait */
object ProcessedMapStorer {
  def apply( lcmsDb: LcmsDb ): IProcessedMapStorer = { lcmsDb.config.driver match {
    case "org.postgresql.Driver" => new GenericProcessedMapStorer(lcmsDb)
    case "org.sqlite.JDBC" => new SQLiteProcessedMapStorer(lcmsDb)
    case _ => new GenericProcessedMapStorer(lcmsDb)
    }
  }
}

trait IMasterMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  
  def storeMasterMap( processedMap: ProcessedMap ): Unit
  
 }

/** A factory object for implementations of the IMasterMapStorer trait */
object MasterMapStorer {
  def apply( lcmsDb: LcmsDb ): IMasterMapStorer = { lcmsDb.config.driver match {
    case "org.postgresql.JDBC" => new GenericMasterMapStorer(lcmsDb)
    case "org.sqlite.JDBC" => new SQLiteMasterMapStorer(lcmsDb)
    case _ => new GenericMasterMapStorer(lcmsDb)
    }
  }
}
