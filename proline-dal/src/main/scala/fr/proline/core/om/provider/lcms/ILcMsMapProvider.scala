package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ILcMsMap

trait ILcMsMapProvider {
  
  def getLcMsMaps( mapIds: collection.Seq[Long], loadPeakels: Boolean ): collection.Seq[ILcMsMap]
  def getFeatures( mapIds: collection.Seq[Long], loadPeakels: Boolean ): Array[Feature]

  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( rawMapIds: collection.Seq[Long] ): Map[Long,Array[Long]]
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById(
    mapIds: collection.Seq[Long],
    scanInitialIdById: Map[Long,Int],
    ms2EventIdsByFtId: Map[Long,Array[Long]] 
  ): Map[Long,Feature]

}