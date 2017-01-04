package fr.proline.core.om.storer.lcms

import java.io.File
import scala.collection.mutable.ArrayBuffer
import fr.profi.jdbc.PreparedStatementWrapper
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.om.model.lcms._
import fr.proline.core.om.model.msi.Instrument
import fr.proline.core.om.storer.lcms.impl._
import fr.proline.repository.DriverType

trait IFeatureWriter {
  
  def insertFeatures(features: Seq[Feature], rawMapId: Long, linkToPeakels: Boolean): Seq[Feature]
  
  def linkFeaturesToPeakels(features: Seq[Feature], rawMapId: Long): Unit
  
  def insertProcessedMapFeatureItems(processedMap: ProcessedMap): Unit
  
}

trait IPeakelWriter {
  
  def insertPeakels(peakels: Seq[Peakel], rawMapId: Long)
  
}

trait IRawMapStorer {
  
  def featureWriter: IFeatureWriter
  
  def peakelWriter: Option[IPeakelWriter]
  
  def storeRawMap( rawMap: RawMap, storeFeatures: Boolean = true, storePeakels: Boolean = false ): Unit
  
}

trait IScanSequenceStorer {
  
  def storeScanSequence( scanSeq: LcMsScanSequence ) : Unit
}

trait IRunStorer {
  
  def storeLcMsRun( run: LcMsRun ) : Unit
}

object FeatureWriter {
  
  def apply( lcmsDbCtx: LcMsDbConnectionContext ): IFeatureWriter = {
    lcmsDbCtx.getDriverType match {
      case DriverType.POSTGRESQL => new PgFeatureWriter(lcmsDbCtx)
      case _ => new SQLFeatureWriter(lcmsDbCtx)
    }    
  }
}

object PeakelWriter {
  
  def apply( lcmsDbCtx: LcMsDbConnectionContext ): IPeakelWriter = {
    lcmsDbCtx.getDriverType match {
      case DriverType.POSTGRESQL => new PgPeakelWriter(lcmsDbCtx)
      case _ => new SQLPeakelWriter(lcmsDbCtx)
    }
  }
}

/** A factory object for implementations of the IRunMapStorer trait */
object RawMapStorer {
  
  def apply( lcmsDbCtx: LcMsDbConnectionContext ): IRawMapStorer = {
    
    val peakelWriter = PeakelWriter(lcmsDbCtx)
    new SQLRawMapStorer(lcmsDbCtx, FeatureWriter(lcmsDbCtx), Some(peakelWriter) )
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
  
  def apply( lcmsDbCtx: LcMsDbConnectionContext ): IProcessedMapStorer = {
    new SQLProcessedMapStorer(lcmsDbCtx, new SQLFeatureWriter(lcmsDbCtx))
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
  
  def apply( lcmsDbCtx: LcMsDbConnectionContext ): IMasterMapStorer = {
    new SQLMasterMapStorer(lcmsDbCtx, new SQLFeatureWriter(lcmsDbCtx))
    /*if( lcmsDbCtx.isJPA ) new JPAMasterMapStorer(lcmsDbCtx)
    else {
      lcmsDbCtx.getDriverType match {
        //case DriverType.POSTGRESQL => new PgMasterMapStorer(lcmsDbCtx)
        case _ => new SQLMasterMapStorer(lcmsDbCtx)
      }
    }*/
    
  }
  
}
