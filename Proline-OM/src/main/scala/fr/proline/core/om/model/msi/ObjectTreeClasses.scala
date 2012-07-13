package fr.proline.core.om.model.msi

import scala.reflect.BeanProperty
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include

/** 
* @param fragmentMatches 
**/
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class SpectrumMatch (
@BeanProperty var fragmentMatches: Array[FragmentMatch]
)


/** 
* @param ionSerie 
* @param aaPosition 
* @param type 
* @param charge 
* @param moz 
* @param calculatedMoz 
* @param intensity 
* @param neutralLossMass 
* @param theoreticalFragmentId 
**/
@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class FragmentMatch ( 
  @BeanProperty var ionSerie: String,
  @BeanProperty var aaPosition: Int,
  @BeanProperty var `type`: String = "regular",
  @BeanProperty var charge: Int,
  @BeanProperty var moz: Double,
  @BeanProperty var calculatedMoz: Double,
  @BeanProperty var intensity: Float,
  @BeanProperty var neutralLossMass: Option[Double] = None,
  @BeanProperty var theoreticalFragmentId: Int
)

