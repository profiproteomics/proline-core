package fr.proline.core.algo.lcms

import scala.collection.mutable.ArrayBuffer
import org.junit.Before
import org.junit.Test
import org.junit.Assert._
import org.scalatest.junit.JUnitSuite
import com.typesafe.scalalogging.slf4j.Logging
import fr.proline.core.om.model.lcms._
import fr.proline.core.util.generator.lcms._
import filtering.Filter

class MasterMapBuilderTest extends JUnitSuite  with Logging {
  
  @Test
  def testBuildMasterMap() {
    
    val nbMaps = 3
    val nbFeatures = 1000
    
    val rawMapGenerator = new RawMapFakeGenerator(
      nbFeatures = nbFeatures,
      minMoz = LcMsRunGeneratorsTest.MIN_MOZ,
      maxMoz = LcMsRunGeneratorsTest.MAX_MOZ,
      minTime = 0,
      maxTime = LcMsRunGeneratorsTest.MAX_FT_TIME
    )
    
    val lcmsRun = LcMsRunGeneratorsTest.lcmsRunGenerator.generateLcMsRun()
    val rawMap = rawMapGenerator.generateRawMap( lcmsRun )
    
    val alnFakeGen = new MapAlignmentFakeGenerator( distortion = Distortions.SINE_DISTORTION(), amplitude = 30f )
    val mapSetFakeGen = new LcMsMapSetFakeGenerator(
      nbMaps = nbMaps,
      alnFakeGenerator = alnFakeGen,
      mozErrorPPM = 2f,
      timeError = 5f
    )
    
    // Generate a new map set
    val mapSet = mapSetFakeGen.generateMapSet(lcmsRun, rawMap)
    
    // Build the corresponding master map
    val ftMappingParams = FeatureMappingParams(mozTol=10.0, mozTolUnit= "PPM", timeTol=20f )  
    val ftClusteringParams = ClusteringParams(ftMappingParams, intensityComputation = "MOST_INTENSE", timeComputation= "MOST_INTENSE")
      /*
      mozTol=10., mozTolUnit= "PPM", timeTol=20f,
      intensityComputation = "MOST_INTENSE",
      timeComputation = "MOST_INTENSE"
      */
    
    val masterFtFilter = new Filter( "INTENSITY", "GT", 0.0 )
    val masterMap = BuildMasterMap(mapSet, Seq(lcmsRun.scanSequence.get), masterFtFilter, ftMappingParams,ftClusteringParams)
    
    // We should have less master features than the number of input features
    assertTrue(masterMap.features.length <= nbFeatures)
    //println( masterMap.features.length )
    
    masterMap.features.foreach { mft =>
      //val allTimes = mft.children.map( _.getCorrectedElutionTimeOrElutionTime )
      //val allmoz = mft.children.map( _.moz )
      //println( mft.charge )
      //println( allmoz.mkString("\t") )
      //println( mft.children.length )
    }
    
  }
  
}