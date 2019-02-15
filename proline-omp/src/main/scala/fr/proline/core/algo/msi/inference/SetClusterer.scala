package fr.proline.core.algo.msi.inference

import collection.mutable.ArrayBuffer
import collection.mutable.HashSet
import util.control.Breaks._

case class SetCluster[K,V](
  id: Long,
  samesetsValues: Set[V],
  samesetsKeys: ArrayBuffer[K] = new ArrayBuffer[K](1),
  subsetsKeys: HashSet[K] = new HashSet[K](),
  var isSubset: Boolean,
  var strictSubsetsIds: Option[ArrayBuffer[Long]] = None,
  var subsumableSubsetsIds: Option[ArrayBuffer[Long]] = None, 
  var oversetId: Option[Long] = None
)

object SetClusterer {
  
  def clusterizeMappedSets[K,V]( valuesByKey: Map[K,Set[V]] ): Array[SetCluster[K,V]] = {
    
    // Reverse the map
    val keysByValueBuilder = new collection.mutable.HashMap[V,ArrayBuffer[K]]()
    
    for( ( key, values) <- valuesByKey ) {
      require( values != null, "undefined values for key '"+ key +"'")
      
      // Iterate over values to fill the reversed map
      for( value <- values ) {        
        // Add new array buffer if value is not already in the map
        // And add key to the array buffer
        keysByValueBuilder.getOrElseUpdate(value,new ArrayBuffer[K](1) ) += key
      }
    }
    
    val keysByValue = keysByValueBuilder.toMap
    
    // Search sets of values which share at least one value
    val keySets = new ArrayBuffer[List[K]]
    val assignedKeys = new collection.mutable.HashSet[K]
    
    for( ( key, values) <- valuesByKey ) {
      if( ! assignedKeys.contains(key) ) {
        val keySet = new collection.mutable.HashSet[K]
        this.getAllKeysHavingRelatedValues[K,V](
          valuesByKey,
          keysByValue,
          values,
          new collection.mutable.HashSet[V],
          keySet
        )
        
        if( keySet.isEmpty == false ) {
          keySets += keySet.toList
          assignedKeys ++= keySet
        }
      }
    }
    
    // Compute clusters of keys inside each key set
    val setClusters = new ArrayBuffer[SetCluster[K,V]]
    var clusterIdCount = 0L
    for( keySet <- keySets ) {
      
      // Group keys which have the same set of values
      val samesetMap = new collection.mutable.HashMap[String,SetCluster[K,V]]
      val samesetCountByValue = new collection.mutable.HashMap[V,Int]
      
      for( key <- keySet ) {
        
        val values = valuesByKey(key)
        val valuesAsStr = stringifySortedList(values.toList)
        
        if( ! samesetMap.contains(valuesAsStr) ) {
          clusterIdCount += 1L
          samesetMap += valuesAsStr -> SetCluster( id = clusterIdCount, isSubset = false, samesetsValues = values ) 
          
          // Update values counters
          for( value <- values ) {
            samesetCountByValue.getOrElseUpdate(value, 0)
            samesetCountByValue(value) += 1
          }
        }
        
        val sameset = samesetMap( valuesAsStr )
        sameset.samesetsKeys += key
      }
      
      // Retrieve samesets
      val samesets = samesetMap.values
      
      // Split samesets based on the specificity of their values
      val unspecificSamesets = new ArrayBuffer[SetCluster[K,V]](0)
      val samesetByIdPairs = new ArrayBuffer[(Long,SetCluster[K,V])](samesets.size)
      val oversetIdsByValue = new collection.mutable.HashMap[V,ArrayBuffer[Long]]()
     
      for( sameset <- samesets ) {
        
        val samesetId = sameset.id
        samesetByIdPairs += (samesetId -> sameset )
        
        val values = sameset.samesetsValues
        var hasSpecificValue = false
        
        // Define the block as breakable
        breakable {
          for( value <- values ) {            
            // Check if this value is specific to this sameset
            if( samesetCountByValue(value) == 1 ) {
              hasSpecificValue = true
              break
            }
          }
        }
        
        if( ! hasSpecificValue ) { unspecificSamesets += sameset }
        else {
          for( value <- values ) {
            oversetIdsByValue.getOrElseUpdate( value, new ArrayBuffer[Long](1) ) += samesetId
          }
        }
      }
      
      val samesetById = collection.immutable.LongMap( samesetByIdPairs: _* )
      
      // Search for strict subsets
      for( sameset <- samesets ) {
        val samesetId = sameset.id
        
        // Determine strict subsets ids
        for( unspeSameset <- unspecificSamesets ) {
          val unspeSamesetId = unspeSameset.id
          if( samesetId != unspeSamesetId ) {
            val( samesetValues, unspeSamesetValues ) = ( sameset.samesetsValues, unspeSameset.samesetsValues )
          
            // Check if unspeSameset is a strict subset of sameset
            if( isSubsetOf(unspeSamesetValues,samesetValues) ) {
              unspeSameset.isSubset = true
              if( sameset.strictSubsetsIds.isEmpty ) {
                sameset.strictSubsetsIds = Some( new ArrayBuffer[Long](1) )
              }
              sameset.strictSubsetsIds.get += unspeSamesetId
              //VD Add subset cluster's protMatchIds to overset cluster 
              sameset.subsetsKeys ++= unspeSameset.samesetsKeys
              unspeSameset.oversetId = Some(sameset.id)
            }
          }
        }
      }
      
      // Search for subsumable sets
      // A cluster is a subsumable set if doesn't have a specific value and is ! a strict subset
      for( cluster <- unspecificSamesets ) {
        
        if( ! cluster.isSubset ) {
          
          val clusterId = cluster.id
          val values = cluster.samesetsValues
          
          // Check if the current cluster has at least one specific value compare to other oversets
          val oversetClusterIdSet = new collection.mutable.HashSet[Long]
          var hasSpecificValue = false
          
          for( value <- values ) {
            val oversetClusterIds = oversetIdsByValue.get(value).getOrElse( ArrayBuffer() )
            
            // Check if the current value is specific to the current cluster
            if( oversetClusterIds.length == 0 ) { hasSpecificValue = true }
            else {
              for( tmpClusterId <- oversetClusterIds ) {
                if( tmpClusterId != clusterId ) {
                  oversetClusterIdSet += tmpClusterId
                }
              }
            }
          }
          
          // If subsumable set detected link it to strict oversets
          if( ! hasSpecificValue ) {
            
            cluster.isSubset = true
            
            // Link subsumable set to strict oversets
            for( oversetClusterId <- oversetClusterIdSet ) {
              val oversetCluster = samesetById(oversetClusterId)
              if( oversetCluster.subsumableSubsetsIds.isEmpty ) {
                oversetCluster.subsumableSubsetsIds = Some( new ArrayBuffer[Long] )
              }
              oversetCluster.subsumableSubsetsIds.get += clusterId
            }
          }
        }
      }
      
      setClusters ++= samesets
      
    }
    
    setClusters.toArray
  }
  
  @scala.annotation.tailrec
  private def getAllKeysHavingRelatedValues[K, V](
    valuesByKey: Map[K, Set[V]],
    keysByValue: Map[V, Seq[K]],
    valuesToSearch: Set[V],
    searchedValues: collection.mutable.HashSet[V],
    foundKeys: collection.mutable.HashSet[K]
  ): Unit = {
    if (valuesToSearch.isEmpty) return // break recursion if no more value to search

    val relatedValues = new ArrayBuffer[V](100)

    // Iterate values to initialize the search across the reversed map
    for (value <- valuesToSearch; if !searchedValues.contains(value)) {

      // Set the value status as searched
      searchedValues += value

      // Retrieve keys having this value
      val keysForThisValueOpt = keysByValue.get(value)
      assert(keysForThisValueOpt.isDefined, s"undefined keys for value '$value'")

      // Iterate related keys to retrieve their corresponding values
      for (key <- keysForThisValueOpt.get; if !foundKeys.contains(key)) {
        foundKeys += key

        relatedValues ++= valuesByKey(key)
      }
    }

    this.getAllKeysHavingRelatedValues(
      valuesByKey,
      keysByValue,
      relatedValues.toSet,
      searchedValues,
      foundKeys
    )
  }
  
  private def stringifySortedList( values: List[Any] ): String = {
    values(0) match {
      case int: Int => stringifySortedIntList(values.asInstanceOf[List[Int]])
      case v: Long => stringifySortedLongList(values.asInstanceOf[List[Long]]) // Handle Int and Long Scala primitives
      case str: String => stringifySortedStringList(values.asInstanceOf[List[String]])
      case _ => throw new Exception("can only sort integers, longs and strings")
    }
  }
    
  private def stringifySortedIntList( values: List[Int] ): String = {
    val sortedValues = values.sortWith { (a,b) => a < b }
    sortedValues.map { _.toString } mkString("&")
  }
  
  private def stringifySortedLongList( values: List[Long] ): String = {
    val sortedValues = values.sortWith { (a,b) => a < b }
    sortedValues.map { _.toString } mkString("&")
  }
  
  private def stringifySortedStringList( values: List[String] ): String = {
    val sortedValues = values.sortWith { (a,b) => a < b }
    sortedValues.map { _.toString } mkString("&")
  }
  
  /** Is set1 a subset of set2 */
  private def isSubsetOf[T]( set1: Set[T], set2: Set[T] ): Boolean = {
    
    for( value1 <- set1 ) {
      if( ! set2.contains(value1) ) { return false }
    }
    
    true
  }

}