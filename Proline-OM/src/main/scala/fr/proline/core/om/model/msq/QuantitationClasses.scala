package fr.proline.core.om.model.msq

import scala.collection.mutable.ArrayBuffer
import com.codahale.jerkson.JsonSnakeCase
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import fr.proline.core.utils.misc.InMemoryIdGen
import fr.proline.core.om.model.msi.{PeptideInstance,ProteinSet,ResultSummary}

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
  val moz: Double
  val elutionTime: Float
  val scanNumber: Int
}

trait MasterQuantComponent extends Item {
  def id: Int
  //var quantComponentMap: Map[Int,QuantComponent] // QuantComponent mapped by quantChannelId
}

trait MasterLcmsQuantComponent extends MasterQuantComponent {
  val calculatedMoz: Option[Double]
  val charge: Int
  val elutionTime: Float
}

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


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantPeptideIon(  val rawAbundance: Float,
                             var abundance: Float,
                             val moz: Double,
                             val elutionTime: Float,
                             val scanNumber: Int,
                             
                             var peptideMatchesCount: Int,
                             var bestPeptideMatchScore: Option[Float] = None,
                             var predictedElutionTime: Option[Float] = None,
                             var predictedScanNumber: Option[Int] = None,
                             
                             val quantChannelId: Int,
                             val peptideId: Option[Int] = None,
                             val peptideInstanceId: Option[Int] = None,
                             val msQueryIds: Option[Array[Int]] = None,
                             val lcmsFeatureId: Int,
                             val unmodifiedPeptideIonId: Option[Int] = None,
                             
                             var selectionLevel: Int = 2

                           ) extends LcmsQuantComponent {
  
}

object MasterQuantPeptideIon extends InMemoryIdGen

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantPeptideIon(  var id: Int,
                                   
                                   val unlabeledMoz: Double,
                                   val charge: Int,
                                   val elutionTime: Float,
                                   val peptideMatchesCount: Int,
                                   val calculatedMoz: Option[Double] = None,
                                                  
                                   var selectionLevel: Int,
                                   var resultSummaryId: Int,
                                   var bestPeptideMatchId: Option[Int] = None,
                                   var lcmsFeatureId: Option[Int] = None,
                                   var unmodifiedPeptideIonId: Option[Int] = None,
                                   
                                   var quantPeptideIonMap: Map[Int,QuantPeptideIon],
                                   var properties: Option[MasterQuantPeptideIonProperties] = None,
                                   //var bestQuantPeptideIon: QuantPeptideIon,
                                   var masterQuantReporterIons: Array[MasterQuantReporterIon] = null
                                   
                                 ) extends MasterLcmsQuantComponent {
  
  /*def getQuantPeptideIonMap: Map[Int,QuantPeptideIonProperties] = {
    this.properties.getQuantPeptideIons.map { pepIon => pepIon.getQuantChannelId -> pepIon } toMap
  }*/
  //this.quantComponentMap.map { entry => ( entry._1 -> entry._2 ) }
  
  def getBestQuantPeptideIon: Option[QuantPeptideIon] = {
    if( this.properties == None ) return None
    
    val bestQuantChannelId = this.properties.get.getBestQuantChannelId
    if( bestQuantChannelId == None ) None
    else this.quantPeptideIonMap.get( bestQuantChannelId.get )
  }    
  
}


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantPeptide( val rawAbundance: Float,
                         var abundance: Float,
                         val elutionTime: Float,
                         val peptideMatchesCount: Int,
                         
                         val quantChannelId: Int,
                         val peptideId: Int,
                         val peptideInstanceId: Int,
                         
                         var selectionLevel: Int

                       ) extends QuantComponent {
  
}

object MasterQuantPeptide extends InMemoryIdGen

@JsonSnakeCase
case class MasterQuantPeptide( val peptideInstance: Option[PeptideInstance], // without label in the context of isotopic labeling
                               var quantPeptideMap: Map[Int,QuantPeptide], // QuantPeptide by quant channel id
                               var masterQuantPeptideIons: Array[MasterQuantPeptideIon] = null,
                               
                               var selectionLevel: Int,
                               var properties: Option[MasterQuantPeptideProperties] = None
                               
                             ) extends MasterQuantComponent {
  
  private lazy val _id = MasterQuantPeptide.generateNewId
  
  def id(): Int = if( this.peptideInstance != None ) this.peptideInstance.get.id else this._id
  def getPeptideId: Option[Int] = if( this.peptideInstance != None ) Some(this.peptideInstance.get.peptide.id) else None
  
  def getMasterQuantProteinSetIds(): Option[Array[Int]] = {
    if( this.properties != None ) this.properties.get.getMasterQuantProteinSetIds()
    else None
  }
  
  def isProteinSetSpecific: Option[Boolean] = {
    val masterQuantProteinSetIds = this.getMasterQuantProteinSetIds.get
    if( masterQuantProteinSetIds == null || 
        masterQuantProteinSetIds.length == 0 ) return None
        
    val isProteinSetSpecific = if( masterQuantProteinSetIds.length == 1 ) true else false
    Some(isProteinSetSpecific)    
  }
  
  def isProteinMatchSpecific: Option[Boolean] = {
    if( this.peptideInstance == None ) return None
    
    val proteinMatchesCount = this.peptideInstance.get.proteinMatchesCount
    if( proteinMatchesCount == 0 ) return None
        
    val isProteinMatchSpecific = if( proteinMatchesCount == 1 ) true else false
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
  val peptideMatchesCount: Int,
  val quantChannelId: Int,
  var selectionLevel: Int
 ) extends QuantComponent


@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class MasterQuantProteinSet(  val proteinSet: ProteinSet,
                                   var quantProteinSetMap: Map[Int,QuantProteinSet], // QuantProteinSet by quant channel id
                                   var masterQuantPeptides: Array[MasterQuantPeptide] = null,
                                   
                                   var selectionLevel: Int,
                                   var properties: Option[MasterQuantProteinSetProperties] = None

                               ) extends MasterQuantComponent {
  
  def id() = this.proteinSet.id
}

@JsonSnakeCase
@JsonInclude( Include.NON_NULL )
case class QuantResultSummary( 
                               var masterQuantProteinSets: Array[MasterQuantProteinSet],
                               var masterQuantPeptides: Array[MasterQuantProteinSet],
                               var masterQuantPeptideIons: Array[MasterQuantProteinSet],
                               
                               var resultSummary: ResultSummary
                               
                               )  {
  
  def id() = resultSummary.id
}
