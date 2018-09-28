package fr.proline.core.util

import scala.collection.mutable.ArrayBuffer
import scala.collection.TraversableLike

import fr.profi.util.collection._

/**
 * @author David Bouyssie
 *
 */
package object collection {
  
  private object TraversableLikeSorter {
    @inline final def sortByLongSeq[A, Repr](
      xs: TraversableLike[A, Repr],
      withLong: A => Long,
      longSeq: Seq[Long],
      nullIfAbsent: Boolean = false
    ): Seq[A] = {

      val distinctLongs = longSeq.distinct
      val indexByLong = distinctLongs.zipWithIndex.toLongMap
      
      val nullA = null.asInstanceOf[A]
      val sortedXS = ArrayBuffer.fill[A](distinctLongs.length)(nullA)
      
      for (x <- xs) {
        sortedXS(indexByLong(withLong(x))) = x
      }
  
      if (nullIfAbsent) sortedXS else sortedXS.filter(_ != null)
    }
  }

  class TraversableLikeLongSeqSorter[A, Repr](val xs: TraversableLike[A, Repr]) extends AnyVal {
    def sortByLongSeq(longSeq: Seq[Long], withLong: A => Long, nullIfAbsent: Boolean = false): Seq[A] = {
      TraversableLikeSorter.sortByLongSeq(xs, withLong, longSeq, nullIfAbsent)
    }
  }
  implicit def traversableOnce2longSeqSorter[A,Repr]( xs: TraversableLike[A, Repr] ): TraversableLikeLongSeqSorter[A, Repr] = {
    new TraversableLikeLongSeqSorter[A,Repr](xs)
  }
  implicit def array2longSeqSorter[A]( xs: Array[A] ): TraversableLikeLongSeqSorter[A, Array[A]] = {
    new TraversableLikeLongSeqSorter[A, Array[A]](xs)
  }
  
}