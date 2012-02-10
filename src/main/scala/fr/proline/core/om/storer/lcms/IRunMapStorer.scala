package fr.proline.core.om.storer.lcms

trait IRunMapStorer {
  
  import fr.proline.core.om.lcms.MapClasses._
  import fr.proline.core.om.lcms.FeatureClasses.Feature
  
  def storeRunMap( runMap: RunMap ): Unit
  def storeMap( lcmsMap: LcmsMap, modificationTimestamp: java.util.Date ): Int
  
 }