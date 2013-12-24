package fr.proline.core.dal.tables.lcms

import fr.proline.core.dal.tables._

object LcmsDbCacheColumns extends ColumnEnumeration {
  val $tableName = LcmsDbCacheTable.name
  val SCOPE = Column("scope")
  val ID = Column("id")
  val FORMAT = Column("format")
  val BYTE_ORDER = Column("byte_order")
  val DATA = Column("data")
  val COMPRESSION = Column("compression")
  val TIMESTAMP = Column("timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbCacheTable extends TableDefinition[LcmsDbCacheColumns.type]

object LcmsDbCacheTable extends LcmsDbCacheTable {
  val name = "cache"
  val columns = LcmsDbCacheColumns
}

object LcmsDbCompoundColumns extends ColumnEnumeration {
  val $tableName = LcmsDbCompoundTable.name
  val ID = Column("id")
  val EXPERIMENTAL_MASS = Column("experimental_mass")
  val THEORETICAL_MASS = Column("theoretical_mass")
  val ELUTION_TIME = Column("elution_time")
  val FORMULA = Column("formula")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val BEST_FEATURE_ID = Column("best_feature_id")
  val MAP_LAYER_ID = Column("map_layer_id")
  val MAP_ID = Column("map_id")
}

abstract class LcmsDbCompoundTable extends TableDefinition[LcmsDbCompoundColumns.type]

object LcmsDbCompoundTable extends LcmsDbCompoundTable {
  val name = "compound"
  val columns = LcmsDbCompoundColumns
}

object LcmsDbFeatureColumns extends ColumnEnumeration {
  val $tableName = LcmsDbFeatureTable.name
  val ID = Column("id")
  val MOZ = Column("moz")
  val INTENSITY = Column("intensity")
  val CHARGE = Column("charge")
  val ELUTION_TIME = Column("elution_time")
  val QUALITY_SCORE = Column("quality_score")
  val MS1_COUNT = Column("ms1_count")
  val MS2_COUNT = Column("ms2_count")
  val IS_CLUSTER = Column("is_cluster")
  val IS_OVERLAPPING = Column("is_overlapping")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val FIRST_SCAN_ID = Column("first_scan_id")
  val LAST_SCAN_ID = Column("last_scan_id")
  val APEX_SCAN_ID = Column("apex_scan_id")
  val THEORETICAL_FEATURE_ID = Column("theoretical_feature_id")
  val COMPOUND_ID = Column("compound_id")
  val MAP_LAYER_ID = Column("map_layer_id")
  val MAP_ID = Column("map_id")
}

abstract class LcmsDbFeatureTable extends TableDefinition[LcmsDbFeatureColumns.type]

object LcmsDbFeatureTable extends LcmsDbFeatureTable {
  val name = "feature"
  val columns = LcmsDbFeatureColumns
}

object LcmsDbFeatureClusterItemColumns extends ColumnEnumeration {
  val $tableName = LcmsDbFeatureClusterItemTable.name
  val CLUSTER_FEATURE_ID = Column("cluster_feature_id")
  val SUB_FEATURE_ID = Column("sub_feature_id")
  val PROCESSED_MAP_ID = Column("processed_map_id")
}

abstract class LcmsDbFeatureClusterItemTable extends TableDefinition[LcmsDbFeatureClusterItemColumns.type]

object LcmsDbFeatureClusterItemTable extends LcmsDbFeatureClusterItemTable {
  val name = "feature_cluster_item"
  val columns = LcmsDbFeatureClusterItemColumns
}

object LcmsDbFeatureMs2EventColumns extends ColumnEnumeration {
  val $tableName = LcmsDbFeatureMs2EventTable.name
  val FEATURE_ID = Column("feature_id")
  val MS2_EVENT_ID = Column("ms2_event_id")
  val RUN_MAP_ID = Column("run_map_id")
}

abstract class LcmsDbFeatureMs2EventTable extends TableDefinition[LcmsDbFeatureMs2EventColumns.type]

object LcmsDbFeatureMs2EventTable extends LcmsDbFeatureMs2EventTable {
  val name = "feature_ms2_event"
  val columns = LcmsDbFeatureMs2EventColumns
}

object LcmsDbFeatureObjectTreeMappingColumns extends ColumnEnumeration {
  val $tableName = LcmsDbFeatureObjectTreeMappingTable.name
  val FEATURE_ID = Column("feature_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class LcmsDbFeatureObjectTreeMappingTable extends TableDefinition[LcmsDbFeatureObjectTreeMappingColumns.type]

object LcmsDbFeatureObjectTreeMappingTable extends LcmsDbFeatureObjectTreeMappingTable {
  val name = "feature_object_tree_mapping"
  val columns = LcmsDbFeatureObjectTreeMappingColumns
}

object LcmsDbFeatureOverlapMappingColumns extends ColumnEnumeration {
  val $tableName = LcmsDbFeatureOverlapMappingTable.name
  val OVERLAPPED_FEATURE_ID = Column("overlapped_feature_id")
  val OVERLAPPING_FEATURE_ID = Column("overlapping_feature_id")
  val MAP_ID = Column("map_id")
}

abstract class LcmsDbFeatureOverlapMappingTable extends TableDefinition[LcmsDbFeatureOverlapMappingColumns.type]

object LcmsDbFeatureOverlapMappingTable extends LcmsDbFeatureOverlapMappingTable {
  val name = "feature_overlap_mapping"
  val columns = LcmsDbFeatureOverlapMappingColumns
}

object LcmsDbFeatureScoringColumns extends ColumnEnumeration {
  val $tableName = LcmsDbFeatureScoringTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbFeatureScoringTable extends TableDefinition[LcmsDbFeatureScoringColumns.type]

object LcmsDbFeatureScoringTable extends LcmsDbFeatureScoringTable {
  val name = "feature_scoring"
  val columns = LcmsDbFeatureScoringColumns
}

object LcmsDbInstrumentColumns extends ColumnEnumeration {
  val $tableName = LcmsDbInstrumentTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val SOURCE = Column("source")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbInstrumentTable extends TableDefinition[LcmsDbInstrumentColumns.type]

object LcmsDbInstrumentTable extends LcmsDbInstrumentTable {
  val name = "instrument"
  val columns = LcmsDbInstrumentColumns
}

object LcmsDbMapColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMapTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val DESCRIPTION = Column("description")
  val TYPE = Column("type")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val MODIFICATION_TIMESTAMP = Column("modification_timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val FEATURE_SCORING_ID = Column("feature_scoring_id")
}

abstract class LcmsDbMapTable extends TableDefinition[LcmsDbMapColumns.type]

object LcmsDbMapTable extends LcmsDbMapTable {
  val name = "map"
  val columns = LcmsDbMapColumns
}

object LcmsDbMapAlignmentColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMapAlignmentTable.name
  val FROM_MAP_ID = Column("from_map_id")
  val TO_MAP_ID = Column("to_map_id")
  val MASS_START = Column("mass_start")
  val MASS_END = Column("mass_end")
  val TIME_LIST = Column("time_list")
  val DELTA_TIME_LIST = Column("delta_time_list")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val MAP_SET_ID = Column("map_set_id")
}

abstract class LcmsDbMapAlignmentTable extends TableDefinition[LcmsDbMapAlignmentColumns.type]

object LcmsDbMapAlignmentTable extends LcmsDbMapAlignmentTable {
  val name = "map_alignment"
  val columns = LcmsDbMapAlignmentColumns
}

object LcmsDbMapLayerColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMapLayerTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val PROCESSED_MAP_ID = Column("processed_map_id")
  val MAP_SET_ID = Column("map_set_id")
}

abstract class LcmsDbMapLayerTable extends TableDefinition[LcmsDbMapLayerColumns.type]

object LcmsDbMapLayerTable extends LcmsDbMapLayerTable {
  val name = "map_layer"
  val columns = LcmsDbMapLayerColumns
}

object LcmsDbMapObjectTreeMappingColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMapObjectTreeMappingTable.name
  val MAP_ID = Column("map_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class LcmsDbMapObjectTreeMappingTable extends TableDefinition[LcmsDbMapObjectTreeMappingColumns.type]

object LcmsDbMapObjectTreeMappingTable extends LcmsDbMapObjectTreeMappingTable {
  val name = "map_object_tree_mapping"
  val columns = LcmsDbMapObjectTreeMappingColumns
}

object LcmsDbMapSetColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMapSetTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val MAP_COUNT = Column("map_count")
  val CREATION_TIMESTAMP = Column("creation_timestamp")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val MASTER_MAP_ID = Column("master_map_id")
  val ALN_REFERENCE_MAP_ID = Column("aln_reference_map_id")
}

abstract class LcmsDbMapSetTable extends TableDefinition[LcmsDbMapSetColumns.type]

object LcmsDbMapSetTable extends LcmsDbMapSetTable {
  val name = "map_set"
  val columns = LcmsDbMapSetColumns
}

object LcmsDbMapSetObjectTreeMappingColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMapSetObjectTreeMappingTable.name
  val MAP_SET_ID = Column("map_set_id")
  val OBJECT_TREE_ID = Column("object_tree_id")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class LcmsDbMapSetObjectTreeMappingTable extends TableDefinition[LcmsDbMapSetObjectTreeMappingColumns.type]

object LcmsDbMapSetObjectTreeMappingTable extends LcmsDbMapSetObjectTreeMappingTable {
  val name = "map_set_object_tree_mapping"
  val columns = LcmsDbMapSetObjectTreeMappingColumns
}

object LcmsDbMasterFeatureItemColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMasterFeatureItemTable.name
  val MASTER_FEATURE_ID = Column("master_feature_id")
  val CHILD_FEATURE_ID = Column("child_feature_id")
  val IS_BEST_CHILD = Column("is_best_child")
  val MASTER_MAP_ID = Column("master_map_id")
}

abstract class LcmsDbMasterFeatureItemTable extends TableDefinition[LcmsDbMasterFeatureItemColumns.type]

object LcmsDbMasterFeatureItemTable extends LcmsDbMasterFeatureItemTable {
  val name = "master_feature_item"
  val columns = LcmsDbMasterFeatureItemColumns
}

object LcmsDbMsPictureColumns extends ColumnEnumeration {
  val $tableName = LcmsDbMsPictureTable.name
  val ID = Column("id")
  val Z_INDEX = Column("z_index")
  val MOZ_RESOLUTION = Column("moz_resolution")
  val TIME_RESOLUTION = Column("time_resolution")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val RUN_ID = Column("run_id")
}

abstract class LcmsDbMsPictureTable extends TableDefinition[LcmsDbMsPictureColumns.type]

object LcmsDbMsPictureTable extends LcmsDbMsPictureTable {
  val name = "ms_picture"
  val columns = LcmsDbMsPictureColumns
}

object LcmsDbObjectTreeColumns extends ColumnEnumeration {
  val $tableName = LcmsDbObjectTreeTable.name
  val ID = Column("id")
  val SERIALIZED_DATA = Column("serialized_data")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SCHEMA_NAME = Column("schema_name")
}

abstract class LcmsDbObjectTreeTable extends TableDefinition[LcmsDbObjectTreeColumns.type]

object LcmsDbObjectTreeTable extends LcmsDbObjectTreeTable {
  val name = "object_tree"
  val columns = LcmsDbObjectTreeColumns
}

object LcmsDbObjectTreeSchemaColumns extends ColumnEnumeration {
  val $tableName = LcmsDbObjectTreeSchemaTable.name
  val NAME = Column("name")
  val TYPE = Column("type")
  val VERSION = Column("version")
  val SCHEMA = Column("schema")
  val DESCRIPTION = Column("description")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbObjectTreeSchemaTable extends TableDefinition[LcmsDbObjectTreeSchemaColumns.type]

object LcmsDbObjectTreeSchemaTable extends LcmsDbObjectTreeSchemaTable {
  val name = "object_tree_schema"
  val columns = LcmsDbObjectTreeSchemaColumns
}

object LcmsDbPeakPickingSoftwareColumns extends ColumnEnumeration {
  val $tableName = LcmsDbPeakPickingSoftwareTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val VERSION = Column("version")
  val ALGORITHM = Column("algorithm")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbPeakPickingSoftwareTable extends TableDefinition[LcmsDbPeakPickingSoftwareColumns.type]

object LcmsDbPeakPickingSoftwareTable extends LcmsDbPeakPickingSoftwareTable {
  val name = "peak_picking_software"
  val columns = LcmsDbPeakPickingSoftwareColumns
}

object LcmsDbPeakelFittingModelColumns extends ColumnEnumeration {
  val $tableName = LcmsDbPeakelFittingModelTable.name
  val ID = Column("id")
  val NAME = Column("name")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbPeakelFittingModelTable extends TableDefinition[LcmsDbPeakelFittingModelColumns.type]

object LcmsDbPeakelFittingModelTable extends LcmsDbPeakelFittingModelTable {
  val name = "peakel_fitting_model"
  val columns = LcmsDbPeakelFittingModelColumns
}

object LcmsDbProcessedMapColumns extends ColumnEnumeration {
  val $tableName = LcmsDbProcessedMapTable.name
  val ID = Column("id")
  val NUMBER = Column("number")
  val NORMALIZATION_FACTOR = Column("normalization_factor")
  val IS_MASTER = Column("is_master")
  val IS_ALN_REFERENCE = Column("is_aln_reference")
  val IS_LOCKED = Column("is_locked")
  val MAP_SET_ID = Column("map_set_id")
}

abstract class LcmsDbProcessedMapTable extends TableDefinition[LcmsDbProcessedMapColumns.type]

object LcmsDbProcessedMapTable extends LcmsDbProcessedMapTable {
  val name = "processed_map"
  val columns = LcmsDbProcessedMapColumns
}

object LcmsDbProcessedMapFeatureItemColumns extends ColumnEnumeration {
  val $tableName = LcmsDbProcessedMapFeatureItemTable.name
  val PROCESSED_MAP_ID = Column("processed_map_id")
  val FEATURE_ID = Column("feature_id")
  val CALIBRATED_MOZ = Column("calibrated_moz")
  val NORMALIZED_INTENSITY = Column("normalized_intensity")
  val CORRECTED_ELUTION_TIME = Column("corrected_elution_time")
  val IS_CLUSTERIZED = Column("is_clusterized")
  val SELECTION_LEVEL = Column("selection_level")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbProcessedMapFeatureItemTable extends TableDefinition[LcmsDbProcessedMapFeatureItemColumns.type]

object LcmsDbProcessedMapFeatureItemTable extends LcmsDbProcessedMapFeatureItemTable {
  val name = "processed_map_feature_item"
  val columns = LcmsDbProcessedMapFeatureItemColumns
}

object LcmsDbProcessedMapMozCalibrationColumns extends ColumnEnumeration {
  val $tableName = LcmsDbProcessedMapMozCalibrationTable.name
  val PROCESSED_MAP_ID = Column("processed_map_id")
  val SCAN_ID = Column("scan_id")
  val MOZ_LIST = Column("moz_list")
  val DELTA_MOZ_LIST = Column("delta_moz_list")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
}

abstract class LcmsDbProcessedMapMozCalibrationTable extends TableDefinition[LcmsDbProcessedMapMozCalibrationColumns.type]

object LcmsDbProcessedMapMozCalibrationTable extends LcmsDbProcessedMapMozCalibrationTable {
  val name = "processed_map_moz_calibration"
  val columns = LcmsDbProcessedMapMozCalibrationColumns
}

object LcmsDbProcessedMapRawMapMappingColumns extends ColumnEnumeration {
  val $tableName = LcmsDbProcessedMapRawMapMappingTable.name
  val PROCESSED_MAP_ID = Column("processed_map_id")
  val RAW_MAP_ID = Column("raw_map_id")
}

abstract class LcmsDbProcessedMapRawMapMappingTable extends TableDefinition[LcmsDbProcessedMapRawMapMappingColumns.type]

object LcmsDbProcessedMapRawMapMappingTable extends LcmsDbProcessedMapRawMapMappingTable {
  val name = "processed_map_raw_map_mapping"
  val columns = LcmsDbProcessedMapRawMapMappingColumns
}

object LcmsDbRawMapColumns extends ColumnEnumeration {
  val $tableName = LcmsDbRawMapTable.name
  val ID = Column("id")
  val SCAN_SEQUENCE_ID = Column("scan_sequence_id")
  val PEAK_PICKING_SOFTWARE_ID = Column("peak_picking_software_id")
  val PEAKEL_FITTING_MODEL_ID = Column("peakel_fitting_model_id")
}

abstract class LcmsDbRawMapTable extends TableDefinition[LcmsDbRawMapColumns.type]

object LcmsDbRawMapTable extends LcmsDbRawMapTable {
  val name = "raw_map"
  val columns = LcmsDbRawMapColumns
}

object LcmsDbScanColumns extends ColumnEnumeration {
  val $tableName = LcmsDbScanTable.name
  val ID = Column("id")
  val INITIAL_ID = Column("initial_id")
  val CYCLE = Column("cycle")
  val TIME = Column("time")
  val MS_LEVEL = Column("ms_level")
  val TIC = Column("tic")
  val BASE_PEAK_MOZ = Column("base_peak_moz")
  val BASE_PEAK_INTENSITY = Column("base_peak_intensity")
  val PRECURSOR_MOZ = Column("precursor_moz")
  val PRECURSOR_CHARGE = Column("precursor_charge")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val SCAN_SEQUENCE_ID = Column("scan_sequence_id")
}

abstract class LcmsDbScanTable extends TableDefinition[LcmsDbScanColumns.type]

object LcmsDbScanTable extends LcmsDbScanTable {
  val name = "scan"
  val columns = LcmsDbScanColumns
}

object LcmsDbScanSequenceColumns extends ColumnEnumeration {
  val $tableName = LcmsDbScanSequenceTable.name
  val ID = Column("id")
  val RAW_FILE_NAME = Column("raw_file_name")
  val MIN_INTENSITY = Column("min_intensity")
  val MAX_INTENSITY = Column("max_intensity")
  val MS1_SCAN_COUNT = Column("ms1_scan_count")
  val MS2_SCAN_COUNT = Column("ms2_scan_count")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val INSTRUMENT_ID = Column("instrument_id")
}

abstract class LcmsDbScanSequenceTable extends TableDefinition[LcmsDbScanSequenceColumns.type]

object LcmsDbScanSequenceTable extends LcmsDbScanSequenceTable {
  val name = "scan_sequence"
  val columns = LcmsDbScanSequenceColumns
}

object LcmsDbTheoreticalFeatureColumns extends ColumnEnumeration {
  val $tableName = LcmsDbTheoreticalFeatureTable.name
  val ID = Column("id")
  val MOZ = Column("moz")
  val CHARGE = Column("charge")
  val ELUTION_TIME = Column("elution_time")
  val SOURCE_TYPE = Column("source_type")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val MAP_LAYER_ID = Column("map_layer_id")
  val MAP_ID = Column("map_id")
}

abstract class LcmsDbTheoreticalFeatureTable extends TableDefinition[LcmsDbTheoreticalFeatureColumns.type]

object LcmsDbTheoreticalFeatureTable extends LcmsDbTheoreticalFeatureTable {
  val name = "theoretical_feature"
  val columns = LcmsDbTheoreticalFeatureColumns
}

object LcmsDbTileColumns extends ColumnEnumeration {
  val $tableName = LcmsDbTileTable.name
  val ID = Column("id")
  val X_INDEX = Column("x_index")
  val Y_INDEX = Column("y_index")
  val BEGIN_MOZ = Column("begin_moz")
  val END_MOZ = Column("end_moz")
  val BEGIN_TIME = Column("begin_time")
  val END_TIME = Column("end_time")
  val WIDTH = Column("width")
  val HEIGHT = Column("height")
  val INTENSITIES = Column("intensities")
  val SERIALIZED_PROPERTIES = Column("serialized_properties")
  val MS_PICTURE_ID = Column("ms_picture_id")
}

abstract class LcmsDbTileTable extends TableDefinition[LcmsDbTileColumns.type]

object LcmsDbTileTable extends LcmsDbTileTable {
  val name = "tile"
  val columns = LcmsDbTileColumns
}

