package fr.proline.core.algo.msi.inference

import org.scalatest.Spec
import org.scalatest.Assertions
import org.scalatest.GivenWhenThen
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.junit.JUnitSuite

@RunWith(classOf[JUnitRunner])
class SetClustererTest extends Spec with GivenWhenThen {

  describe("a map containing two same sets of integers ") {
    
    val inputSet = Set(1,2,3,4)
    val setsById = Map( 1 -> inputSet, 
                        2 -> inputSet )

    val clusters = SetClusterer.clusterizeMappedSets[Int,Int]( setsById )
    
    it("should be grouped in the same cluster") {
      assert( clusters.length === 1 )
    }
    
    val cluster = clusters(0)
    
    it("should yield to a cluster with values identical to the input") {
      assert( cluster.samesetsValues === inputSet )
    }
    
    it("should yield to a cluster which is not a subset") {
    assert( cluster.isSubset === false )
    }
  }

  describe("a map containing an overset and a strict subset of integers") {
    
    val setsById = Map( 1 -> Set(1,2,3,4,5), 
                        2 -> Set(1,2,3,4) )
                        
    val clusters = SetClusterer.clusterizeMappedSets[Int,Int]( setsById )
    it("should be retrieved as two separated clusters") {
      assert( clusters.length === 2 )
    }
    
    it("should yield to an overset") {
         
      given("this overset")
      val overset = clusters.filter( _.samesetsValues == setsById(1) )(0)
      assert( overset.isSubset === false )
      
      then("it should have strict subsets ids")
      assert( overset.strictSubsetsIds != None )
      
    }
    
    it("should yield to a strict subset") {
      val subset = clusters.filter( _.samesetsValues == setsById(2) )(0)
      assert( subset.isSubset === true )
    }

  }
  
  describe("a map containing 2 different sets of integers and a third one being a subsumable subset" ) {
    
    val setsById = Map( 1 -> Set(1,2,3), 
                        2 -> Set(4,5,6),
                        3 -> Set(3,6) )
                        
    val clusters = SetClusterer.clusterizeMappedSets[Int,Int]( setsById )
    it("should be retrieved three separated clusters") {
      assert( clusters.length === 3 )
    }
    
    
    it("should yield to 2 non strict oversets") {
         
      given("these 2 oversets")
      val oversets = clusters.filter( _.isSubset == false )
      assert( oversets.length === 2 )
      
      then("each overset doesn't have strict subset ids")
      for( overset <- oversets ) {
        assert( overset.strictSubsetsIds == None )
      }
      
      and("have a subsumable subset ids")
      for( overset <- oversets ) {
        assert( overset.subsumableSubsetsIds != None )
      }
      
    }
    
    it("should yield to a subsumable subset") {
      val subsumableSubsets = clusters.filter( _.isSubset == true )
      assert( subsumableSubsets.length === 1 )
    }

  }

  describe("a map containing 3 different sets of integers and a forth one being a subsumable subset" ) {
    
    val setsById = Map( 1 -> Set(5,6,7),
                        2 -> Set(1,2,3),
                        3 -> Set(3,4,5), 
                        4 -> Set(1,3,6))
                        
    val clusters = SetClusterer.clusterizeMappedSets[Int,Int]( setsById )
    it("should be retrieved 4 separated clusters") {
      assert( clusters.length === 4 )
    }
    
    
    it("should yield to 3 non strict oversets") {
         
      given("these 3 oversets")
      val oversets = clusters.filter( _.isSubset == false )
      assert( oversets.length === 3 )
      
      then("each overset doesn't have strict subset ids")
      for( overset <- oversets ) {
        assert( overset.strictSubsetsIds == None )
      }
      
      and("have a subsumable subset ids")
      for( overset <- oversets ) {
        assert( overset.subsumableSubsetsIds != None )
      }
      
    }
    
    it("should yield to a subsumable subset") {
      val subsumableSubsets = clusters.filter( _.isSubset == true )
      assert( subsumableSubsets.length === 1 )
    }

  }

}
