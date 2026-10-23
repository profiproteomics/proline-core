package fr.proline.core.util

import scala.collection.mutable.ArrayBuffer
import scala.collection.Seq
import fr.profi.util.collection._

import scala.language.implicitConversions

/**
 * @author David Bouyssie
 *
 */
package object collection {
  
  private object TraversableLikeSorter {
    @inline final def sortByLongSeq[A](
      xs: IterableOnce[A],
      withLong: A => Long,
      longSeq: Seq[Long],
      nullIfAbsent: Boolean = false
    ): Seq[A] = {

      val distinctLongs = longSeq.distinct
      val indexByLong = distinctLongs.zipWithIndex.toLongMap
      
      val nullA = null.asInstanceOf[A]
      val sortedXS = ArrayBuffer.fill[A](distinctLongs.length)(nullA)
      
      for (x <- xs.iterator) {
        sortedXS(indexByLong(withLong(x))) = x
      }
  
      if (nullIfAbsent) sortedXS else sortedXS.filter(_ != null)
    }
  }

  class TraversableLikeLongSeqSorter[A](val xs: IterableOnce[A]) extends AnyVal {
    def sortByLongSeq(longSeq: Seq[Long], withLong: A => Long, nullIfAbsent: Boolean = false): Seq[A] = {
      TraversableLikeSorter.sortByLongSeq(xs, withLong, longSeq, nullIfAbsent)
    }
  }
  implicit def iterableOnce2longSeqSorter[A]( xs: IterableOnce[A] ): TraversableLikeLongSeqSorter[A] = {
    new TraversableLikeLongSeqSorter[A](xs)
  }
  implicit def array2longSeqSorter[A]( xs: Array[A] ): TraversableLikeLongSeqSorter[A] = {
    new TraversableLikeLongSeqSorter[A](xs)
  }
  
}