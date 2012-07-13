package fr.proline.core.om.model.msq

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.core.utils.misc.InMemoryIdGen

trait Item {
  var selectionLevel: Int
}

trait QuantComponent {
  val quantChannelId: Int
  val rawAbundance: Float
  var abundance: Float
  var selectionLevel: Int
  
  def hasRawAbundance = if( rawAbundance.isNaN ) false else true
  def hasAbundance = if( abundance.isNaN ) false else true
}

trait LcmsQuantComponent extends QuantComponent {
  val elutionTime: Float
  val scanNumber: Int
}

trait MasterQuantComponent extends Item {
  var id: Int
  //var quantComponentMap: Map[Int,QuantComponent] // QuantComponent mapped by quantChannelId
}

trait MasterLcmsQuantComponent extends MasterQuantComponent {
  val calculatedMoz: Double
  val charge: Int
  val elutionTime: Float
}

//object QuantReporterIon extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantReporterIon( val quantChannelId: Int,
                             val moz: Double,
                             val rawAbundance: Float,
                             var abundance: Float,                             
                             var selectionLevel: Int
      
                            ) extends QuantComponent

object MasterQuantReporterIon extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantReporterIon( var id: Int,
                                   var msQueryId: Int,
                                   var spectrumId: Int,
                                   var scanNumber: Int,
                                   var quantReporterIonMap: Map[Int,QuantReporterIon],
                                   var selectionLevel: Int,
                                   var properties: MasterQuantReporterIonProperties

                                 ) extends MasterQuantComponent {
  
  /*def getQuantReporterIonMap: Map[Int,QuantReporterIon] = {
    this.quantComponentMap.map { entry => ( entry._1 -> entry._2.asInstanceOf[QuantReporterIon] ) }
  }*/
  
  /*def getQuantReporterIonMap: Map[Int,QuantReporterIonProperties] = {
    this.properties.getQuantReporterIons.map { repIon => repIon.getQuantChannelId -> repIon } toMap
  }*/
  
}


//object QuantPeptideIon extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantPeptideIon(  val rawAbundance: Float,
                             var abundance: Float,
                             val elutionTime: Float,
                             val scanNumber: Int,
                             
                             val peptideMatchesCount: Int,
                             val bestPeptideMatchScore: Option[Float] = None,
                             val predictedElutionTime: Option[Float] = None,
                             val predictedScanNumber:  Option[Int] = None,
                             
                             val quantChannelId: Int,
                             val peptideId:  Option[Int] = None,
                             val peptideInstanceId:  Option[Int] = None,
                             val msQueryIds: Option[Array[Int]] = None,
                             val lcmsFeatureId: Int,
                             val unmodifiedPeptideIonId: Option[Int] = None,
                             
                             var selectionLevel: Int

                           ) extends LcmsQuantComponent {
  
}

object MasterQuantPeptideIon extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideIon(  var id: Int,
                                   
                                   val calculatedMoz: Double,
                                   val unlabeledMoz: Double,
                                   val charge: Int,
                                   val elutionTime: Float,
                                   val peptideMatchesCount: Int,
                                   
                                   var quantPeptideIonMap: Map[Int,QuantPeptideIon],
                                   var properties: MasterQuantPeptideIonProperties,
                                   //var bestQuantPeptideIon: QuantPeptideIon,
                                   var masterQuantReporterIons: Array[MasterQuantReporterIon] = null,                                   
                                   
                                   val bestPeptideMatchId: Int,
                                   
                                   var selectionLevel: Int

                                 ) extends MasterLcmsQuantComponent {
  
  /*def getQuantPeptideIonMap: Map[Int,QuantPeptideIonProperties] = {
    this.properties.getQuantPeptideIons.map { pepIon => pepIon.getQuantChannelId -> pepIon } toMap
  }*/
  //this.quantComponentMap.map { entry => ( entry._1 -> entry._2 ) }
  
  def getBestQuantPeptideIon: Option[QuantPeptideIon] = {
    val bestQuantChannelId = this.properties.getBestQuantChannelId
    if( bestQuantChannelId == None ) None
    else this.quantPeptideIonMap.get( bestQuantChannelId.get )
  }    
  
}


//object QuantPeptide extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantPeptide( val rawAbundance: Float,
                         var abundance: Float,
                         val elutionTime: Float,
                         
                         val quantChannelId: Int,
                         val peptideId: Int,
                         val peptideInstanceId: Int,
                         
                         var selectionLevel: Int

                       ) extends QuantComponent {
  
}

object MasterQuantPeptide extends InMemoryIdGen

@JsonSnakeCase
case class MasterQuantPeptide( var id: Int,
    
                               val peptideMatchesCount: Float,
                               var proteinMatchesCount: Float,
                               
                               var quantPeptideMap: Map[Int,QuantPeptide], // QuantPeptide by quant channel id
                               var masterQuantPeptideIons: Array[MasterQuantPeptideIon] = null,
                               
                               val peptideId: Int, // without label in the context of isotopic labeling
                               val peptideInstanceId: Int, // without label in the context of isotopic labeling
                               var masterQuantProteinSetIds: Array[Int] = null,
                               
                               var selectionLevel: Int,
                               var properties: MasterQuantPeptideProperties
      
                             ) extends Item {
  
  /*lazy val quantPeptideMap: Map[Int,QuantPeptideProperties] = {
    this.properties.getQuantPeptides.map { pepIon => pepIon.getQuantChannelId -> pepIon } toMap
  }*/
  
  def isProteinSetSpecific: Option[Boolean] = {
    if( this.masterQuantProteinSetIds == null || 
        this.masterQuantProteinSetIds.length == 0 ) return None
        
    val isProteinSetSpecific = if( this.masterQuantProteinSetIds.length == 1 ) true else false
    Some(isProteinSetSpecific)    
  }
  
  def isProteinMatchSpecific: Option[Boolean] = {
    if( this.proteinMatchesCount == 0 ) return None
        
    val isProteinMatchSpecific = if( this.proteinMatchesCount == 1 ) true else false
    Some(isProteinMatchSpecific)
  }
  
  def getBestQuantPeptide: QuantPeptide = {    
    this.quantPeptideMap.values.reduce { (a,b) => if( a.abundance > b.abundance ) a else b }
  }
  
  def getQuantPeptideAbundance( quantChannelId: Int ): Float = {
    val quantPeptide = this.quantPeptideMap.get(quantChannelId)
    if( quantPeptide == None ) Float.NaN else quantPeptide.get.abundance
  }
  
  def getDefinedAbundancesForQuantChannels( quantChannelIds: Array[Int] ): Array[Float] = {    
    quantChannelIds map { quantChannelId => getQuantPeptideAbundance(quantChannelId) } filter { ! _.isNaN }
  }
   
  def calcMeanAbundanceForQuantChannels( quantChannelIds: Array[Int] ): Float = {
    
    val values = this.getDefinedAbundancesForQuantChannels( quantChannelIds )
    val nbValues = values.length
    
    var mean = Float.NaN
    if( nbValues > 0 ) {
      mean = values.reduceLeft[Float](_+_) / nbValues
    }
    
    mean
  }

  def calcRatio( numQuantChannelIds: Array[Int], denomQuantChannelIds: Array[Int] ): Float = {

    val quantPepMap = this.quantPeptideMap
    
    val numerator = this.calcMeanAbundanceForQuantChannels( denomQuantChannelIds )
    if( numerator.isNaN || numerator == 0 ) return Float.NaN
    
    val denominator = this.calcMeanAbundanceForQuantChannels( denomQuantChannelIds )
    if( denominator.isNaN || denominator == 0  ) return Float.NaN
    
    numerator/denominator
  }

}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantProteinSet (
  val rawAbundance: Float,
  var abundance: Float,
  val quantChannelId: Int,
  var selectionLevel: Int
 ) extends QuantComponent


object MasterQuantProteinSet extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantProteinSet(  var id: Int,
    
                                   val peptideMatchesCount: Float,
                                   
                                   var quantProteinSetMap: Map[Int,QuantProteinSet], // QuantProteinSet by quant channel id
                                   var masterQuantPeptides: Array[MasterQuantPeptide] = null,
                                   
                                   val selectedMasterQuantPeptideIds: Array[Int],
                                   val peptideSetId: Int,
                                   val proteinIds: Array[Int],
                                   val proteinMatchIds: Array[Int],
                                   val specificSampleId: Int = 0, // defined if the protein has been seen in a single sample
                                   
                                   var selectionLevel: Int,
                                   var properties: MasterQuantProteinSetProperties

                               ) extends Item {
  
}

object QuantResultSummary extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantResultSummary( var id: Int,
                               var description: String,
                               
                               var masterQuantProteinSets: Array[MasterQuantProteinSet],
                               var masterQuantPeptides: Array[MasterQuantProteinSet],
                               var masterQuantPeptideIons: Array[MasterQuantProteinSet],
                               
                               val quantResultSetId: Int
                               
                               )  {
  
}
