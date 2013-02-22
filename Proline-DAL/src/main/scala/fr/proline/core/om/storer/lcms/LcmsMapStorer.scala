package fr.proline.core.om.storer.lcms

import java.io.File
import fr.proline.core.dal.SQLQueryHelper
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ILcMsMap
import fr.proline.core.om.model.lcms.LcMsRun
import fr.proline.core.om.model.lcms.ProcessedMap
import fr.proline.core.om.model.lcms.RunMap
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.storer.lcms.impl.GenericMasterMapStorer
import fr.proline.core.om.storer.lcms.impl.GenericProcessedMapStorer
import fr.proline.core.om.storer.lcms.impl.SQLiteMasterMapStorer
import fr.proline.core.om.storer.lcms.impl.SQLiteProcessedMapStorer
import fr.proline.repository.DriverType

trait IRunMapStorer {
  
  import fr.proline.core.om.model.lcms.ILcMsMap
  import fr.proline.core.om.model.lcms.RunMap
  
  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit
  def insertMap( lcmsMap: ILcMsMap, modificationTimestamp: java.util.Date ): Int
  
 }


trait IRunStorer {
  
  def storeLcmsRun( run: LcMsRun, instrument: Instrument) : Unit
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
  
  def apply( lcmsSqlHelper: SQLQueryHelper ): IProcessedMapStorer = { lcmsSqlHelper.driverType match {
    //case DriverType.POSTGRESQL => new GenericProcessedMapStorer(lcmsDb.ezDBC)
    case DriverType.SQLITE => new SQLiteProcessedMapStorer(lcmsSqlHelper.ezDBC)
    case _ => new SQLiteProcessedMapStorer(lcmsSqlHelper.ezDBC)
    }
  }
}

trait IMasterMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  
  def storeMasterMap( processedMap: ProcessedMap ): Unit
  
 }

/** A factory object for implementations of the IMasterMapStorer trait */
object MasterMapStorer {
  def apply( lcmsDb: SQLQueryHelper ): IMasterMapStorer = { lcmsDb.driverType match {
    //case DriverType.POSTGRESQL => new GenericMasterMapStorer(lcmsDb)
    //case DriverType.SQLITE => new SQLiteMasterMapStorer(lcmsDb)
    case _ => new SQLiteMasterMapStorer(lcmsDb.ezDBC)
    }
  }
}
