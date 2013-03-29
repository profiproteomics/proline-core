package fr.proline.core.om.provider.lcms

import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.lcms.ILcMsMap

trait ILcMsMapProvider {
  
  def getLcMsMaps( mapIds: Seq[Int] ): Seq[ILcMsMap]
  def getFeatures( mapIds: Seq[Int] ): Array[Feature]

  /** Returns a map of overlapping feature ids keyed by feature id */
  def getOverlappingFtIdsByFtId( runMapIds: Seq[Int] ): Map[Int,Array[Int]]
  
  /** Returns a map of overlapping feature keyed by its id */
  def getOverlappingFeatureById( mapIds: Seq[Int],
                                 scanInitialIdById: Map[Int,Int],
                                 ms2EventIdsByFtId: Map[Int,Array[Int]] 
                               ): Map[Int,Feature]

}