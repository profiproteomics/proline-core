package fr.proline.core.om.storer.lcms

trait IRunMapStorer {
  
  import fr.proline.core.om.lcms.LcmsMap
  import fr.proline.core.om.lcms.RunMap
  
  def storeRunMap( runMap: RunMap, storePeaks: Boolean = false ): Unit
  def insertMap( lcmsMap: LcmsMap, modificationTimestamp: java.util.Date ): Int
  
 }