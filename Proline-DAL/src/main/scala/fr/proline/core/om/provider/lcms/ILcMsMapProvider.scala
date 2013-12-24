package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ILcMsMap

trait ILcMsMapProvider {
  
  def getLcMsMaps( mapIds: Seq[Long] ): Seq[ILcMsMap]
  def getFeatures( mapIds: Seq[Long] ): Array[Feature]

  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( rawMapIds: Seq[Long] ): Map[Long,Array[Long]]
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById( mapIds: Seq[Long],
                                 scanInitialIdById: Map[Long,Int],
                                 ms2EventIdsByFtId: Map[Long,Array[Long]] 
                               ): Map[Long,Feature]

}