package fr.proline.core.om.helper

/** Miscellaneous helpers */
package object MiscUtils {

  trait InMemoryIdGen {
    private var inMemoryIdCount = 0
    def generateNewId(): Int = { inMemoryIdCount -= 1; inMemoryIdCount }
  }
  
  /** Computes the median value of a sequence of Doubles */
  /*def median(s: Seq[Double]) = {
    val (lower, upper) = s.sortWith(_<_).splitAt(s.size / 2)
    if (s.size % 2 == 0) (lower.last + upper.head) / 2.0 else upper.head
  }*/
  
  def median[T](s: Seq[T])(implicit n: Fractional[T]) = {
    import n._
    val (lower, upper) = s.sortWith(_<_).splitAt(s.size / 2)
    if (s.size % 2 == 0) (lower.last + upper.head) / fromInt(2) else upper.head
  }
  
  def getMedianObject[T]( objects: List[T], sortingFunc: Function2[T,T,Boolean] ): T = {
  
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
  
  /** Compute slope and intercept of a line using two data points coordinates */
  def calcLineParams( x1: Double, y1: Double, x2: Double, y2: Double ): Tuple2[Double,Double] =  {
    
    val deltaX = x2 - x1
    if( deltaX == 0 )  {
      throw new IllegalArgumentException("can't solve line parameters with two identical x values (" + x1 + ")" )
    }
    
    val slope = (y2 - y1) / deltaX
    val intercept = y1 - (slope * x1)
    
    ( slope, intercept )
  }

  
}