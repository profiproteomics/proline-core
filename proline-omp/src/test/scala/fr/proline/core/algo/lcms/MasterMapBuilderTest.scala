package fr.proline.core.algo.lcms

import com.typesafe.scalalogging.StrictLogging
import fr.proline.core.algo.lcms.filtering.Filter
import fr.proline.core.util.generator.lcms._
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

class MasterMapBuilderTest extends JUnitSuite  with StrictLogging {
  
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
    val ftMappingParams = FeatureMappingParams(mozTol = Some(10.0), mozTolUnit = Some("PPM"), timeTol = Some(20f), useMozCalibration = false )
    val ftClusteringParams = ClusteringParams(mozTol = 10.0, mozTolUnit = "PPM", timeTol = 20f, intensityComputation = "MOST_INTENSE", timeComputation= "MOST_INTENSE")
      /*
      mozTol=10., mozTolUnit= "PPM", timeTol=20f,
      intensityComputation = "MOST_INTENSE",
      timeComputation = "MOST_INTENSE"
      */
    
    val masterFtFilter = new Filter( "INTENSITY", "GT", 0.0 )
    val masterMap = BuildMasterMap(mapSet, Seq(lcmsRun.scanSequence.get), Some(masterFtFilter), ftMappingParams,ftClusteringParams)
    
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