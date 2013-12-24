package fr.proline.core.om.storer.lcms

import java.io.File
import fr.proline.context.DatabaseConnectionContext
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.storer.lcms.impl._
import fr.proline.repository.DriverType

trait IRawMapStorer {
  
  def storeRawMap( rawMap: RawMap, storePeaks: Boolean = false ): Unit
  
}

trait IScanSequenceStorer {
  
  def storeScanSequence( scanSeq: LcMsScanSequence ) : Unit
}

trait IRunStorer {
  
  def storeLcMsRun( run: LcMsRun ) : Unit
}

/** A factory object for implementations of the IRunMapStorer trait */
object RawMapStorer {
  
  def apply( lcmsDbCtx: DatabaseConnectionContext ): IRawMapStorer = {
    
    new SQLRawMapStorer(lcmsDbCtx)
    
    /*if( lcmsDbCtx.isJPA ) new JPARunMapStorer(lcmsDbCtx)
    else {
      lcmsDbCtx.getDriverType match {
        //case DriverType.POSTGRESQL => new PgRunMapStorer(lcmsDbCtx)
        case _ => new SQLRunMapStorer(lcmsDbCtx)
      }
    }*/
    
  }
}

trait IProcessedMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  import fr.proline.core.om.model.lcms.Feature
  
  def insertProcessedMap( processedMap: ProcessedMap ): Long
  def storeProcessedMap( processedMap: ProcessedMap, storeClusters: Boolean = false ): Unit
  def storeFeatureClusters( features: Seq[Feature] ): Unit
  
}

/** A factory object for implementations of the IProcessedMapStorer trait */
object ProcessedMapStorer {
  
  def apply( lcmsDbCtx: DatabaseConnectionContext ): IProcessedMapStorer = {
    new SQLProcessedMapStorer(lcmsDbCtx)
    /*
    if( lcmsDbCtx.isJPA ) new JPAProcessedMapStorer(lcmsDbCtx)
    else {
      lcmsDbCtx.getDriverType match {
        //case DriverType.POSTGRESQL => new PgProcessedMapStorer(lcmsDbCtx)
        case _ => new SQLProcessedMapStorer(lcmsDbCtx)
      }
    }*/
  }
}

trait IMasterMapStorer {
  
  import fr.proline.core.om.model.lcms.ProcessedMap
  
  def storeMasterMap( processedMap: ProcessedMap ): Unit
  
 }

/** A factory object for implementations of the IMasterMapStorer trait */
object MasterMapStorer {
  
  def apply( lcmsDbCtx: DatabaseConnectionContext ): IMasterMapStorer = {
    new SQLMasterMapStorer(lcmsDbCtx)
    /*if( lcmsDbCtx.isJPA ) new JPAMasterMapStorer(lcmsDbCtx)
    else {
      lcmsDbCtx.getDriverType match {
        //case DriverType.POSTGRESQL => new PgMasterMapStorer(lcmsDbCtx)
        case _ => new SQLMasterMapStorer(lcmsDbCtx)
      }
    }*/
    
  }
  
}
