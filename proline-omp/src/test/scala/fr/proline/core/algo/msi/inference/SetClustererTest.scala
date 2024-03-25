package fr.proline.core.algo.msi.inference

import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

@Test
class SetClustererTest extends JUnitSuite {

  @Test
  def OneSets() : Unit = {

    val inputSet = Set(1, 2, 3, 4)
    val setsById = Map(1 -> inputSet,
   		 				  2 -> inputSet)

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)
    assertEquals(1, clusters.length)

    val cluster = clusters(0)

    assertEquals(inputSet, cluster.samesetsValues)
    assert(!cluster.isSubset)
    assert(cluster.oversetId.isEmpty)
  }

  @Test
  def OneSetOneStrictSubSet(): Unit = {

    val setsById = Map(1 -> Set(1, 2, 3, 4, 5),
                       2 -> Set(1, 2, 3, 4))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)
    assertEquals(2, clusters.length)

    val overset = clusters.filter(_.samesetsValues == setsById(1))(0)
    assert(!overset.isSubset)
    assert(overset.strictSubsetsIds.isDefined)

    val subset = clusters.filter(_.samesetsValues == setsById(2))(0)
    assert(subset.isSubset === true)
    assert(subset.oversetId.get == overset.id)
  }

  @Test
  def TwoSetsOneSubsumable(): Unit = {

    val setsById = Map(1 -> Set(1, 2, 3),
                       2 -> Set(4, 5, 6),
                       3 -> Set(3, 6))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)
    assertEquals(3, clusters.length)

    val oversets = clusters.filter(_.isSubset == false)
    assertEquals(2, oversets.length)

    for (overset <- oversets) {
      assert(overset.strictSubsetsIds.isEmpty)
    }
    for (overset <- oversets) {
      assert(overset.subsumableSubsetsIds.isDefined)
    }

    val subsumableSubsets = clusters.filter(_.isSubset == true)
    assertEquals(1, subsumableSubsets.length)

  }

  @Test
  def ThreeSetsOneSubsumable(): Unit = {

    val setsById = Map(1 -> Set(5, 6, 7),
                       2 -> Set(1, 2, 3),
                       3 -> Set(3, 4, 5),
                       4 -> Set(1, 3, 6))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)
    assertEquals(4, clusters.length)

    val oversets = clusters.filter(_.isSubset == false)
    assertEquals(3, oversets.length)

    for (overset <- oversets) {
      assert(overset.strictSubsetsIds.isEmpty)
    }

    for (overset <- oversets) {
      assert(overset.subsumableSubsetsIds.isDefined)
    }

    val subsumableSubsets = clusters.filter(_.isSubset == true)
    assertEquals(1, subsumableSubsets.length)

  }
  
    @Test
  def ThreeSetsOneSubsumable2(): Unit = {

    val setsById = Map(1 -> Set(1,2),
                       2 -> Set(3, 4),
                       3 -> Set(5, 6),
                       4 -> Set(1, 3, 5))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)
    assertEquals(4, clusters.length)

    val oversets = clusters.filter(_.isSubset == false)
    assertEquals(3, oversets.length)

    for (overset <- oversets) {
      assert(overset.strictSubsetsIds.isEmpty)
    }

    for (overset <- oversets) {
      assert(overset.subsumableSubsetsIds.isDefined)
    }

    val subsumableSubsets = clusters.filter(_.isSubset == true)
    assertEquals(1, subsumableSubsets.length)
    assert(!subsumableSubsets(0).oversetId.isDefined)
  }

  @Test
  def ThreeSubsumable(): Unit = {

    val setsById = Map(1 -> Set(1, 2),
                       2 -> Set(2, 3),
                       3 -> Set(3, 1))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)

    //    printClusters(clusters)

    assertEquals(3, clusters.length)

    val subsumableSubsets = clusters.filter(_.isSubset == true)
    assertEquals(0, subsumableSubsets.length)

  }

    @Test
  def SixSubsumable() : Unit= {

    val setsById = Map(1 -> Set(1, 2),
                       2 -> Set(2, 3),
                       3 -> Set(3, 4),
                       4 -> Set(4, 5),
                       5 -> Set(5, 6),
                       6 -> Set(6, 1))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)

    //    printClusters(clusters)

    assertEquals(6, clusters.length)

    val subsumableSubsets = clusters.filter(_.isSubset == true)
    assertEquals(0, subsumableSubsets.length)

  }

  @Test
  def Subsumables() : Unit = {

    val setsById = Map(1 -> Set(1, 2),
                       2 -> Set(2, 3),
                       3 -> Set(3, 4),
                       4 -> Set(4, 5))

    val clusters = SetClusterer.clusterizeMappedSets[Int, Int](setsById)
    assertEquals(4, clusters.length)

    printClusters(clusters)
    
    val oversets = clusters.filter(_.isSubset == false)
    assertEquals(4, oversets.length)

    for (overset <- oversets) {
      assert(overset.strictSubsetsIds.isEmpty)
    }
    
    val subsumableSubsets = clusters.filter(_.isSubset == true)
     assertEquals(0, subsumableSubsets.length)

  }
  
  
  def printClusters(clusters: Array[SetCluster[Int, Int]]) = {
    for (cluster <- clusters) {
      println(cluster.id + " = [" + cluster.samesetsKeys.mkString(",") + "] -> (" + cluster.samesetsValues.mkString(",") + ") sub=" + cluster.isSubset)
    }
  }
}
