package fr.proline.core.util.generator.lcms

import scala.util.Random
import fr.proline.core.om.model.lcms.Feature
import fr.profi.util.random._

/**
 * Utility object containing helper functions for the generation a faked RawMap.
 */
object LcMsRandomator {
 
  def randomCharge(minCharge: Int, maxCharge: Int): Int = {
    require( minCharge > 0 && maxCharge > 0 )
    
    val randCharge = randomGaussian(-maxCharge,maxCharge,maxCharge).toInt.abs
    if( randCharge < minCharge ) minCharge
    else randCharge
  }
  
  def randomMoz(minMoz: Double, maxMoz: Double, mozCenter: Double): Double = {
    require( mozCenter >= minMoz && mozCenter <= maxMoz)
    
    val leftMax = 2 * mozCenter - minMoz
    var leftDraw = randomGaussian(minMoz,leftMax, (mozCenter-minMoz).toFloat/3 )
    if( leftDraw > mozCenter ) leftDraw = 2 * mozCenter - leftDraw
    
    val rightMin = 2 * mozCenter - maxMoz
    var rightDraw = randomGaussian(rightMin,maxMoz, (maxMoz-mozCenter).toFloat/3 )
    if( rightDraw < mozCenter ) rightDraw = 2 * mozCenter - rightDraw
    
    val draws = List(leftDraw,rightDraw)
    
    // Select it randomly
    val randomIndex = randomInt(0,1)
    
    draws(randomIndex)
    
    //if( (mozCenter-leftDraw).abs < (mozCenter-rightDraw).abs ) leftDraw
    //else rightDraw
    
    /*val avgMoz = (leftDraw+rightDraw)/2
    
    if( avgMoz < minMoz ) leftDraw
    else if( avgMoz > maxMoz ) rightDraw
    else avgMoz*/
  }
  
  def randomIntensity(minIntensity: Float, maxIntensity: Float): Float = {
    randomFloat(minIntensity,maxIntensity)
  }
  
  def randomDuration(minDuration: Float, maxDuration: Float, stdDev: Float ): Float = {
    randomGaussian(minDuration.toDouble, maxDuration.toDouble, stdDev.toDouble ).toFloat 
  }
  
  /*def scaleDurationStdDev(stdDevRef: Float, intensityRatio: Float): Float = {
    stdDevRef * intensityRatio
  }*/
 
  def fluctuateValue( value: Float, error: Float ): Float = {
    require( error >= 0 )
    
    value + (Random.nextGaussian.toFloat * error)
  }
  
  def fluctuateValue( value: Double, error: Double ): Double = {
    require( error >= 0 )
    
    value + (Random.nextGaussian * error)
  }
  
  def fluctuateValue( value: Float, error: Float, maxError: Float ): Float = {
    fluctuateValue( value.toDouble, error.toDouble, maxError.toDouble ).toFloat 
  }
  
  def fluctuateValue( value: Double, error: Double, maxError: Double ): Double = {
    require( error >= 0 )
    require( maxError >= 0 )
    
    randomGaussian( value - maxError/2, value + maxError/2, error ) 
  }
  //def randomFeature(): Feature = null
  
  //def estimateFeatureMs2Count( time: Float, duration: Float, tic: Array[Pair[Float,Float]] )
  //def estimateFeatureMs1Count( time: Float, duration: Float, tic: Array[Pair[Float,Float]] )
  
/*
  var id: Long,
  val moz: Double,
  var intensity: Float,
  val charge: Int,
  val elutionTime: Float,
  val duration: Float,
  val qualityScore: Double,
  var ms1Count: Int,
  var ms2Count: Int,
  val isOverlapping: Boolean,
  
  val isotopicPatterns: Option[Array[IsotopicPattern]],
  val relations: FeatureRelations,
  
  // Mutable optional fields
  var children: Array[Feature] = null,
  var subFeatures: Array[Feature] = null,
  var overlappingFeatures: Array[Feature] = null,
  var calibratedMoz: Option[Double] = None,
  var normalizedIntensity: Option[Float] = None,
  var correctedElutionTime: Option[Float] = None,
  var isClusterized: Boolean = false,
  var selectionLevel: Int = 2,
  
  var properties: Option[FeatureProperties] = None*/
}