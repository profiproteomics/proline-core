package fr.proline.core.util.generator.lcms

import org.junit.Test
import org.scalatest.junit.JUnitSuite

import com.weiglewilczek.slf4s.Logging

import fr.proline.core.om.model.lcms.RawFile

@Test
class LcMsRunFakeGeneratorTest extends JUnitSuite with Logging {
  
  private final val MIN_MOZ = 300
  private final val MAX_MOZ = 2000
  private final val MAX_RUN_TIME = 120 * 60
  private final val MAX_FT_TIME = MAX_RUN_TIME
  private final val MAX_MS1_INTENSITY = 10e9.toInt
  private final val MAX_MS2_INTENSITY = 1000
  
  val landmarks = Array( (1,10e7),(300,10e7),(400,10e9),(2000,10e9),(3000,10e8),(4000,10e7),(6000,10e8),(7200,10e6) )
  val relLandmarks = landmarks.map( lm => lm._1.toFloat -> (lm._2/10e9).toFloat )
  
  val lcmsRunGenerator = new LcMsRunFakeGenerator(
    rawFile = RawFile(name="generated.raw",extension="raw"),
    minMoz = MIN_MOZ,
    maxMoz = MAX_MOZ,
    maxTime = MAX_RUN_TIME,
    ms1MaxIntensity = MAX_MS1_INTENSITY,
    ms2MaxIntensity = MAX_MS2_INTENSITY,
    ms1RelativeLandmarks = relLandmarks
  )
  
  @Test
  def testRunMapGenerator() {
    
    val runMapGenerator = new RunMapFakeGenerator(
      nbFeatures = 10,
      minMoz = MIN_MOZ,
      maxMoz = MAX_MOZ,
      minTime = 0,
      maxTime = MAX_FT_TIME
    )
    
    val runMap = runMapGenerator.generateRunMap( lcmsRunGenerator.generateLcMsRun )
    assert( runMap.features.length === 10 )

    /*runMap.features.foreach { ft =>
      println( "id=%d moz=%.6f intensity=%.0f charge=%d time=%.1f duration=%.0f ms1Count=%d ms2Count=%d".format(
        ft.id, ft.moz, ft.intensity, ft.charge, ft.elutionTime, ft.duration, ft.ms1Count, ft.ms2Count
      ))
      
    }*/
    
    ()
  }
  
  @Test
  def testMapSetGenerator() {
    
    val nbMaps = 2
    val nbFeatures = 1000
    
    val runMapGenerator = new RunMapFakeGenerator(
      nbFeatures = nbFeatures,
      minMoz = MIN_MOZ,
      maxMoz = MAX_MOZ,
      minTime = 0,
      maxTime = MAX_FT_TIME
    )
    
    val lcmsRun = lcmsRunGenerator.generateLcMsRun()
    val runMap = runMapGenerator.generateRunMap( lcmsRun )
    
    val alnFakeGen = new MapAlignmentFakeGenerator( distortion = Distortions.SINE_DISTORTION() )
    val mapSetFakeGen = new LcMsMapSetFakeGenerator( nbMaps = nbMaps, alnFakeGenerator = alnFakeGen )
    
    val mapSet = mapSetFakeGen.generateMapSet(lcmsRun, runMap)
    assert( mapSet.childMaps.length === nbMaps )
  }
  
  
  
  

}