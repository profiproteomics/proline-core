package fr.proline.core.om.provider.msi.cache

import java.util.concurrent.TimeUnit
import com.github.benmanes.caffeine.cache._
import com.typesafe.scalalogging.LazyLogging
import fr.proline.core.om.model.msi.Peptide

object PeptideCacheConstant {
  val GIGA: Long = 1024 * 1024 * 1024L

  /* Max distinct Peptides per ResultSet estimated : 137 145 */
  val INITIAL_CACHE_SIZE = 200000
  val CACHE_SIZE_INCREMENT = 100000
  /* Max distinct Peptides per MSI / Project estimated : 3 129 096 */
  val MAXIMUM_CACHE_SIZE = 4000000
}

//// Old implementation of LMN
//trait LruCache[A,B] {
//
//  protected def calculateCacheSize(): Int
//  protected def INITIAL_CAPACITY: Int
//  protected def LOAD_FACTOR: Float
//
//  private val CACHE_SIZE = calculateCacheSize()
//
//  private val _cacheLock = new Object()
//
//  /* Use a Java LinkedHashMap configured as LRU cache (with access-order) ; @GuardedBy("_cacheLock") */
//  private val _lruCache = new java.util.LinkedHashMap[A, B](INITIAL_CAPACITY, LOAD_FACTOR, true) {
//    override def removeEldestEntry(eldest: java.util.Map.Entry[A, B]): Boolean = {
//      size > CACHE_SIZE
//    }
//  }
//
//  def getOrElse(key: A)(fn: => B): B = {
//    get(key).getOrElse {
//      val result = fn
//      put(key, result)
//      result
//    }
//  }
//
//  def get(key: A): Option[B] = _cacheLock.synchronized {
//    Option(_lruCache.get(key))
//  }
//
//  def put(key: A, value: B): B = _cacheLock.synchronized {
//    _lruCache.put(key, value)
//  }
//
//  def remove(key: A): B = _cacheLock.synchronized {
//    _lruCache.remove(key)
//  }
//
//  /**
//   * Clear (purge all entries) this static cache.
//   */
//  def clear() {
//    _cacheLock.synchronized {
//      _lruCache.clear()
//    }
//  }
//
//}
//
//class OldPeptideCache extends LruCache[Long,Peptide] with LazyLogging {
//
//  import PeptideCacheConstant._
//
//  // Create a static HashMap to cache loaded peptides
//  // TODO: use cache for other queries than getPeptides(peptideIds)
//  // TODO: implement cache at context level
//
//  protected val INITIAL_CAPACITY = 16 // Default Java Map  initial capacity and load factor
//  protected val LOAD_FACTOR = 0.75f
//
//  /**
//   * Clear (purge all entries) this static cache.
//   */
//  override def clear() {
//    super.clear()
//
//    logger.info("PeptideCache cache cleared")
//  }
//
//  /* Private methods */
//  protected def calculateCacheSize(): Int = {
//    val maxMemory = Runtime.getRuntime.maxMemory
//
//    val cacheSize = if (maxMemory > (4 * GIGA)) {
//      /* Big cacheSize = 200000 + (100000 for each GiB over 4 GiB) */
//      val extendedMemory = maxMemory - 4 * GIGA
//
//      val nBlocks = (extendedMemory + GIGA - 1) / GIGA // rounding up
//
//      val bigCacheSize = (INITIAL_CACHE_SIZE + nBlocks * CACHE_SIZE_INCREMENT).asInstanceOf[Int]
//
//      logger.trace("MaxMemory: " + maxMemory + "  NBlocks over 4 Gib : " + nBlocks + "  bigCacheSize: " + bigCacheSize)
//
//      bigCacheSize.min(MAXIMUM_CACHE_SIZE)
//    } else {
//      INITIAL_CACHE_SIZE
//    }
//
//    logger.info("PeptideCache size: " + cacheSize)
//
//    cacheSize
//  }
//
//}


trait KVContainer[A, B]  {
  
  def getOrElseUpdate(key: A)(fn: => B): B

  def get(key: A): Option[B]

  def put(key: A, value: B): Unit

  def remove(key: A): Unit

  def clear(): Unit
  
  def size(): Long
}

/*
See :
- http://www.blozinek.cz/2015/06/finding-best-in-memory-lru-cache-for.html
- https://github.com/ben-manes/caffeine
- https://gist.github.com/jeffreyolchovy/3278505
*/
trait CaffeineCacheOps[A <: Object, B <: Object] extends KVContainer[A, B] {
  
  protected def _caffeineCache: Cache[A, B]
  
  def getOrElseUpdate(key: A)(fn: => B): B = { 
    val valueBuilder = new java.util.function.Function[A,B]() {
      def apply(k: A): B = fn
    }
    _caffeineCache.get(key, valueBuilder )
  }

  def get(key: A): Option[B] = Option(_caffeineCache.getIfPresent(key))

  def put(key: A, value: B): Unit = _caffeineCache.put(key, value)

  def remove(key: A): Unit = _caffeineCache.invalidate(key)

  def clear(): Unit = _caffeineCache.invalidateAll()
  
  def size(): Long = _caffeineCache.estimatedSize()
  
}

trait CaffeineCacheWrapper[A <: Object, B <: Object] extends CaffeineCacheOps[A, B] {
  
  protected def calculateCacheSize(): Int
  protected def INITIAL_CAPACITY: Int
  
  def getCacheSize(): Int = CACHE_SIZE
  private val CACHE_SIZE = calculateCacheSize()
  
  protected val _caffeineCache: Cache[A, B] = Caffeine.newBuilder().
    initialCapacity(INITIAL_CAPACITY).
    maximumSize(calculateCacheSize()).
    expireAfterAccess(1, TimeUnit.HOURS).
    build()

}


trait IPeptideCache extends CaffeineCacheWrapper[java.lang.Long,Peptide] {

//  def get(key: Long): Option[Peptide]
//
//  def put(key: Long, value: Peptide): Unit
//
//  def remove(key: Long): Unit
//
//  def clear(): Unit
//
//  def size(): Long
//
//  def getCacheSize(): Int
}

class PeptideCache extends  IPeptideCache with LazyLogging {
  
  import PeptideCacheConstant._

  // TODO: use cache for other queries than getPeptides(peptideIds)
  // TODO: implement cache at context level ?
  
  protected val INITIAL_CAPACITY = 10000

  /**
   * Clear (purge all entries) this static cache.
   */
  override def clear() {
    super.clear()

    logger.info("PeptideCache cache cleared")
  }

  /* Private methods */
  protected def calculateCacheSize(): Int = {
    val maxMemory = Runtime.getRuntime.maxMemory

    val cacheSize = if (maxMemory > (4 * GIGA)) {
      /* Big cacheSize = 200000 + (100000 for each GiB over 4 GiB) */
      val extendedMemory = maxMemory - 4 * GIGA

      val nBlocks = (extendedMemory + GIGA - 1) / GIGA // rounding up

      val bigCacheSize = (INITIAL_CACHE_SIZE + nBlocks * CACHE_SIZE_INCREMENT).asInstanceOf[Int]

      logger.trace("MaxMemory: " + maxMemory + " NBlocks over 4 Gib: " + nBlocks + " bigCacheSize: " + bigCacheSize)

      bigCacheSize.min(MAXIMUM_CACHE_SIZE)
    } else {
      INITIAL_CACHE_SIZE
    }

    logger.info("PeptideCache size: " + cacheSize)

    cacheSize
  }

}



/**
 * A registry of PeptideCaffeineCache which are
 */
object PeptideCacheRegistry extends CaffeineCacheOps[java.lang.Long, IPeptideCache] {
  
  val INITIAL_CAPACITY = 1000
  
  private val cacheBuilder = new CacheLoader[java.lang.Long, IPeptideCache]() {
    def load(projectId: java.lang.Long): PeptideCache = new PeptideCache()
  }
 
  protected val _caffeineCache: LoadingCache[java.lang.Long, IPeptideCache] = Caffeine.newBuilder().
    initialCapacity(INITIAL_CAPACITY).
    expireAfterAccess(24, TimeUnit.HOURS).
    build(cacheBuilder)
    
  def getOrCreate(projectId: java.lang.Long): IPeptideCache = _caffeineCache.get(projectId)
}