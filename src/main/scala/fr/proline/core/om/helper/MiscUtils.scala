package fr.proline.core.om.helper

package MiscUtils {

  object getMedianObject{
    def apply[T]( objects: List[T], sortingFunc: Function2[T,T,Boolean] ): T = {
    
      val sortedObjects = objects.sort { (a,b) => sortingFunc(a,b) } 
      val nbObjects = sortedObjects.length
      
      // Compute median index
      var medianIndex = 0
      
      // If even number of objects
      if( nbObjects % 2 == 0 ) medianIndex = nbObjects / 2
      // Else => odd number of objects
      else medianIndex = (nbObjects-1) / 2
      
      sortedObjects(medianIndex)
      
    }
  }
  
  /*object factorizeSeq {
    def apply (seq : Seq[_]) : Array[Pair[_, _]] = {
      val p = new scala.collection.mutable.ArrayBuffer[Pair[_, _]]()
      for (e1 <- seq) for (e2 <- seq) p += Pair(e1, e2)
      p.toArray
    }
  }*/
  
  object combinations {
 
    def apply[A](n: Int, ls: List[A]): List[List[A]] =
      if (n == 0) List(Nil)
      else flatMapSublists(ls) { sl =>
        apply(n - 1, sl.tail) map {sl.head :: _}
      }
    
    // flatMapSublists is like list.flatMap, but instead of passing each element
    // to the function, it passes successive sublists of L.
    private def flatMapSublists[A,B](ls: List[A])(f: (List[A]) => List[B]): List[B] = 
      ls match {
        case Nil => Nil
        case sublist@(_ :: tail) => f(sublist) ::: flatMapSublists(tail)(f)
      }
  
  }

  
}