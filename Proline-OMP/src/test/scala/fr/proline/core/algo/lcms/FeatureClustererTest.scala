package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import org.junit.Before
import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import org.scalatest.matchers.MustMatchers
import com.weiglewilczek.slf4s.Logging
import fr.proline.core.om.model.lcms._
import fr.proline.core.util.generator.lcms._

class FeatureClustererTest extends JUnitSuite with Logging {
  
  val nbFeatures = 1
  
  val runMapGenerator = new RunMapFakeGenerator(
    nbFeatures = nbFeatures,
    minMoz = LcMsRunGeneratorsTest.MIN_MOZ,
    maxMoz = LcMsRunGeneratorsTest.MAX_MOZ,
    minTime = 0,
    maxTime = LcMsRunGeneratorsTest.MAX_FT_TIME
  )
  
  val lcmsRun = LcMsRunGeneratorsTest.lcmsRunGenerator.generateLcMsRun()
  val runMap = runMapGenerator.generateRunMap( lcmsRun )
    
  // TODO: try other kind of parameters
  val ftMappingParams = FeatureMappingParams(mozTol=5., mozTolUnit= "PPM", timeTol=5f )  
  val ftClusteringParams = new ClusteringParams(
    ftMappingParams,
    intensityComputation = "MOST_INTENSE",
    timeComputation = "MOST_INTENSE"
  )
  
  @Test
  def testClusterizeFeatures() {
    
    var ftIdSeq = 0
    def newFtId = { ftIdSeq -= 1; ftIdSeq }
    
    val runMapFt = runMap.features.first
    val procMapFts = new ArrayBuffer[Feature]
    
    // Unique feature
    procMapFts += runMapFt.copy( id = newFtId, moz = 100., elutionTime = 100f, relations = runMapFt.relations.copy() )
    
    // Five identical features
    val( moz5, time5 ) = (200.,500f)
    for ( i <- 1 to 5) {
      procMapFts += runMapFt.copy( id = newFtId, moz = moz5, elutionTime = time5, relations = runMapFt.relations.copy() )
    }
    
    // Ten features with increasing m/z
    var( mzRefMZ10, timeRefMZ10 ) = (300.,1000f)
    val onePPMDelta = mzRefMZ10 / 1000000
    
    for( i <- 1 to 10 ) {
      procMapFts += runMapFt.copy( id = newFtId, moz = mzRefMZ10, elutionTime = timeRefMZ10, relations = runMapFt.relations.copy() )
      mzRefMZ10 += onePPMDelta
    }
    
    // Apply a shift identical to m/z tolerance
    mzRefMZ10 += 5 * onePPMDelta
    
    // Ten other features with increasing m/z after a shift larger then m/z tolerance
    for( i <- 1 to 10 ) {
      procMapFts += runMapFt.copy( id = newFtId, moz = mzRefMZ10, elutionTime = timeRefMZ10, relations = runMapFt.relations.copy() )
      mzRefMZ10 += onePPMDelta
    }
    
    // Ten features with increasing time
    var( mzRefRT10, timeRefRT10 ) = (400.,1500f)
    val oneSec = 1
    
    for( i <- 1 to 10 ) {
      procMapFts += runMapFt.copy( id = newFtId, moz = mzRefRT10, elutionTime = timeRefRT10, relations = runMapFt.relations.copy() )
      timeRefRT10 += oneSec
    }
    
    // Apply a shift identical to time tolerance
    timeRefRT10 += 5f * oneSec
    
    // Ten other features with increasing time after a shift larger then time tolerance
    for( i <- 1 to 10 ) {
      procMapFts += runMapFt.copy( id = newFtId, moz = mzRefRT10, elutionTime = timeRefRT10, relations = runMapFt.relations.copy() )
      timeRefRT10 += oneSec
    }
    
    //println( "nb features = "+procMapFts.length )
    
    val scans = lcmsRun.scanSequence.get.scans
    val newScans = new ArrayBuffer[LcMsScan](procMapFts.length)
    for ( (ft,scan) <- procMapFts.zip(scans.take(procMapFts.length)) ) {
      ft.relations.firstScanId = ft.id
      ft.relations.lastScanId = ft.id
      newScans += scan.copy( id = ft.id, time = ft.elutionTime )
    }
    
    // At this point we must have the same number of scans and features
    assert(newScans.length === procMapFts.length)
    
    // Append two features with close m/z and long overlapping duration
    val firstScan1 = scans(0).copy( id = LcMsScan.generateNewId, time = 1900f )
    val lastScan1 = scans(0).copy( id = LcMsScan.generateNewId, time = 2100f )
    val firstScan2 = scans(0).copy( id = LcMsScan.generateNewId, time = 2050f )
    val lastScan2 = scans(0).copy( id = LcMsScan.generateNewId, time = 2055f )
    newScans ++=  Array(firstScan1,lastScan1,firstScan2,lastScan2)
    
    val ft1Relations = runMapFt.relations.copy( firstScanId = firstScan1.id, lastScanId = lastScan1.id )
    val ft2Relations = runMapFt.relations.copy( firstScanId = firstScan2.id, lastScanId = lastScan2.id )
    procMapFts += runMapFt.copy( id = newFtId, moz = 500.000, elutionTime = 2000f, relations = ft1Relations )
    procMapFts += runMapFt.copy( id = newFtId, moz = 500.001, elutionTime = 2052f, relations = ft2Relations )
    
    val processedMap = runMap.toProcessedMap(1, -1, procMapFts.toArray)
    val newProcMap = ClusterizeFeatures(processedMap,newScans,ftClusteringParams)
    
    // We expect 7 features after the clustering
    assertEquals(7, newProcMap.features.length)
    
    //println( "nb features after clustering = "+newProcMap.features.length )
    /*for( ft <- newProcMap.features ) {
      println("moz="+ft.moz + " time="+ft.elutionTime)
    }*/
    //println( "nb sbtfts"+newProcMap.features.last.subFeatures.length )
    
    ()
  }
  
  //@Test
  def testBuildFeatureCluster() {
    
    ()
  }
  
}