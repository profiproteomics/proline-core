package fr.proline.core.util

object CoreConfig {
  
  private var _mzdbMaxParallelism = 2

  // Getter  
  def mzdbMaxParallelism = _mzdbMaxParallelism 
   
  // Setter 
 def mzdbMaxParallelism_= (newXicFilesPoolSize:Int):Unit = _mzdbMaxParallelism = newXicFilesPoolSize 
  
}