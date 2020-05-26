package fr.proline.core.om.model.msq

import scala.collection.mutable.LongMap
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import fr.profi.util.collection._
import fr.profi.util.misc.InMemoryIdGen
import fr.profi.util.primitives.isZeroOrNaN
import fr.proline.core.om.model.msi._

trait Item {
  var selectionLevel: Int
}

trait QuantComponent {
  val quantChannelId: Long
  val rawAbundance: Float
  var abundance: Float
  var selectionLevel: Int
  def peptideMatchesCount: Int
  
  def hasRawAbundance = if( rawAbundance.isNaN ) false else true
  def hasAbundance = if( abundance.isNaN ) false else true
}

trait LcmsQuantComponent extends QuantComponent {
  val moz: Double
  val elutionTime: Float
  val scanNumber: Int
}

trait MasterQuantComponent[A <: QuantComponent] extends Item {
  
  def id: Long
  
  protected def getQuantComponentMap(): LongMap[A]
  
  protected def setQuantComponentMap(quantComponentMap: LongMap[A]): Unit
  
  protected def getMostAbundantQuantComponent(): A = {
    this.getQuantComponentMap.values.maxBy(_.abundance)
  }
  
  protected def getQuantComponentPepMatchesCount( quantChannelId: Long ): Int = {
    val quantCompOpt = this.getQuantComponentMap.get(quantChannelId)
    if( quantCompOpt.isEmpty ) 0 else quantCompOpt.get.peptideMatchesCount
  }
  
  protected def getQuantComponentRawAbundance(quantChannelId: Long, applySelectionFilter: Boolean): Float = {
    val quantCompOpt = this.getQuantComponentMap.get(quantChannelId)
    if (quantCompOpt.isEmpty) Float.NaN
    else {
      val quantComp = quantCompOpt.get
      if (applySelectionFilter && quantComp.selectionLevel < 2) Float.NaN
      else quantComp.rawAbundance
    }
  }
  
  protected def getQuantComponentAbundance( quantChannelId: Long ): Float = {
    val quantCompOpt = this.getQuantComponentMap.get(quantChannelId)
    if( quantCompOpt.isEmpty ) Float.NaN else quantCompOpt.get.abundance
  }
  
  def getPepMatchesCountsForQuantChannels( quantChannelIds: Array[Long] ): Array[Int] = {
    quantChannelIds.map( getQuantComponentPepMatchesCount(_) )
  }
  
  def getRawAbundancesForQuantChannels(quantChannelIds: Array[Long], applySelectionFilter: Boolean = false): Array[Float] = {
    quantChannelIds.map( getQuantComponentRawAbundance(_, applySelectionFilter) )
  }
  
  def getAbundancesForQuantChannels( quantChannelIds: Array[Long] ): Array[Float] = {
    quantChannelIds.map( getQuantComponentAbundance(_) )
  }
  
  def getDefinedAbundancesForQuantChannels( quantChannelIds: Array[Long] ): Array[Float] = {
    quantChannelIds map { quantChannelId => getQuantComponentAbundance(quantChannelId) } filter { ! _.isNaN }
  }
  
  
  def setAbundancesForQuantChannels(abundances: Seq[Float], quantChannelIds: Seq[Long])
    
  protected def updateOrCreateComponentForQuantChannels(
    abundances: Seq[Float],
    quantChannelIds: Seq[Long],
    buildQuantComponent: (Float,Long) => A
  ) {
    
    val quantCompMap = this.getQuantComponentMap()
    
    for( (ab, qcId) <- abundances.zip( quantChannelIds ) ) {
      if( quantCompMap.contains(qcId) ) {
        quantCompMap(qcId).abundance = ab
      } else {
        quantCompMap.put(qcId, buildQuantComponent(ab,qcId))
      }
    }
  }
   
  def calcMeanAbundanceForQuantChannels( quantChannelIds: Array[Long] ): Float = {
    
    val values = this.getDefinedAbundancesForQuantChannels( quantChannelIds )
    val nbValues = values.length
    
    if( nbValues == 0 ) Float.NaN else values.sum / nbValues
  }

  def calcRatio( numQuantChannelIds: Array[Long], denomQuantChannelIds: Array[Long] ): Float = {

    val numerator = this.calcMeanAbundanceForQuantChannels( denomQuantChannelIds )
    if( numerator.isNaN || numerator == 0 ) return Float.NaN
    
    val denominator = this.calcMeanAbundanceForQuantChannels( denomQuantChannelIds )
    if( denominator.isNaN || denominator == 0  ) return Float.NaN
    
    numerator / denominator
  }
}

trait MasterLcmsQuantComponent[ A <: QuantComponent] extends MasterQuantComponent[A] {
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
) extends QuantComponent {
  def peptideMatchesCount: Int = 1
}

object MasterQuantReporterIon extends InMemoryIdGen

case class MasterQuantReporterIon(
  var id: Long,
  val charge: Int,
  val elutionTime: Float,
  val msQueryId: Long,
  val spectrumId: Long,
  val scanNumber: Int,
  var quantReporterIonMap: LongMap[QuantReporterIon],
  var selectionLevel: Int,
  var masterQuantPeptideIonId: Option[Long] = None,
  var properties: Option[MasterQuantReporterIonProperties] = None

) extends MasterQuantComponent[QuantReporterIon] {
  
  def getQuantComponentMap() = quantReporterIonMap
  
  def setQuantComponentMap(quantComponentMap: LongMap[QuantReporterIon]) = {
    this.quantReporterIonMap = quantComponentMap
  }
  
  /*def getQuantReporterIonMap: Map[Int,QuantReporterIon] = {
    this.quantComponentMap.map { entry => ( entry._1 -> entry._2.asInstanceOf[QuantReporterIon] ) }
  }*/
  
  /*def getQuantReporterIonMap: Map[Int,QuantReporterIonProperties] = {
    this.properties.getQuantReporterIons.map { repIon => repIon.getQuantChannelId -> repIon } toMap
  }*/
  
  def setAbundancesForQuantChannels(abundances: Seq[Float], quantChannelIds: Seq[Long]) {
    this.updateOrCreateComponentForQuantChannels(
      abundances,
      quantChannelIds,
      (abundance,qcId) => QuantReporterIon(
        moz = Double.NaN,
        rawAbundance = Float.NaN,
        abundance = abundance,
        selectionLevel = 2,
        quantChannelId = qcId
      )
    )
  }
  
}

/** Represents a PeptideIon quantitative data in a single quant channel (run).
 *  
 *  This information is stored in Proline datastore in MSIdb.master_quant_component.object_tree as a JSON 
 *  string.
 * 
 * @see MasterQuantPeptideIon
 *
 */
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
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsFeatureId: Option[Long] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val lcmsMasterFeatureId: Option[Long] = None,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val unmodifiedPeptideIonId: Option[Long] = None,
  
  var selectionLevel: Int = 2,
  
  @JsonDeserialize(contentAs = classOf[java.lang.Boolean] )
  var isReliable: Option[Boolean] = None

 ) extends LcmsQuantComponent {
  
  // Check some requirements
  if( ms2MatchingFrequency.isDefined ) {
    val freq = ms2MatchingFrequency.get
    require( freq >= 0f && freq <= 1f, "MS2 matching frequency must be a number between 0 and 1, but not " + freq )
  }
  
}

object MasterQuantPeptideIon extends InMemoryIdGen

/** Represents PeptideIon quantitative information across multiple quant channels (runs). 
 * 
 * This information is stored in Proline datastore in MSIdb.master_quant_component. An example 
 * can be extracted from the datastore by the following SQL Query : 
 * {{{
 *  select * 
 *  from master_quant_component mqc, object_tree ot
 *  where mqc.schema_name like '%ion%' 
 *    and mqc.object_tree_id = ot.id
 *  limit 10	
 * }}}
 *
 */
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
   
  var quantPeptideIonMap: LongMap[QuantPeptideIon], // Key = QuantChannel ID ; Value = QuantPeptideIon
  var properties: Option[MasterQuantPeptideIonProperties] = None,
  var masterQuantReporterIons: Array[MasterQuantReporterIon] = Array()
   
 ) extends MasterLcmsQuantComponent[QuantPeptideIon] {
  
  def getQuantComponentMap() = quantPeptideIonMap
  def setQuantComponentMap(quantComponentMap: LongMap[QuantPeptideIon]) = {
    this.quantPeptideIonMap = quantComponentMap
  }
  
  def getBestQuantPeptideIon(): Option[QuantPeptideIon] = {
    if( this.properties.isEmpty ) return None
    
    val bestQuantChannelId = this.properties.get.getBestQuantChannelId
    if( bestQuantChannelId.isEmpty ) None
    else this.quantPeptideIonMap.get( bestQuantChannelId.get )
  }
  
  def calcAbundanceSum(): Float = {
    quantPeptideIonMap.values.filter(qp => !isZeroOrNaN(qp.abundance)).foldLeft(0f)( (s,qp) => s + qp.abundance )
  }
  
  def calcRawAbundanceSum(): Float = {
    quantPeptideIonMap.values.filter(qp => !isZeroOrNaN(qp.rawAbundance)).foldLeft(0f)( (s,qp) => s + qp.rawAbundance)
  }

  def calcFrequency( qcCount: Int ): Float = {
    this.countDefinedRawAbundances() / qcCount
  }
  
 def calcIdentFrequency( qcCount: Int ): Float = {
    this.countIdentifications() / qcCount
  }
 
  def countIdentifications(): Int = {
    quantPeptideIonMap.values.count( qPepIon => qPepIon.peptideMatchesCount>0)
  }
  
  def countDefinedRawAbundances(): Int = {
    quantPeptideIonMap.values.count( qPepIon => isZeroOrNaN(qPepIon.rawAbundance) == false )
  }
  
  def setAbundancesForQuantChannels( abundances: Seq[Float], quantChannelIds: Seq[Long] ) {
    this.updateOrCreateComponentForQuantChannels(
      abundances,
      quantChannelIds,
      (abundance,qcId) => QuantPeptideIon(
        rawAbundance = Float.NaN,
        abundance = abundance,
        moz = Double.NaN,
        elutionTime = Float.NaN,
        duration = 0,
        correctedElutionTime = Float.NaN,
        scanNumber = 0,
        peptideMatchesCount = 0,
        ms2MatchingFrequency = None,
        quantChannelId = qcId
      )
    )
  }
    
}

/** Represents a Peptide quantitative data in a single quant channel (run).
 *  
 *  This information is stored in Proline datastore in MSIdb.master_quant_component.object_tree as a JSON 
 *  string.
 * 
 * @see MasterQuantPeptide
 *
 */
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

/** Represents Peptide quantitative information across multiple quant channels (runs). 
 * 
 * This information is stored in Proline datastore in MSIdb.master_quant_component. An example 
 * can be extracted from the datastore by the following SQL Query : 
 * {{{
 *  select * 
 *  from master_quant_component mqc, object_tree ot
 *  where mqc.schema_name like '%peptide%' 
 *    and mqc.object_tree_id = ot.id
 *  limit 10	
 * }}}
 *
 */
case class MasterQuantPeptide(
  var id: Long, // important: master quant component id
  val peptideInstance: Option[PeptideInstance], // without label in the context of isotopic labeling
  var quantPeptideMap: LongMap[QuantPeptide], // QuantPeptide by quant channel id
  var masterQuantPeptideIons: Array[MasterQuantPeptideIon],
  
  var selectionLevel: Int,
  var resultSummaryId: Long,
  var properties: Option[MasterQuantPeptideProperties] = None
  
) extends MasterQuantComponent[QuantPeptide] {
  
  def getQuantComponentMap() = quantPeptideMap
  
  def setQuantComponentMap(quantComponentMap: LongMap[QuantPeptide]) = {
    this.quantPeptideMap = quantComponentMap
  }
  
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
  
  def getBestQuantPeptide: QuantPeptide = this.getMostAbundantQuantComponent()
  
  def getQuantPeptidePepMatchesCount( quantChannelId: Long ): Int = {
    this.getQuantPeptidePepMatchesCount(quantChannelId)
  }
  
  def getQuantPeptideRawAbundance( quantChannelId: Long, applySelectionFilter: Boolean = false ): Float = {
    this.getQuantComponentRawAbundance(quantChannelId, applySelectionFilter)
  }
  
  def getQuantPeptideAbundance( quantChannelId: Long ): Float = {
    this.getQuantComponentAbundance(quantChannelId)
  }
  
  def setAbundancesForQuantChannels( abundances: Seq[Float], quantChannelIds: Seq[Long] ) {
    this.updateOrCreateComponentForQuantChannels(
      abundances,
      quantChannelIds,
      (abundance,qcId) => QuantPeptide(
        rawAbundance = Float.NaN,
        abundance = abundance,
        elutionTime = Float.NaN,
        peptideMatchesCount = 0,
        selectionLevel = 2,
        quantChannelId = qcId
      )
    )
  }
  
  def getRatios( groupSetupNumber: Int ): List[Option[ComputedRatio]] = {
    this.properties.flatMap { mqPepProps =>
      val profileByGSNumberOpt = mqPepProps.getMqPepProfileByGroupSetupNumber
      if( profileByGSNumberOpt.isEmpty ) None
      else {
        val mqPepProfileOpt = profileByGSNumberOpt.get.get(groupSetupNumber)
        mqPepProfileOpt.map(_.getRatios())
      }
    }.getOrElse(List())
  }

}

/** Represents a ProteinSet quantitative data in a single quant channel (run).
 *  
 *  This information is stored in Proline datastore in MSIdb.master_quant_component.object_tree as a JSON 
 *  string.
 * 
 * @see MasterQuantProteinSet
 *
 */
case class QuantProteinSet(
  val rawAbundance: Float,
  var abundance: Float,
  var peptideMatchesCount: Int,
  
  // TODO: fill this value and update the MSIdb
  @JsonDeserialize(contentAs = classOf[java.lang.Integer] )
  var peptidesCount: Option[Int] = None,
  
  val quantChannelId: Long,
  
  // TODO: fill this value
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val proteinSetId: Option[Long] = None,
  
  // TODO: fill this value
  @JsonDeserialize(contentAs = classOf[java.lang.Long] )
  val proteinMatchId: Option[Long] = None,
  
  var selectionLevel: Int
) extends QuantComponent

case class QuantProteinSetProfileObs(
  val quantChannelId: Long,
  val rawAbundance: Float,
  var abundance: Float,
  var peptideMatchesCount: Int
) extends QuantComponent {
  var selectionLevel = 2
}

/** Represents a ProteinSet quantitative information across multiple quant channels (runs). 
 * 
 * This information is stored in Proline datastore in MSIdb.master_quant_component. An example 
 * can be extracted from the datastore by the following SQL Query : 
 * {{{
 *  select * 
 *  from master_quant_component mqc, object_tree ot
 *  where mqc.schema_name like '%protein%' 
 *    and mqc.object_tree_id = ot.id
 *  limit 10	
 * }}}
 *
 */
case class MasterQuantProteinSet(
  val proteinSet: ProteinSet,
  var quantProteinSetMap: LongMap[QuantProteinSet], // QuantProteinSet by quant channel id
  var masterQuantPeptides: Array[MasterQuantPeptide] = null,
     
  var selectionLevel: Int,
  var properties: Option[MasterQuantProteinSetProperties] = None
     
) extends MasterQuantComponent[QuantProteinSet] {
  
  def id() = this.proteinSet.id
  
  def getQuantComponentMap(): LongMap[QuantProteinSet] = quantProteinSetMap
  def setQuantComponentMap(quantComponentMap: LongMap[QuantProteinSet]) = {
    this.quantProteinSetMap = quantComponentMap
  }
  
  def getMasterQuantComponentId() = {
    val mqcId = this.proteinSet.masterQuantComponentId
    require( mqcId != 0, "masterQuantComponentId is not defined" )
    mqcId
  }

  def getSpecificMqPepCount() : Int = {
    if(masterQuantPeptides == null)
      return 0

    masterQuantPeptides.filter( pep =>  pep.isProteinSetSpecific.isDefined && pep.isProteinSetSpecific.get ).length
  }

  def getBestProfile( groupSetupNumber: Int ): Option[MasterQuantProteinSetProfile] = {
    if( properties.isEmpty ) return None
    
    val props = this.properties.get
    if( props.getMqProtSetProfilesByGroupSetupNumber.isEmpty ) return None
    
    val profilesOpt = props.getMqProtSetProfilesByGroupSetupNumber.get.get(groupSetupNumber)
    if( profilesOpt.isEmpty ) return None
    
    var profiles = profilesOpt.get
    if( profiles.length == 0 ) return None
    
    // Check if we have a poor number of peptides
    // TODO: use properties.get.getSelectedMasterQuantPeptideIds.get instead when it is correctly stored
    if ( masterQuantPeptides.length < 5 ) {
      
      // Keep only profiles with highest number of defined ratios
      def countDefinedRatios(profile: MasterQuantProteinSetProfile) = {
        profile.getRatios.count( _.map(_.ratioValue.isNaN == false).getOrElse(false) )
      }
      
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
  
 /* // TODO: move to the MasterQuantProteinSetProfile class when peptideMatchesCount has been updated in the database
  def getProfileQuantComponentMap(profile: MasterQuantProteinSetProfile, qcIds: Seq[Long]): LongMap[QuantComponent] = {
    val rawAbundanceByQcId = qcIds.zip(profile.rawAbundances).toLongMap
    val abundanceByQcId = qcIds.zip(profile.abundances).toLongMap
    
    val quantCompMap = new LongMap[QuantComponent](qcIds.length)
    for( qcId <- qcIds ) {
      val pepMatchesCountOpt = quantProteinSetMap.get(qcId).map(_.peptideMatchesCount)
      
      val quantCompObs = new QuantProteinSetProfileObs(
        quantChannelId = qcId,
        rawAbundance = rawAbundanceByQcId(qcId),
        abundance = abundanceByQcId(qcId),
        // FIXME: tmp workaround because peptideMatchesCount is not filled
        peptideMatchesCount = pepMatchesCountOpt.getOrElse(0)
      )
      
      quantCompMap.put(qcId, quantCompObs)
    }
    
    quantCompMap
  }*/
  
  def setAbundancesForQuantChannels( abundances: Seq[Float], quantChannelIds: Seq[Long] ) {
    this.updateOrCreateComponentForQuantChannels(
      abundances,
      quantChannelIds,
      (abundance,qcId) => QuantProteinSet(
        rawAbundance = Float.NaN,
        abundance = abundance,
        peptideMatchesCount = 0,
        peptidesCount=None,
        proteinSetId = None,
        proteinMatchId = None,
        selectionLevel = 2,
        quantChannelId = qcId
      )
    )
  }

  def setAbundancesAndCountsForQuantChannels(abundances: Seq[Float], psmCounts: Seq[Int],pepCounts: Seq[Int], quantChannelIds: Seq[Long] ) {

    assert(abundances.size.equals(quantChannelIds.size) && psmCounts.size.equals(quantChannelIds.size) && pepCounts.size.equals(quantChannelIds.size))
    val quantCompMap = this.getQuantComponentMap()

    for (index <- 0 until quantChannelIds.length) {
      val qcId = quantChannelIds(index)


      if( quantCompMap.contains(qcId) ) {
        quantCompMap(qcId).abundance = abundances(index)
        quantCompMap(qcId).peptideMatchesCount =  psmCounts(index)
        quantCompMap(qcId).peptidesCount = Some(pepCounts(index))
      } else {
        val qProtSet = QuantProteinSet(
          rawAbundance = Float.NaN,
          abundance = abundances(index),
          peptideMatchesCount = psmCounts(index),
          peptidesCount = Some(pepCounts(index)),
          proteinSetId = None,
          proteinMatchId = None,
          selectionLevel = 2,
          quantChannelId = qcId
        )
        quantCompMap.put(qcId,qProtSet)
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

class LazyQuantResultSummary(
  val lazyResultSummary: LazyResultSummary,
  protected val loadMasterQuantProteinSets: (LazyResultSummary) => Array[MasterQuantProteinSet],
  protected val loadMasterQuantPeptides: (LazyResultSummary) => Array[MasterQuantPeptide],
  protected val loadMasterQuantPeptideIons: (LazyResultSummary) => Array[MasterQuantPeptideIon]
) {
  
  // Shortcut to RSM ID
  def id = lazyResultSummary.id
  
  lazy val masterQuantProteinSets = loadMasterQuantProteinSets(lazyResultSummary)
  lazy val masterQuantPeptides = loadMasterQuantPeptides(lazyResultSummary)
  lazy val masterQuantPeptideIons = loadMasterQuantPeptideIons(lazyResultSummary)
}
  
  
