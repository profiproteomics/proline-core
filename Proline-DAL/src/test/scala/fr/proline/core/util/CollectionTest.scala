package fr.proline.core.util

import org.junit.Assert._
import org.junit.Test

import fr.proline.core.util.collection._

@Test
class CollectionTest {
  
  val entities = Array((2L,"entity 2"), (1L, "entity 1"))
  
  @Test
  def testSortByLongSeq() {
    
    val ids = Array(1L,2L,3L)
    val sortedEntities = entities.sortByLongSeq(ids, _._1, nullIfAbsent = false)
    
    assertEquals("should get 2 entities", 2, sortedEntities.length)
    assertTrue("entities should be sorted", sortedEntities.head._1 < sortedEntities.last._1)
  }
  
  @Test
  def testSortByLongSeqDistinctIds() {
    
    val distinctIds = Array(1L,2L,2L).distinct
    val sortedEntities = entities.sortByLongSeq(distinctIds, _._1, nullIfAbsent = false)
    
    assertEquals("should get 2 entities", 2, sortedEntities.length)
    assertTrue("entities should be sorted", sortedEntities.head._1 < sortedEntities.last._1)
  }
  
  @Test
  def testSortByLongSeqWithNulls() {
    val ids = Array(1L,2L,3L)
    val sortedEntities = entities.sortByLongSeq(ids, _._1, nullIfAbsent = true)
    
    assertEquals("should get 3 entities", 3, sortedEntities.length)
    assertTrue("entities should be sorted", sortedEntities.head._1 < sortedEntities(1)._1)
    assertEquals("las entity should be null", null, sortedEntities.last)
  }

}
