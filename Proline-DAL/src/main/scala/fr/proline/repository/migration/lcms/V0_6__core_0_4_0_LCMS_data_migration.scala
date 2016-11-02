package fr.proline.repository.migration.lcms

import java.sql.Connection

import scala.beans.BeanProperty
import scala.collection.mutable.HashMap

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.flywaydb.core.api.migration.jdbc.JdbcMigration
import com.typesafe.scalalogging.LazyLogging

import fr.profi.jdbc.easy._
import fr.profi.util.serialization.ProfiJson
import fr.profi.util.serialization.ProfiMsgPack
import fr.proline.context.LcMsDbConnectionContext
import fr.proline.core.dal.ProlineEzDBC
import fr.proline.core.om.provider.lcms.impl.SQLScanSequenceProvider
import fr.proline.repository.DriverType
import fr.proline.repository.ProlineDatabaseType

class V0_6__core_0_4_0_LCMS_data_migration extends JdbcMigration with LazyLogging {
  
  private final val LOG_PEAKEL_COUNT = 10000
  
  /**
   * Migrate LCMS db in order to:
   * - update the content of the feature_peakel_item.is_base_peakel column
   * - update the binary structure of peakel serialized data
   * 
   */
  override def migrate(lcMsConn: Connection) {
    
    val lcMsDbCtx = new LcMsDbConnectionContext(lcMsConn,DriverType.POSTGRESQL)
    // Be sure that the connection is not closed when the DatabaseConnectionContext is closed
    lcMsDbCtx.setClosableConnection(false)
    
    val ezDBC = ProlineEzDBC(lcMsDbCtx)
      
    val updateFeaturePeakelItemStmt = ezDBC.prepareStatementWrapper(
      "UPDATE feature_peakel_item SET is_base_peakel = ? WHERE peakel_id = ?"
    )
    val updatePeakelStmt = ezDBC.prepareStatementWrapper(
      "UPDATE peakel SET peaks = ? WHERE id = ?"
    )
    
    try {
      
      println("Updating is_base_peakel value of feature peakel items..." )
      
      /* --- Iterate over all LC-MS features --- */
      var basePeakelIndexByFeatureId = new HashMap[Long,Int]()
      ezDBC.selectAndProcess("SELECT id, serialized_properties FROM feature") { record =>
        
        // Retrieve record values
        val featureId = record.getLong("id")
        val serializedPropsOpt = record.getStringOption("serialized_properties")
        
        for( serializedProps <- serializedPropsOpt ) {
          val featureProps = ProfiJson.deserialize[FeaturePropertiesCoreV0_4_0](serializedProps)
          
          // Retrieve base peakel index from properties
          for( basePeakelIdx <- featureProps.getBasePeakelIndex ) {
            basePeakelIndexByFeatureId += featureId -> basePeakelIdx
          }
        }
      }
      
      /* --- Iterate over all LC-MS feature peakel items --- */
      ezDBC.selectAndProcess("SELECT feature_id, peakel_id, isotope_index FROM feature_peakel_item") { record =>
        
        // Retrieve record values
        val featureId = record.getLong("feature_id")
        val peakelId = record.getLong("peakel_id")
        val isotopeIndex = record.getInt("isotope_index")
        
        // Update the is_base_peakel column if we have retrieve this information
        for( basePeakelIdx <- basePeakelIndexByFeatureId.get(featureId); if basePeakelIdx == isotopeIndex ) {
          updateFeaturePeakelItemStmt.executeWith(true, peakelId)
        }
      }
      // Clear basePeakelIndexByFeatureId
      basePeakelIndexByFeatureId.clear()
      basePeakelIndexByFeatureId = null
      
      val scanSeqIdByRawMapId = ezDBC.select("SELECT id, scan_sequence_id FROM raw_map") { record =>
        record.getLong("id") -> record.getLong("scan_sequence_id")
      } toMap
      
      val scanSeqIds = scanSeqIdByRawMapId.values.toArray.distinct
      val scanSequences = new SQLScanSequenceProvider(lcMsDbCtx).getScanSequences( scanSeqIds )
      val scanSequenceById = scanSequences.map( ss => ss.runId -> ss ).toMap // runId is the scan_sequence_id
      
      logger.info("Updating binary structure of peakel serialized data.." )
      println("Updating binary structure of peakel serialized data..." ) // TODO: remove me 
      
      /* --- Iterate over all LC-MS peakels --- */
      var updatedPeakelsCount = 0
      ezDBC.selectAndProcess("SELECT id, peaks, first_scan_id, map_id FROM peakel") { record =>
        
        // Retrieve record values
        val peakelId = record.getLong("id")
        val peaksAsBytes = record.getBytes("peaks")
        val firstScanId = record.getLong("first_scan_id")
        val rawMapId = record.getLong("map_id")
        
        // Retrieve the corresponding scan sequence
        val scanSeqId = scanSeqIdByRawMapId(rawMapId)
        val scanSeq = scanSequenceById(scanSeqId)
        
        // Parse LC-MS peaks
        val lcMsPeaks = ProfiMsgPack.deserialize[Array[LcMsPeakCoreV0_4_0]](peaksAsBytes)
        
        // Define some primitive arrays
        val peaksCount = lcMsPeaks.length
        val scanIds = new Array[Long](peaksCount)
        val elutionTimes = new Array[Float](peaksCount)
        val mzValues = new Array[Double](peaksCount)
        val intensityValues = new Array[Float](peaksCount)
        
        // Convert array of LcMsPeaks into arrays of primitives
        var i = 0
        while( i < peaksCount ) {
          
          val peak = lcMsPeaks(i)
          val elutionTime = peak.elutionTime
          val scan = scanSeq.getScanAtTime(elutionTime, msLevel = 1)
          
          if( i == 0 ) {
            require( firstScanId == scan.id, "can't convert elution times into scan ids")
          }

          // Update primitives
          scanIds(i) = scan.id
          elutionTimes(i) = elutionTime
          mzValues(i) = peak.moz
          intensityValues(i) = peak.intensity

          i += 1
        }
        
        // Create the peakel data matrix
        val peakelDataMatrix = PeakelDataMatrixCoreV0_4_0(
          scanIds = scanIds,
          elutionTimes = elutionTimes,
          mzValues = mzValues,
          intensityValues = intensityValues
        )
        
        // Serialize the peakel data matrix
        val peakelMatrixAsBytes = ProfiMsgPack.serialize(peakelDataMatrix)
        
        // Update the peaks column
        updatePeakelStmt.executeWith(peakelMatrixAsBytes, peakelId)
        
        updatedPeakelsCount += 1
        
        if ( (updatedPeakelsCount % LOG_PEAKEL_COUNT) == 0) {
          println(s"Updated $updatedPeakelsCount peakels..." )
        }
      }

    } finally {
      updateFeaturePeakelItemStmt.close()
      updatePeakelStmt.close()
      lcMsDbCtx.close()
    }

  }

}

case class FeaturePropertiesCoreV0_4_0(
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var predictedElutionTime: Option[Float] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Integer] )
  @BeanProperty var peakelsCount: Option[Int] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Integer] )
  @BeanProperty var basePeakelIndex: Option[Int] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var overlapCorrelation: Option[Float] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  @BeanProperty var overlapFactor: Option[Float] = None
)

@org.msgpack.annotation.Message
case class LcMsPeakCoreV0_4_0(
  // MessagePack requires mutable fields
  var moz: Double,
  var elutionTime: Float,
  var intensity: Float
) {
  // Plain constructor needed for MessagePack
  def this() = this(Double.NaN,Float.NaN,Float.NaN)
}

@org.msgpack.annotation.Message
case class PeakelDataMatrixCoreV0_4_0(
  // MessagePack requires mutable fields
  var scanIds: Array[Long],
  var elutionTimes: Array[Float],
  var mzValues: Array[Double],
  var intensityValues: Array[Float]
) {
  // Plain constructor needed for MessagePack
  def this() = this(null,null,null,null)
}
