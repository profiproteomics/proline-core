package fr.proline.core.om.model.msq


import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.primitives.isZeroOrNaN
import fr.proline.core.om.model.msi.{PeptideInstance,ProteinSet,ResultSummary}

trait Item {
  var selectionLevel: Int
}

trait QuantComponent {
  val quantChannelId: Long
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
  def id: Long
  //var quantComponentMap: Map[Int,QuantComponent] // QuantComponent mapped by quantChannelId
}

trait MasterLcmsQuantComponent extends MasterQuantComponent {
  val calculatedMoz: Option[Double]
  val charge: Int
  val elutionTime: Float
}

case class QuantReporterIon(
  val quantChannelId: Long,
  val moz: Double,
  val rawAbundance: Float,
  var abundance: Float,
  var selectionLevel: Int
) extends QuantComponent

object MasterQuantReporterIon extends InMemoryIdGen

case class MasterQuantReporterIon(
  var id: Long,
  var msQueryId: Long,
  var spectrumId: Long,
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


case class QuantPeptideIon(  
  val rawAbundance: Float,
  var abundance: Float,
  val moz: Double,
  val elutionTime: Float,
  val duration: Float,
  val correctedElutionTime: Float,
  val scanNumber: Int,
  
  var peptideMatchesCount: Int,  
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  var ms2MatchingFrequency: Option[Float], // TODO: remove the Option
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  var bestPeptideMatchScore: Option[Float] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Float] )
  var predictedElutionTime: Option[Float] = None,
  
  val quantChannelId: Long,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val peptideId: Option[Long] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val peptideInstanceId: Option[Long] = None,
  
  @JsonDeserialize(contentAs = classOf[ Array[Long] ] )
  val msQueryIds: Option[Array[Long]] = None,
  
  val lcmsFeatureId: Long, // TODO: set as Option[Long] = None
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsMasterFeatureId: Option[Long] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val unmodifiedPeptideIonId: Option[Long] = None,
  
  var selectionLevel: Int = 2

 ) extends LcmsQuantComponent {
  
  // Check some requirements
  if( ms2MatchingFrequency.isDefined ) {
    val freq = ms2MatchingFrequency.get
    require( freq >= 0f && freq <= 1f, "MS2 matching frequency must be a number between 0 and 1, but not " + freq )
  }
  
}

object MasterQuantPeptideIon extends InMemoryIdGen

case class MasterQuantPeptideIon(
  var id: Long,
  
  val unlabeledMoz: Double,
  val charge: Int,
  val elutionTime: Float,
  val peptideMatchesCount: Int,
  val calculatedMoz: Option[Double] = None,
  
  var selectionLevel: Int,
  var masterQuantPeptideId: Long, // is a master quant component id in MSIdb
  var resultSummaryId: Long,

  var peptideInstanceId: Option[Long] = None,
  var bestPeptideMatchId: Option[Long] = None,
  var lcmsMasterFeatureId: Option[Long] = None,
  var unmodifiedPeptideIonId: Option[Long] = None,
   
  var quantPeptideIonMap: Map[Long, QuantPeptideIon], // Key = QuantChannel ID ; Value = QuantPeptideIon
  var properties: Option[MasterQuantPeptideIonProperties] = None,
  var masterQuantReporterIons: Array[MasterQuantReporterIon] = null
   
 ) extends MasterLcmsQuantComponent {
  
  /*def getQuantPeptideIonMap: Map[Int,QuantPeptideIonProperties] = {
    this.properties.getQuantPeptideIons.map { pepIon => pepIon.getQuantChannelId -> pepIon } toMap
  }*/
  //this.quantComponentMap.map { entry => ( entry._1 -> entry._2 ) }
  
  def getBestQuantPeptideIon: Option[QuantPeptideIon] = {
    if( this.properties.isEmpty ) return None
    
    val bestQuantChannelId = this.properties.get.getBestQuantChannelId
    if( bestQuantChannelId.isEmpty ) None
    else this.quantPeptideIonMap.get( bestQuantChannelId.get )
  }
  
  def calcAbundanceSum(): Float = {
    quantPeptideIonMap.values.foldLeft(0f)( (s,qp) => s + qp.abundance )
  }
  
  def calcFrequency( qcCount: Int ): Float = {
    this.countDefinedRawAbundances() / qcCount
  }
    
  def countDefinedRawAbundances(): Int = {
    quantPeptideIonMap.values.count( qPepIon => isZeroOrNaN(qPepIon.rawAbundance) == false )
  }
  
}


case class QuantPeptide(
  val rawAbundance: Float,
  var abundance: Float,
  val elutionTime: Float,
  val peptideMatchesCount: Int,
  var selectionLevel: Int,
  
  val quantChannelId: Long,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val peptideId: Option[Long] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val peptideInstanceId: Option[Long] = None

) extends QuantComponent

object MasterQuantPeptide extends InMemoryIdGen

case class MasterQuantPeptide(
  var id: Long, // important: master quant component id
  val peptideInstance: Option[PeptideInstance], // without label in the context of isotopic labeling
  var quantPeptideMap: Map[Long,QuantPeptide], // QuantPeptide by quant channel id
  var masterQuantPeptideIons: Array[MasterQuantPeptideIon],
  
  var selectionLevel: Int,
  var resultSummaryId: Long,
  var properties: Option[MasterQuantPeptideProperties] = None
  
) extends MasterQuantComponent {
  
  //private lazy val _id = MasterQuantPeptide.generateNewId
  
  //def id(): Int = if( this.peptideInstance.isDefined ) this.peptideInstance.get.id else this._id // TODO: replace by true MQC id
  def getPeptideId: Option[Long] = if( this.peptideInstance.isDefined ) Some(this.peptideInstance.get.peptide.id) else None
  
  def getMasterQuantProteinSetIds(): Option[Array[Long]] = {
    if( this.properties.isDefined ) this.properties.get.getMqProtSetIds() else None
  }
  
  def isProteinSetSpecific: Option[Boolean] = {

    val protSetIdCount = this.getMasterQuantProteinSetIds.map( _.length ).getOrElse(0)
    if( protSetIdCount == 0 ) return None
    
    val isProteinSetSpecific = if( this.getMasterQuantProteinSetIds.get.length == 1 ) true else false
    Some(isProteinSetSpecific)
  }
  
  def isProteinMatchSpecific: Option[Boolean] = {
    if( this.peptideInstance.isEmpty ) return None
    
    val proteinMatchesCount = this.peptideInstance.get.proteinMatchesCount
    if( proteinMatchesCount == 0 ) return None
        
    val isProteinMatchSpecific = if( proteinMatchesCount == 1 ) true else false
    Some(isProteinMatchSpecific)
  }
  
  def getBestQuantPeptide: QuantPeptide = {
    this.quantPeptideMap.values.reduce { (a,b) => if( a.abundance > b.abundance ) a else b }
  }
  
  def getQuantPeptidePepMatchesCount( quantChannelId: Long ): Int = {
    val quantPeptide = this.quantPeptideMap.get(quantChannelId)
    if( quantPeptide.isEmpty ) 0 else quantPeptide.get.peptideMatchesCount
  }
  
  def getQuantPeptideRawAbundance( quantChannelId: Long ): Float = {
    val quantPeptide = this.quantPeptideMap.get(quantChannelId)
    if( quantPeptide.isEmpty ) Float.NaN else quantPeptide.get.rawAbundance
  }
  
  def getQuantPeptideAbundance( quantChannelId: Long ): Float = {
    val quantPeptide = this.quantPeptideMap.get(quantChannelId)
    if( quantPeptide.isEmpty ) Float.NaN else quantPeptide.get.abundance
  }
  
  def getPepMatchesCountsForQuantChannels( quantChannelIds: Array[Long] ): Array[Int] = {   
    quantChannelIds.map( getQuantPeptidePepMatchesCount(_) )
  }
  
  def getRawAbundancesForQuantChannels( quantChannelIds: Array[Long] ): Array[Float] = {   
    quantChannelIds.map( getQuantPeptideRawAbundance(_) )
  }
  
  def getAbundancesForQuantChannels( quantChannelIds: Array[Long] ): Array[Float] = {   
    quantChannelIds.map( getQuantPeptideAbundance(_) )
  }
  
  def setAbundancesForQuantChannels( abundances: Seq[Float], quantChannelIds: Seq[Long] ) {
    
    for( (ab, qcId) <- abundances.zip( quantChannelIds ) ) {
      if( this.quantPeptideMap.contains(qcId) ) {
        this.quantPeptideMap(qcId).abundance = ab
      } else {
        this.quantPeptideMap = this.quantPeptideMap + (
          qcId -> QuantPeptide(
            rawAbundance = Float.NaN,
            abundance = ab,
            elutionTime = Float.NaN,
            peptideMatchesCount = 0,
            selectionLevel = 2,
            quantChannelId = qcId
          )
        )
      }
    }
    
  }
  
  def getDefinedAbundancesForQuantChannels( quantChannelIds: Array[Long] ): Array[Float] = {    
    quantChannelIds map { quantChannelId => getQuantPeptideAbundance(quantChannelId) } filter { ! _.isNaN }
  }
   
  def calcMeanAbundanceForQuantChannels( quantChannelIds: Array[Long] ): Float = {
    
    val values = this.getDefinedAbundancesForQuantChannels( quantChannelIds )
    val nbValues = values.length
    
    var mean = Float.NaN
    if( nbValues > 0 ) {
      mean = values.reduceLeft[Float](_+_) / nbValues
    }
    
    mean
  }

  def calcRatio( numQuantChannelIds: Array[Long], denomQuantChannelIds: Array[Long] ): Float = {

    val quantPepMap = this.quantPeptideMap
    
    val numerator = this.calcMeanAbundanceForQuantChannels( denomQuantChannelIds )
    if( numerator.isNaN || numerator == 0 ) return Float.NaN
    
    val denominator = this.calcMeanAbundanceForQuantChannels( denomQuantChannelIds )
    if( denominator.isNaN || denominator == 0  ) return Float.NaN
    
    numerator/denominator
  }
  
  def getRatios( groupSetupNumber: Long ): List[Option[ComputedRatio]] = {
    this.properties.flatMap { mqPepProps =>
      val profileByGSNumberOpt = mqPepProps.getMqPepProfileByGroupSetupNumber
      if( profileByGSNumberOpt.isEmpty ) None
      else {
        val mqPepProfile = profileByGSNumberOpt.get(groupSetupNumber.toString)
        Some( mqPepProfile.getRatios() )
      }
    }.getOrElse( List() )
  }

}

case class QuantProteinSet (
  val rawAbundance: Float,
  var abundance: Float,
  val peptideMatchesCount: Int,
  val quantChannelId: Long,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val proteinSetId: Option[Long] = None,
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val proteinMatchId: Option[Long] = None,
  var selectionLevel: Int
 ) extends QuantComponent


case class MasterQuantProteinSet(
  val proteinSet: ProteinSet,
  var quantProteinSetMap: Map[Long,QuantProteinSet], // QuantProteinSet by quant channel id
  var masterQuantPeptides: Array[MasterQuantPeptide] = null,
     
  var selectionLevel: Int,
  var properties: Option[MasterQuantProteinSetProperties] = None
     
) extends MasterQuantComponent {
  
  def id() = this.proteinSet.id
  
  def getMasterQuantComponentId() = {
    val mqcId = this.proteinSet.masterQuantComponentId
    require( mqcId != 0, "masterQuantComponentId is not defined" )
    mqcId
  }
  
  def getBestProfile( groupSetupNumber: Int ): Option[MasterQuantProteinSetProfile] = {
    if( properties.isEmpty ) return None
    
    val props = this.properties.get
    if( props.getMqProtSetProfilesByGroupSetupNumber.isEmpty ) return None
    
    val profilesOpt = props.getMqProtSetProfilesByGroupSetupNumber.get.get(groupSetupNumber.toString)
    if( profilesOpt.isEmpty ) return None
    
    var profiles = profilesOpt.get
    if( profiles.length == 0 ) return None
    
    // Check if we have a poor number of peptides
    // TODO: use properties.get.getSelectedMasterQuantPeptideIds.get instead when it is correctly stored
    if ( masterQuantPeptides.length < 5 ) {
      
      // Keep only profiles with highest number of defined ratios
      def countDefinedRatios(profile: MasterQuantProteinSetProfile) = profile.getRatios.count( _.map(_.ratioValue.isNaN == false).getOrElse(false) )
      
      val maxDefinedRatios = profiles.map(countDefinedRatios(_)).max
      profiles = profiles.filter( countDefinedRatios(_) == maxDefinedRatios )     
    }

    // Sort profiles by the number of MQ peptides
    val profilesSortedByPepCount = profiles.sortWith { (a,b) => a.mqPeptideIds.length > b.mqPeptideIds.length }
    
    val bestProfile = if( profilesSortedByPepCount.length == 1 ) {
      // Single profile => it's easy
      profilesSortedByPepCount(0)
    }
    // If one winner
    else if ( profilesSortedByPepCount(0).mqPeptideIds.length > profilesSortedByPepCount(1).mqPeptideIds.length ) {
      profilesSortedByPepCount(0)
    // Else no winner => conflict resolution based on abundance
    } else {
      
      def profileMaxAbundance( profile: MasterQuantProteinSetProfile ): Float = {
        
        var maxProfileAbundance = 0f
        for( ratioOpt <- profile.ratios ; ratio <- ratioOpt ) {
          if( ratio.numerator > maxProfileAbundance ) maxProfileAbundance = ratio.numerator
          if( ratio.denominator > maxProfileAbundance ) maxProfileAbundance = ratio.denominator
        }
        
        maxProfileAbundance
      }
      
      if( profileMaxAbundance(profilesSortedByPepCount(0)) > profileMaxAbundance(profilesSortedByPepCount(1)) )
        profilesSortedByPepCount(0)
      else
        profilesSortedByPepCount(1)
    }

    Some(bestProfile)
  }
  
  def setAbundancesForQuantChannels( abundances: Seq[Float], quantChannelIds: Seq[Long] ) {
    
    for( (ab, qcId) <- abundances.zip( quantChannelIds ) ) {
      if( this.quantProteinSetMap.contains(qcId) ) {
        this.quantProteinSetMap(qcId).abundance = ab
      }
    }   
  }
  
} 

case class QuantResultSummary(
  val quantChannelIds: Array[Long],
  var masterQuantProteinSets: Array[MasterQuantProteinSet],
  var masterQuantPeptides: Array[MasterQuantPeptide],
  var masterQuantPeptideIons: Array[MasterQuantPeptideIon],
 
  //var experimentalDesign
 
  var resultSummary: ResultSummary
 
) {
  require(masterQuantProteinSets != null, "masterQuantProteinSets must be provided")
  require(masterQuantPeptides != null, "masterQuantPeptides must be provided")
  require(masterQuantPeptideIons != null, "masterQuantPeptideIons must be provided")
  require(resultSummary != null, "resultSummary must be provided")
  
  def id() = resultSummary.id
}
