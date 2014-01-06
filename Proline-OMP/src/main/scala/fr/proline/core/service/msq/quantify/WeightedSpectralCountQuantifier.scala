package fr.proline.core.service.msq.quantify

import com.weiglewilczek.slf4s.Logging
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.DatabaseConnectionContext
import fr.proline.context.IExecutionContext
import fr.proline.core.algo.msq.Ms2CountQuantifier
import fr.proline.core.algo.msq.SpectralCountConfig
import fr.proline.core.om.model.msi.ResultSummary
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.msi.{ ObjectTree => MsiObjectTree }
import fr.proline.core.orm.uds.MasterQuantitationChannel
import javax.persistence.EntityManager
import fr.proline.core.om.model.msi.ProteinSet
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import collection.JavaConversions.iterableAsScalaIterable
import fr.proline.core.algo.msq.spectralcount.PepInstanceFilteringLeafSCUpdater
import fr.proline.core.om.model.msi.PeptideSet
import fr.proline.util.MathUtils

/**
 * @author VDS
 * Merge SpectralCountQuantifier and WeightedSCCalculator
 *
 */
class WeightedSpectralCountQuantifier(
  val executionContext: IExecutionContext,
  val udsMasterQuantChannel: MasterQuantitationChannel,
  val scConfig: SpectralCountConfig
) extends AbstractMasterQuantChannelQuantifier with Logging {

  /**
   *  Spectral Count Result
   *  {RSM ID -> { ProteinMatchAccession -> (Basic SC; Specific SC, Weighted SC)} }
   */

  //  private var _wscByProtMatchAccessionByRSM : scala.collection.mutable.Map[Long, Map[String, SpectralCountsStruct]] = scala.collection.mutable.Map.empty[Long, Map[String, SpectralCountsStruct]]

  def quantifyMasterChannel(): Unit = {

    // Begin new ORM transaction
    msiDbCtx.beginTransaction()
    udsDbCtx.beginTransaction()

    // Store the master quant result set
    val msiQuantResultSet = this.storeMsiQuantResultSet(msiIdentResultSets)
    val quantRsId = msiQuantResultSet.getId()

    // Create corresponding master quant result summary
    val msiQuantRSM = this.storeMsiQuantResultSummary(msiQuantResultSet)
    val quantRsmId = msiQuantRSM.getId

    // Update quant result summary id of the master quant channel
    udsMasterQuantChannel.setQuantResultSummaryId(quantRsmId)
    
    udsEm.persist(udsMasterQuantChannel)
    udsEm.flush()
    
//    logger.debug(" UDS MasterQCh "+udsMasterQuantChannel.getName()+" ; "+udsMasterQuantChannel.getId()+" -  QRSMID "+udsMasterQuantChannel.getQuantResultSummaryId())
//    for(qCh <- udsMasterQuantChannel.getQuantitationChannels()){
//      logger.debug(" UDS MasterQCh /QCh"+qCh.getId())  
//    }
    
    
    // Store master quant result summary
    this.storeMasterQuantResultSummary(this.mergedResultSummary, msiQuantRSM, msiQuantResultSet)

    // -- Create ProteinPepsWeightStruct from reference RSM
    val proteinSetWeightStructsById = createProteinPepsWeightStructs(true)

    // Compute master quant peptides
    val (mqPeptides, mqProtSets) = computeMasterQuantPeptides(
      udsMasterQuantChannel,
      this.mergedResultSummary,
      this.identResultSummaries,
      proteinSetWeightStructsById
    )
   
    this.logger.info("storing "+mqPeptides.size+" master peptide quant data...")

    // Iterate over master quant peptides to store corresponding spectral counts
    for (mqPeptide <- mqPeptides) {

      //val peptideId = mqPeptide.peptideInstance.get.peptideId
      //val masterPepInst = this.masterPepInstByPepId( peptideId )
      //val msiMasterPepInst = this.msiMasterPepInstById(masterPepInst.id)
      val msiMasterPepInst = this.msiMasterPepInstById(mqPeptide.peptideInstance.get.id)
      this.storeMasterQuantPeptide(mqPeptide, msiQuantRSM, Some(msiMasterPepInst))
    }

    this.logger.info("storing "+mqProtSets.size+" master proteins set quant data...")

    // Iterate over master quant protein sets to store corresponding spectral counts
    for (mqProtSet <- mqProtSets) {
      val msiMasterProtSet = this.msiMasterProtSetById(mqProtSet.proteinSet.id)
      this.storeMasterQuantProteinSet(mqProtSet, msiMasterProtSet, msiQuantRSM)
    }

    // Commit ORM transaction
    msiDbCtx.commitTransaction()
    udsDbCtx.commitTransaction()

  }

  private def createProteinPepsWeightStructs(referenceForPeptides: Boolean): Map[Long, ProteinPepsWeightStruct] = {

    //ProteinPepsWeightStruct for each RSM ProteinSet referenced by ProteinSet id  
    val proteinPepsWeightStructsByProtSetId = Map.newBuilder[Long, ProteinPepsWeightStruct]

    // Map each peptide to the list of identified ProteinSet id 
    val protSetIdByPepId = new HashMap[Long, ArrayBuffer[Long]]()

    //****  For each ProteinSet, initialize a ProteinPepsWeightStruct and create Maps
    mergedResultSummary.proteinSets.foreach(protSet => {

      //Map of weight by peptide Id
      val weightByPepId = new HashMap[Long, Float]()

      //-- Get Typical Protein Match Accession 
      var pmAccession: String = null
      if (protSet.getTypicalProteinMatch != null && protSet.getTypicalProteinMatch.isDefined) {
        pmAccession = protSet.getTypicalProteinMatch.get.accession
      } else {
        val typicalPM = msiEm.find(classOf[fr.proline.core.orm.msi.ProteinMatch], protSet.getTypicalProteinMatchId)
        pmAccession = typicalPM.getAccession()

      }

      //-- Get peptide specific count and create Map : peptide => List ProtSet.Id identified by peptide
      var nbrPepSpecif: Int = 0
      if (referenceForPeptides) {
        protSet.peptideSet.getPeptideInstances.foreach(pepI => {
          val proSetIds = protSetIdByPepId.getOrElseUpdate(pepI.peptideId, new ArrayBuffer[Long])
          proSetIds += protSet.id
          if (pepI.validatedProteinSetsCount == 1) {
            nbrPepSpecif += 1
          }
          weightByPepId += pepI.peptideId -> 0.0f
        })
      }

      proteinPepsWeightStructsByProtSetId += protSet.id -> new ProteinPepsWeightStruct(proteinSet = protSet, typicalPMAcc = pmAccession, nbrPepSpecific = nbrPepSpecif, weightByPeptideId = weightByPepId)

    }) // End ProteinPepsWeightStruct initialization 

    val resultStruct: Map[Long, ProteinPepsWeightStruct] = proteinPepsWeightStructsByProtSetId.result

    //**** Compute Peptides Weight if referenceRSM also used for peptide
    if (referenceForPeptides) computePeptideWeight(resultStruct, protSetIdByPepId)

    resultStruct
  }

  /**
   * Compute Peptide's Weight for each identified ProteinSet
   *
   *  If peptide is a specific  ProteinSet, the corresponding weight will be 1
   *  Else if peptide is shared between multiple ProteinSets the weight = # specific pep of ProtSet / Sum ( #specific pep of all ProtSet identified by this pep)
   *
   *  @param  proteinWeightStructByProtSetId Map ProteinPepsWeightStruct by ProteinSetId in peptide reference RSM. ProteinPepsWeightStruct should be updated
   *  @param  protSetIdByPep For each Peptide (id) references list of ProteinSet (Id) identified by this peptide
   */
  private def computePeptideWeight(proteinWeightStructByProtSetId: Map[Long, ProteinPepsWeightStruct], protSetIdByPep: HashMap[Long, ArrayBuffer[Long]]): Unit = {

    proteinWeightStructByProtSetId.foreach(entry => {
      val currentProteinWeightStruct = entry._2
      //Calculate  weight for each peptide identifying this ProteinSet
      currentProteinWeightStruct.weightByPeptideId.foreach(weightMapEntry => {
        val pepId = weightMapEntry._1
        if (protSetIdByPep.get(pepId).get.length == 1) { // specific peptide, weight =1
          currentProteinWeightStruct.weightByPeptideId(pepId) = 1.0f
        } else {
          //Not specific peptide,  weight = nbr pepSpecific of current ProtSet / Sum ( nbr pepSpecific of all ProtSet identified by this pep)
          var sumNbrSpecificPeptides = 0
          protSetIdByPep.get(pepId).get.foreach(protSetId => {
            sumNbrSpecificPeptides += proteinWeightStructByProtSetId.get(protSetId).get.nbrPepSpecific
          })

          if (sumNbrSpecificPeptides > 0)
            currentProteinWeightStruct.weightByPeptideId.put(pepId, (currentProteinWeightStruct.nbrPepSpecific.toFloat / sumNbrSpecificPeptides.toFloat))
          else
            currentProteinWeightStruct.weightByPeptideId.put(pepId, 0)
        }
      }) //End go through ProteinSet Peptides
    }) // End go through  ProteinSet (ProteinPepsWeightStruct)      
  }

  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingPeptidesSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.spectral_counting_peptides")
  }

  protected def buildMasterQuantPeptideObjectTree(mqPep: MasterQuantPeptide): MsiObjectTree = {

    val quantPeptideMap = mqPep.quantPeptideMap
    val quantPeptides = this.quantChannelIds.map { quantPeptideMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQPepObjectTree = new MsiObjectTree()
    msiMQPepObjectTree.setSchema(spectralCountingPeptidesSchema)
    msiMQPepObjectTree.setClobData(ProfiJson.serialize(quantPeptides))

    msiMQPepObjectTree
  }

  // TODO: create enumeration of schema names (in ObjectTreeSchema ORM Entity)
  protected lazy val spectralCountingQuantPepIonsSchema = {
    this.loadOrCreateObjectTreeSchema("object_tree.spectral_counting_quant_peptide_ions")
  }

  protected def buildMasterQuantPeptideIonObjectTree(mqPepIon: MasterQuantPeptideIon): MsiObjectTree = {

    val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
    val quantPeptideIons = this.quantChannelIds.map { quantPeptideIonMap.getOrElse(_, null) }

    // Store the object tree
    val msiMQPepIonObjectTree = new MsiObjectTree()
    msiMQPepIonObjectTree.setSchema(spectralCountingQuantPepIonsSchema)
    msiMQPepIonObjectTree.setClobData(ProfiJson.serialize(quantPeptideIons))

    msiMQPepIonObjectTree
  }

  protected def getMergedResultSummary(msiDbCtx: DatabaseConnectionContext): ResultSummary = {
    if (scConfig.parentRSMId.isEmpty)
      createMergedResultSummary(msiDbCtx)
    else {
      this.logger.debug("Read Merged RSM with ID " + scConfig.parentRSMId.get)

      // Instantiate a RSM provider
      val rsmProvider = new SQLResultSummaryProvider(msiDbCtx = msiDbCtx, psDbCtx = psDbCtx, udsDbCtx = null)
      rsmProvider.getResultSummary(scConfig.parentRSMId.get, true).get
    }

  }

  /**
   * Compute BSC and SSC for each peptide instance of each resultsummary and store information in
   * QuantPeptideIon and MasterQuantPeptide
   * Compute WSC for each ref RSM typical protein in each rsm and store information in
   * QuantProteinSet and MasterQuantProteinSet
   *
   * @param mergedRSM : RSM de départ et pas celui de quanti ? TODO
   */
  def computeMasterQuantPeptides(udsMasterQuantChannel: MasterQuantitationChannel,
                                 mergedRSM: ResultSummary,
                                 resultSummaries: Seq[ResultSummary],
                                 protSetWeightStructsByProtSetId: Map[Long, ProteinPepsWeightStruct]): (Array[MasterQuantPeptide], Array[MasterQuantProteinSet]) = {

    val rsIdByRsmId = resultSummaries.map(rsm => { rsm.getResultSetId -> rsm.id }).toMap

    // Map quant channel id by result set id    
    val qcIdByRsId = udsMasterQuantChannel.getQuantitationChannels().map {
      qc => rsIdByRsmId(qc.getIdentResultSummaryId()) -> qc.getId
    } toMap

    val refPepInstanceByPepId = mergedRSM.peptideInstances.map(pi => pi.peptideId -> pi).toMap

    //     val qPepIonsMapsByrsmId = new HashMap[Long,Map[Long, Array[QuantPeptideIon]]]
    val forMasterQPepByPepId = new HashMap[Long, scala.collection.mutable.Map[Long, QuantPeptide]]
    val forMasterQProtSetByProtSet = new HashMap[ProteinSet, scala.collection.mutable.Map[Long, QuantProteinSet]]

    // returnesValues
    val mqPeptides = new ArrayBuffer[MasterQuantPeptide]
    val mqProtSets = new ArrayBuffer[MasterQuantProteinSet]

    // Compute SpectralCount for each RSM
    resultSummaries.foreach(rsm => {

      val qcId = qcIdByRsId(rsm.getResultSetId)

       val quantPepByPepID: scala.collection.mutable.Map[Long, QuantPeptide] = scala.collection.mutable.Map[Long, QuantPeptide]()


      //--- Update RSM SpectralCount if necessary
      // TODO FIXME Assume first peptideInstance.totalLeavesMatchCount give global information ! Should be wrong see issue #7984
      if (rsm.peptideInstances(0).totalLeavesMatchCount < 0) {
        PepInstanceFilteringLeafSCUpdater.updatePepInstanceSC(rsm, executionContext)
        rsm.peptideInstances.foreach(pepI =>{
        	val ormPepInst  = this.msiEm.find(classOf[fr.proline.core.orm.msi.PeptideInstance], pepI.id)
        	ormPepInst.setTotalLeavesMatchCount(pepI.totalLeavesMatchCount)
        })

      }

      //--- Get RSM Peptide Match/Protein Match information 	     
      // map   list of ProtMatch accession by PeptideSet
      val pepSetByPMAccList: Map[PeptideSet, Seq[String]] = createProtMatchesAccByPeptideSet(rsm)

      //--- Calculate SCs for each Ref RSM ProtSet
      protSetWeightStructsByProtSetId.foreach(entry => {

	    val currentProteinSetWeightStruct = entry._2

        //Get PeptideSet containing protein in current RSM if exist
        var peptideSetForPM: PeptideSet = null
        val pepSetIt = pepSetByPMAccList.iterator
        while (pepSetIt.hasNext && peptideSetForPM == null) {
          val nextEntry: (PeptideSet, Seq[String]) = pepSetIt.next
          if (nextEntry._2.contains(currentProteinSetWeightStruct.typicalPMAcc))
            peptideSetForPM = nextEntry._1
        }

        // ProteinMatch Spectral Count
        var protWSC: Float = 0.0f
        var protSSC: Float = 0.0f
        var protBSC: Int = 0

        if (peptideSetForPM != null) { //  Ref RSM current typical found

          //Go through peptides instances,  compute SC and create QuantPeptide
          peptideSetForPM.getPeptideInstances.foreach(pepInst => {

            val weight = currentProteinSetWeightStruct.weightByPeptideId.get(pepInst.peptideId).get
            val isPepSpecific = Math.abs(weight - 1.0f) < MathUtils.EPSILON_FLOAT
            val qPep = if (quantPepByPepID.contains(pepInst.peptideId)) {
              quantPepByPepID(pepInst.peptideId)
            } else {
              //FIXME VDS : OK if we use weight on specificity... Maybe this information (specific or not) should be saved in  ProteinPepsWeightStruct
              val ssc = if (isPepSpecific) { pepInst.totalLeavesMatchCount } else { 0 }
              val qp = new QuantPeptide(
                rawAbundance = ssc,
                abundance = ssc,
                elutionTime = 0,
                peptideMatchesCount = pepInst.totalLeavesMatchCount,
                quantChannelId = qcId,
                peptideId = Some(pepInst.peptideId),
                peptideInstanceId = Some(pepInst.id),
                selectionLevel = 2
              )
              //Update rsm specific map
              quantPepByPepID.put(pepInst.peptideId, qp)
              
              //Update complete Map to be used for MasterQuantPeptide creation
              forMasterQPepByPepId.getOrElseUpdate(pepInst.peptideId, new HashMap[Long, QuantPeptide]()).put(qcId, qp)

              qp
            }

            protBSC += qPep.peptideMatchesCount
            if (isPepSpecific)
              protSSC += qPep.rawAbundance
            protWSC += (qPep.peptideMatchesCount.toFloat * weight)
          }) //End go through PeptideInstance of ProtSet's PeptideSet

          val quantProteinSet = new QuantProteinSet(
            rawAbundance = protSSC,
            abundance = protWSC,
            peptideMatchesCount = protBSC,
            quantChannelId = qcId,
            selectionLevel = 2
          )

          //Update complete Map to be used for MasterQuantProtei	nSet creation
          forMasterQProtSetByProtSet.getOrElseUpdate(currentProteinSetWeightStruct.proteinSet, new HashMap[Long, QuantProteinSet]()).put(qcId, quantProteinSet)

        } //End Protein identified in current RSM
        else {
          logger.debug("Protein "+currentProteinSetWeightStruct.typicalPMAcc+" Not identified in RSM id="+rsm.id)
        }

      }) // End go through  proteinSetWeightStructsById

    }) //End go through RSMs 

    //Create MasterQuant Object
    forMasterQPepByPepId.foreach(entry => {
      mqPeptides += new MasterQuantPeptide(
        id = MasterQuantPeptide.generateNewId,
        peptideInstance = Some(refPepInstanceByPepId(entry._1)),
        quantPeptideMap = entry._2.toMap,
        masterQuantPeptideIons = Array.empty[MasterQuantPeptideIon],
        selectionLevel = 2,
        resultSummaryId = udsMasterQuantChannel.getQuantResultSummaryId()
      )
    })

    forMasterQProtSetByProtSet.foreach(entry => {
      mqProtSets += new MasterQuantProteinSet(
        proteinSet = entry._1,
        quantProteinSetMap = entry._2.toMap,
        selectionLevel = 2
      )
    })

    return (mqPeptides.toArray,mqProtSets.toArray)
  }

  private def createProtMatchesAccByPeptideSet(rsm: ResultSummary): Map[PeptideSet, Seq[String]] = {
    val rs = rsm.resultSet.get
    val protMById = rs.proteinMatchById
    val result = scala.collection.mutable.Map[PeptideSet, Seq[String]]()

    rsm.peptideSets.foreach(pepSet => {
      val seqBuilder = Seq.newBuilder[String]
      pepSet.proteinMatchIds.foreach(pmId => {

        seqBuilder += protMById(pmId).accession
      })
      result.put(pepSet, seqBuilder.result)
    })

    result.toMap
  }

}

case class ProteinPepsWeightStruct(
  val proteinSet: ProteinSet,
  val typicalPMAcc: String,
  val nbrPepSpecific: Int,
  val weightByPeptideId: scala.collection.mutable.Map[Long, Float] = null
)
