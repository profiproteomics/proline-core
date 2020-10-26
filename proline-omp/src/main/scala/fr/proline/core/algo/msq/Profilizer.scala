package fr.proline.core.algo.msq

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.math.median
import fr.profi.util.primitives.isZeroOrNaN
import fr.proline.core.algo.msq.config.profilizer.{MqPeptideAbundanceSummarizingMethod, MissingAbundancesInferenceMethod, MqPeptidesClusteringMethod, MqPeptidesSelectionConfig, MqPeptidesSelectionMethod, PostProcessingConfig, ProfilizerStatConfig, QuantComponentItem, RazorStrategyMethod}
import fr.proline.core.algo.msq.profilizer._
import fr.proline.core.algo.msq.profilizer.filtering._
import fr.proline.core.algo.msq.summarizing.BuildMasterQuantPeptide
import fr.proline.core.om.model.SelectionLevel
import fr.proline.core.om.model.msq.MasterQuantComponent
import fr.proline.core.om.model.msq._
import org.apache.commons.math3.stat.StatUtils

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
 * Analyze profiles of Master Quant Peptides and Master Quant Protein sets
 */
class Profilizer( expDesign: ExperimentalDesign, groupSetupNumber: Int = 1, masterQCNumber: Int = 1 ) extends LazyLogging {
  
  val errorModelBinsCount = 1
  
  private val expDesignSetup = ExperimentalDesignSetup(expDesign, groupSetupNumber, masterQCNumber)
  private var minAbundanceByQcIds = Array.emptyFloatArray
  
  /**
   * Computes MasterQuantPeptide profiles.
   */
  def computeMasterQuantPeptideProfiles( masterQuantPeptides: Seq[MasterQuantPeptide], config: PostProcessingConfig ) {
    require( masterQuantPeptides.length >= 10, "at least 10 peptides are required for profile analysis")
    
    logger.info("computing master quant peptide profiles...")

    // --- Reset some values ---
    for( mqPep <- masterQuantPeptides ) {
              
      val mqPepPropsOpt = mqPep.properties
      if(mqPepPropsOpt.isEmpty) {
        mqPep.properties = Some(MasterQuantPeptideProperties())
      } else {
        // Reset quant profiles for this masterQuantPeptide
        val mqPepProps = mqPepPropsOpt.get
        mqPepProps.setMqPepProfileByGroupSetupNumber(None)
       // mqPepProps.setMqPepIonAbundanceSummarizingConfig(None) //VDS don't reset this properties. It's not set again if not in peptideIon Based
      }
    }
    
    // --- Apply protein set specific filter if requested ---
    if( config.peptidesSelectionMethod == MqPeptidesSelectionMethod.SPECIFIC ) {
      UnspecificPeptideFilterer.discardPeptides(masterQuantPeptides)
    }
    
    // --- Apply MC filter if requested ---
    if( config.discardMissCleavedPeptides ) {
      require( config.missCleavedPeptideFilteringMethod.isDefined, "config.missCleavedPeptideFilteringMethod is empty")
      MissCleavedPeptideFilterer.discardPeptides(masterQuantPeptides, config.missCleavedPeptideFilteringMethod.get)
    }
    
    // --- Apply Oxidation filter if requested ---
    if(config.discardModifiedPeptides) {
      require( config.modifiedPeptideFilteringMethod.isDefined , "config.modifiedPeptideFilteringMethod is empty")
      require( config.modifiedPeptideFilterConfig.isDefined && !(config.modifiedPeptideFilterConfig.get.ptmDefinitionIdsToDiscard.isEmpty && !config.modifiedPeptideFilterConfig.get.ptmPattern.isDefined), "No modifications specified for discard Modified peptide")
      ModifiedPeptideFilter.discardPeptides(masterQuantPeptides, config.modifiedPeptideFilteringMethod.get, config.modifiedPeptideFilterConfig.get)
    }

    // Keep master quant peptides passing all filters (i.e. have a selection level higher than 1)
    //
    // val( mqPepsAfterAllFilters, deselectedMqPeps ) = masterQuantPeptides.partition( _.selectionLevel >= 2 )
    //
    // FIXME: DBO => should we work only with filtered mqPeps ?
    // WARN: CBy => selectionLevel < 2 are kept, this means that normalization & inference are also done on deselected masterQuantComponents
    
     val mqPepsAfterAllFilters = {
        if (config.peptideAbundanceSummarizerConfig.isDefined && config.peptideAbundanceSummarizerConfig.get.peptideSummarizingBasedOn.get == QuantComponentItem.QUANT_PEPTIDES.toString) {
          masterQuantPeptides.asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]
        } else {
          masterQuantPeptides.flatMap(_.masterQuantPeptideIons).asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]
        }
      }  //was -> val mqPepsAfterAllFilters = masterQuantPeptides
    
    
    //
    // Reset quant peptide abundance of deselected master quant peptides
    // DBO: is this useful ???
    //
    // for( mqPep <- deselectedMqPeps; (qcid,qPep) <- mqPep.quantPeptideMap ) {
    //  qPep.abundance = Float.NaN
    // }
    //
     
    // --- Compute the PSM count matrix ---
    val psmCountMatrix = mqPepsAfterAllFilters.map( _.getPepMatchesCountsForQuantChannels(expDesignSetup.qcIds) ).toArray
    
    // --- Compute the abundance matrix ---
    val rawAbundanceMatrix: Array[Array[Float]] = mqPepsAfterAllFilters.map( _.getRawAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    //
    // TODO modify this to take into account PEPTIDE or ION
    //
    //
    val normalizedMatrix = if( !config.peptideStatConfig.applyNormalization ) rawAbundanceMatrix else AbundanceNormalizer.normalizeAbundances(rawAbundanceMatrix)
    
    require( normalizedMatrix.length == rawAbundanceMatrix.length, "error during normalization, some peptides were lost...")
 
    // Compute absolute error model and the filled matrix (only if config.peptideStatConfig.applyMissValInference == true)
    //
    val absoluteErrorModelOpt = this.computeAbsoluteErrorModel(normalizedMatrix)
    
    val filledMatrix = this.inferMissingValues(
      normalizedMatrix,
      psmCountMatrix,
      absoluteErrorModelOpt,
      config.peptideStatConfig
    )
    
    minAbundanceByQcIds = filledMatrix.transpose.map { abundanceCol =>
       val abundanceForAQch =  abundanceCol.filter( !isZeroOrNaN(_))
       if(abundanceForAQch.isEmpty)
         Float.NaN
       else
         abundanceForAQch.reduceLeft(_ min _)
    }
     
    // Update master quant component abundances after normalization and missing values inference
    //
    
    for( (mqQuantComponent, abundances) <- mqPepsAfterAllFilters.zip(filledMatrix) ) {
      mqQuantComponent.setAbundancesForQuantChannels(abundances,expDesignSetup.qcIds)
    }

     if (config.isMqPeptideAbundanceSummarizerBasedOn(QuantComponentItem.QUANT_PEPTIDE_IONS)) {
      //
      // if previous step is ION based, then update the associated mqPep Abundance values
      //
      for (mqPep <- masterQuantPeptides) {
        val mqPepIons = mqPep.masterQuantPeptideIons
        // Re-build the master quant peptides
        val newMqPep = BuildMasterQuantPeptide(mqPepIons, mqPep.peptideInstance, mqPep.resultSummaryId, config.pepIonAbundanceSummarizingMethod)
        val abundances = newMqPep.getAbundancesForQuantChannels(expDesignSetup.qcIds)
        mqPep.setAbundancesForQuantChannels(abundances, expDesignSetup.qcIds)
        //Get properties back
        mqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig= newMqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig
        // the next step is mandatory since BuildMasterQuantPeptide updates mqPepIons.masterQuantPeptideId to the new MasterQuantPeptide
        mqPepIons.foreach { mqPepIon =>
          mqPepIon.masterQuantPeptideId = mqPep.id
        }
      }
    }
    
    //
    // Compute masterQuantPeptide ratios and profiles
    //
    
    // Define some mappings
    val mqPepById = new mutable.HashMap[Long,MasterQuantPeptide]()
    mqPepById.sizeHint(masterQuantPeptides.length)
    val ratiosByMQPepId = new mutable.HashMap[Long,ArrayBuffer[Option[ComputedRatio]]]()
    ratiosByMQPepId.sizeHint(masterQuantPeptides.length)
    
    // Compute these mappings
    for( mqPep <- masterQuantPeptides ) {
      mqPepById += mqPep.id -> mqPep
      ratiosByMQPepId += mqPep.id -> new ArrayBuffer[Option[ComputedRatio]]
    }
    
    val maxCv = config.peptideStatConfig.maxCv
    
    val mqPeptideFilledMatrix = masterQuantPeptides.map( _.getAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray 
    val mqPeptidePsmCountMatrix = masterQuantPeptides.map( _.getPepMatchesCountsForQuantChannels(expDesignSetup.qcIds) ).toArray
    
    // Iterate over the ratio definitions
    for ( ratioDef <- expDesignSetup.groupSetup.ratioDefinitions ) {

      val ratios = computeRatios(
        ratioDef,
        mqPeptideFilledMatrix,
        mqPeptidePsmCountMatrix,
        absoluteErrorModelOpt,
        config.peptideStatConfig
      )
 
      for ( ratio <- ratios ) {
        val index = ratio.entityId.toInt
        val masterQuantPep = masterQuantPeptides(index)
        val abundances = mqPeptideFilledMatrix(index)
        
        // Update master quant peptide abundances
        masterQuantPep.setAbundancesForQuantChannels(abundances,expDesignSetup.qcIds)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numerator.toFloat,
          denominator = ratio.denominator.toFloat,
          //numeratorCv = ratio.numeratorSummary.getCv().toFloat,
          //denominatorCv = ratio.denominatorSummary.getCv().toFloat,
          state = ratio.state.map(_.id).getOrElse(AbundanceRatioState.Invariant.id),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue,
          zScore = ratio.zScore
        )
        
        // TODO: compute these statistics independently from ratios ???
        def isCvTooHigh(summary: ExtendedStatisticalSummary): Boolean = {
          if( maxCv.isEmpty ) return false
          if( summary.getN > 2 && summary.getCv() > maxCv.get) true else false
        }
        if( isCvTooHigh(ratio.numeratorSummary) || isCvTooHigh(ratio.denominatorSummary) ) {
          if( masterQuantPep.selectionLevel == 2 ) {
            masterQuantPep.selectionLevel = 1
            masterQuantPep.properties.get.setDiscardingReason( Some("CV is too high") )
          }
        }
        
        ratiosByMQPepId(masterQuantPep.id) += Some(computedRatio)
      }
    }
    
    // Update the MasterQuantPeptide profiles using the obtained results
    for ( (mqPepId,ratios) <- ratiosByMQPepId ) {

      val mqPep = mqPepById(mqPepId)
      val mqPepProps = mqPep.properties.getOrElse( MasterQuantPeptideProperties() )
      
      val quantProfile = MasterQuantPeptideProfile(ratios = ratios.toList)
      val mqPeptProfileMap = mqPepProps.getMqPepProfileByGroupSetupNumber().getOrElse( mutable.HashMap() )
      mqPeptProfileMap += ( groupSetupNumber -> quantProfile )
      mqPepProps.setMqPepProfileByGroupSetupNumber( Some(mqPeptProfileMap) )
      
      mqPep.properties = Some(mqPepProps)
    }
    
    logger.info("After computeMasterQuantPeptideProfiles mqPep with selection level == 1 : "+masterQuantPeptides.count(_.selectionLevel == 1))
    logger.info("After computeMasterQuantPeptideProfiles mqPep with selection level == 2 : "+masterQuantPeptides.count(_.selectionLevel == 2))

    ()
  }
  
  def computeMasterQuantProtSetProfiles( masterQuantProtSets: Seq[MasterQuantProteinSet], config: PostProcessingConfig) {
    require( masterQuantProtSets.length >= 10, "at least 10 protein sets are required for profile analysis")//VDS : depends on config ??
    
    logger.info("computing master quant protein set profiles...")
    
    val qcsSampleNum = expDesignSetup.quantChannels.groupBy(_.sampleNumber)
    val bgBySampleNum = expDesignSetup.groupSetup.biologicalGroups.seq.view.flatMap { bg =>
      bg.sampleNumbers.map { sampleNumber =>
        sampleNumber -> bg
      }
    }.toMap
    val bgByQcIdx = expDesignSetup.quantChannels.map( qc => bgBySampleNum(qc.sampleNumber) )

    val useRazor = config.peptidesSelectionMethod.equals(MqPeptidesSelectionMethod.RAZOR_AND_SPECIFIC)
    val mqProtSetById = if(useRazor) masterQuantProtSets.map(mqprot => mqprot.id() -> mqprot).toMap  else Map.empty[Long,MasterQuantProteinSet]
    val isPepSpecificBymqPepId = mutable.LongMap.empty[Boolean]
    val nbrSpecificMqPepByMqProtSetId : mutable.LongMap[Int] =  mutable.LongMap.empty[Int]
    if(useRazor) {
      masterQuantProtSets.foreach( mqProt => {
        if(mqProt.proteinSet.isValidated){
          var nbSpecificPep = 0
          mqProt.masterQuantPeptides.foreach( mqPep =>{
            if(isPepSpecificBymqPepId.contains(mqPep.id)) {
              if(isPepSpecificBymqPepId(mqPep.id)) //should not occur !
                nbSpecificPep += 1
            } else {
              var nbProtSet = 0
              if (mqPep.getMasterQuantProteinSetIds().isDefined) {
                mqPep.getMasterQuantProteinSetIds().get.foreach(mqProtId => {
                  if (mqProtSetById(mqProtId).proteinSet.isValidated)
                    nbProtSet += 1
                })
              }
              if (nbProtSet == 1) {
                nbSpecificPep += 1
                isPepSpecificBymqPepId += mqPep.id -> true
              } else
                isPepSpecificBymqPepId += mqPep.id -> false
            }
          })//End go through protein set's peptides
          nbrSpecificMqPepByMqProtSetId += mqProt.id() -> nbSpecificPep
        } // Process only valid Protein Set
      }) //end go through proteinSet
    }

    val assignedMqPepIdToProteSet = new mutable.LongMap[Long]()

    // Compute the intersection of filtered masterQuantPeptides and mqPeptideSelLevelById
    for (mqProtSet <- masterQuantProtSets) {
      val mqPepById: mutable.LongMap[MasterQuantPeptide] = if(useRazor) mqProtSet.masterQuantPeptides.toLongMapWith(a => a.id -> a) else mutable.LongMap.empty
      val datasetSelLvlMap: mutable.LongMap[Int] = mqProtSet.masterQuantPeptides.toLongMapWith(a => a.id -> a.selectionLevel)
      val protSetSelLvlMap: mutable.HashMap[Long, Int] = mqProtSet.properties.get.getSelectionLevelByMqPeptideId().getOrElse({
        // Note: this default map construction is kept for backward compatibility,
        // it should be now computed during the quantitation phase
        val defaultSelLvlMap: mutable.HashMap[Long, Int] = datasetSelLvlMap.map{case (k ,v) =>
          val newSelectionLevel = if (SelectionLevel.isSelected(v)) SelectionLevel.SELECTED_AUTO else SelectionLevel.DESELECTED_AUTO
          k -> newSelectionLevel 
          }(collection.breakOut)
        defaultSelLvlMap
      })

      // Update protein set selection level only if not set manually
      for (
        (mqPepId, protSetSelLvl) <- protSetSelLvlMap;
        if (SelectionLevel.isSetAutomatically(protSetSelLvl))
      ) {
        val datasetSelLvl = datasetSelLvlMap(mqPepId)

        // Compute new automatic selection level
        var newAutoSelLvl = if (SelectionLevel.isSelected(datasetSelLvl)) SelectionLevel.SELECTED_AUTO
        else SelectionLevel.DESELECTED_AUTO

        // If peptide selection method is RAZOR and peptide is selected, update selection for protein set using razor rules
        // rule for MOST_SPECIFIC_PEP_SELECTION: assign peptide to protein set with max nbr specific peptide and if equality choose
        // protein set with higher score (--> to change to protein set with higher abundance through quantchannels
        if(newAutoSelLvl == SelectionLevel.SELECTED_AUTO && useRazor) {
          // verify if this peptide has already been seen
          if(assignedMqPepIdToProteSet.contains(mqPepId)) {
            if( !assignedMqPepIdToProteSet(mqPepId).equals(mqProtSet.id)) { //mqpep should not be assign to this protein
              newAutoSelLvl = SelectionLevel.DESELECTED_AUTO
            }
          } else { //mqPep not already seen, get choosen proteinset for it
/*          // to uncomment when more than one method...
            val mqSelectionConfig: MqPeptidesSelectionConfig = config.peptidesSelectionConfig.getOrElse(MqPeptidesSelectionConfig)
            mqSelectionConfig.razorStrategyMethod match {
              case RazorStrategyMethod.MOST_SPECIFIC_PEP_SELECTION
            }*/

            val mqpep = mqPepById(mqPepId)
            if (isPepSpecificBymqPepId.contains(mqpep.id)  || (mqpep.isProteinSetSpecific.isDefined && !mqpep.isProteinSetSpecific.get) ) {
              logger.trace("-------- RAZOR TEST on mqpep Id "+mqpep.id)
              // masterQuantPeptide is shared between proteinSet. Assign to only one regarding RAZOR RULE
              //-- TODO If razor config is MOST_SPECIFIC_PEP_SELECTION
              val mqProtIds = mqpep.getMasterQuantProteinSetIds().get // if isProteinSetSpecific defined getMasterQuantProteinSetIds should also...
              val nbrSpecPepByProtSetId = mutable.LongMap.empty[Int]
              mqProtIds.map(prId =>
                { nbrSpecPepByProtSetId += (prId -> nbrSpecificMqPepByMqProtSetId.getOrElse(prId,0)) }
              )
              val maxNbrSp = nbrSpecPepByProtSetId.maxBy(_._2)._2
              logger.trace("-------- RAZOR max number of specific pep  =" +maxNbrSp)
              val pepProtWithMaxSp = nbrSpecPepByProtSetId.filter(_._2 == maxNbrSp)
              var chosenProtSet = mqProtSet.id()
              if (pepProtWithMaxSp.size > 1) {
                logger.trace("-------- RAZOR more than one with max nbr sp. pep: "+pepProtWithMaxSp.size)
                //getProtSet with higher score ... //VDS TODO: use sum abundance for all quantChannels
                val max = pepProtWithMaxSp.maxBy(entry => mqProtSetById(entry._1).proteinSet.peptideSet.score)
                if (!mqProtSet.id().equals(max._1)) { // This is not proteinset to associate peptide to
                  //Max protSet is not this protein set ... save information, and set to deselected for this prot
                  chosenProtSet = max._1
                  logger.trace("-------- RAZOR chosen prot set: "+chosenProtSet+"(not current)")
                  newAutoSelLvl = SelectionLevel.DESELECTED_AUTO
                } else { // else it is this protein set.  chosenProtSet and selection level already OK
                  logger.trace("-------- RAZOR chosen prot set is current "+chosenProtSet)
                }

              } else { //only one protein set with max nbr specific pep
                logger.trace("-------- RAZOR one prot set with max sp. pep")
                val maxEntry = pepProtWithMaxSp.head
                if (!mqProtSet.id().equals(maxEntry._1)) {
                  chosenProtSet = pepProtWithMaxSp.head._1 //save information of which proteinset pep should be assign to and set to deselected for this prot
                  logger.trace("-------- RAZOR chosen prot set "+chosenProtSet+" (not current)")
                  newAutoSelLvl = SelectionLevel.DESELECTED_AUTO
                } else {
                  logger.trace("-------- RAZOR chosen prot set is current "+chosenProtSet)
                }
              }

              assignedMqPepIdToProteSet.put(mqPepId, chosenProtSet) //save information for next time peptide will be seen

            }  // else mqPep is specific to protein set, keep selected. No need to put in assignedMqPepIdToProteSet. Should be seen once !

          }
        }


        if (protSetSelLvl != newAutoSelLvl) {
          protSetSelLvlMap.update(mqPepId, newAutoSelLvl)
        }
      }
      
      mqProtSet.properties.get.setSelectionLevelByMqPeptideId(Some(protSetSelLvlMap))
    }

    // Clusterize MasterQuantPeptides according to the provided method
    val clusteringMethodName = if( config.applyProfileClustering ) config.profileClusteringMethod.get.toString
    else MqPeptidesClusteringMethod.PEPTIDE_SET.toString

    val mqPepsClusters = MasterQuantPeptidesClusterer.computeMqPeptidesClusters(
      clusteringMethodName,
      config.profileClustererConfig,
      groupSetupNumber = 1,
      masterQuantProtSets
    )
    
    // --- Iterate over MQ peptides clusters to summarize corresponding abundance matrix ---
   // val rawAbundanceMatrixBuffer = new ArrayBuffer[Array[Float]](mqPepsClusters.length)
    val rawAbundancesByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Float]]
    rawAbundancesByProfileClusterBuilder.sizeHint(mqPepsClusters.length)
    val abundanceMatrixBuffer = new ArrayBuffer[Array[Float]](mqPepsClusters.length)
    val psmCountMatrixBuffer = new ArrayBuffer[Array[Int]](mqPepsClusters.length)
    val psmCountsByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Int]]
    psmCountsByProfileClusterBuilder.sizeHint(mqPepsClusters.length)
    val pepCountsByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Int]]
    pepCountsByProfileClusterBuilder.sizeHint(psmCountMatrixBuffer.length)
    val mqPepsRatiosCvs = new ArrayBuffer[ArrayBuffer[Float]](mqPepsClusters.length)
    
    val abSumMethod = MqPeptideAbundanceSummarizingMethod.withName(config.peptideAbundanceSummarizingMethod)
    
    for( mqPepsCluster <- mqPepsClusters ) {

      val clusteredMqPeps = {
        if (config.isMqPeptideAbundanceSummarizerBasedOn(QuantComponentItem.QUANT_PEPTIDES)) {
          mqPepsCluster.mqPeptides.asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]
        } else {
          mqPepsCluster.mqPeptides.flatMap(_.masterQuantPeptideIons).asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]
        }
      }
         
      // Map MasterQuantPeptides by Id
      val mqPepsById = mqPepsCluster.mqPeptides.map(_.id).zip(mqPepsCluster.mqPeptides).toMap
      
      // Summarize raw abundances of the current profile cluster
      val mqPepRawAbundanceMatrix = clusteredMqPeps.map( _.getRawAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray
      
      val summarizedRawAbundances = this.summarizeMatrix(mqPepRawAbundanceMatrix, MqPeptideAbundanceSummarizingMethod.SUM)
      //println("summarizedRawAbundances: " + summarizedRawAbundances.mkString("\t"))

      rawAbundancesByProfileClusterBuilder += mqPepsCluster -> summarizedRawAbundances
      
      // Summarize abundances of the current profile cluster
      val mqPepAbundanceMatrix = clusteredMqPeps.map( _.getAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray

      // Summarize Peptide count of the current profile cluster
      val pepCountMatrixBuilder = Array.newBuilder[Int]
      pepCountMatrixBuilder.sizeHint(expDesignSetup.qcIds.length)
      mqPepsCluster.mqPeptides.map( _.getAbundancesForQuantChannels(expDesignSetup.qcIds) ).transpose.map{ qChAbs =>
        val nbpep = qChAbs.count(a => !a.isNaN && !(a <= 0.0f))
        pepCountMatrixBuilder += nbpep
      }
      val pepCountMatrix = pepCountMatrixBuilder.result()
      pepCountsByProfileClusterBuilder += mqPepsCluster -> pepCountMatrix


      val abRow = this.summarizeMatrix(mqPepAbundanceMatrix, abSumMethod)
      abundanceMatrixBuffer += abRow
      
      // Summarize PSM counts of the current profile cluster
      val psmCountMatrix = clusteredMqPeps.map( _.getPepMatchesCountsForQuantChannels(expDesignSetup.qcIds) ).toArray
      val psmCountSummarized = psmCountMatrix.transpose.map( _.sum )
      psmCountMatrixBuffer += psmCountSummarized
      psmCountsByProfileClusterBuilder += mqPepsCluster -> psmCountSummarized
      
      // Retrieve ratios
      val ratioMatrix = new ArrayBuffer[Array[Float]](clusteredMqPeps.length)
      
      clusteredMqPeps.foreach { mq =>
        val mqPep = if (mq.isInstanceOf[MasterQuantPeptide]) {  
            mq.asInstanceOf[MasterQuantPeptide] 
          } else { 
            mqPepsById(mq.asInstanceOf[MasterQuantPeptideIon].masterQuantPeptideId)
          }
        val profile = mqPep.properties.get.getMqPepProfileByGroupSetupNumber.get(groupSetupNumber)
        val mqPepRatioValues = profile.getRatios().map(_.map(_.ratioValue).getOrElse(Float.NaN)).toArray
        ratioMatrix += mqPepRatioValues
      }
      // Transpose the ratioMatrix to work with ratios of the same definition
      val ratiosCvs = ratioMatrix.transpose.map { computedRatioValues =>
        val defRatioValues = computedRatioValues.filter( !isZeroOrNaN(_) )
        if( defRatioValues.length < 3 ) Float.NaN
        else {
          // FXIME: transform ratios before computing their CV ?
          coefficientOfVariation(defRatioValues.map(_.toDouble).toArray).toFloat
        }
      }
      
      mqPepsRatiosCvs += ratiosCvs
    }
    
    // --- Map raw abundances by the corresponding MQ peptides cluster ---
    val rawAbundancesByProfileCluster = rawAbundancesByProfileClusterBuilder.result
    val psmCountsByProfileCluster = psmCountsByProfileClusterBuilder.result
    val pepCountsByProfileCluster = pepCountsByProfileClusterBuilder.result()

    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( !config.proteinStatConfig.applyNormalization ) abundanceMatrixBuffer.toArray
    else AbundanceNormalizer.normalizeAbundances(abundanceMatrixBuffer.toArray)
    
    // --- Compute absolute error model and the filled matrix ---
    val psmCountMatrix = psmCountMatrixBuffer.toArray
    val absoluteErrorModel = this.computeAbsoluteErrorModel(normalizedMatrix)
    val filledMatrix = this.inferMissingValues(
      normalizedMatrix,
      psmCountMatrix,
      absoluteErrorModel,
      config.proteinStatConfig
    )
    assert(filledMatrix.length == abundanceMatrixBuffer.length)
    
    // --- Compute the ratios corresponding to each profile cluster ---
    
    // Create a map which will store the ratios corresponding to each profile cluster
    val ratiosByMqPepCluster = mqPepsClusters.seq.view.map( _ -> new ArrayBuffer[Option[ComputedRatio]]).toMap
    
    // Iterate over the ratio definitions
    for ( ratioDef <- expDesignSetup.groupSetup.ratioDefinitions ) {
      
      val ratios = computeRatios(
        ratioDef,
        filledMatrix,
        psmCountMatrix,
        absoluteErrorModel,
        config.proteinStatConfig
      )
      
      val computedRatioIdx = ratioDef.number - 1
      for ( ratio <- ratios ) {
        
        val rowIndex = ratio.entityId.toInt
        val mqPepCluster = mqPepsClusters(rowIndex)
        val mqPepRatiosCvs = mqPepsRatiosCvs(rowIndex)
        val ratioCv = mqPepRatiosCvs(computedRatioIdx)
        
        val computedRatio = ComputedRatio(
          numerator = ratio.numerator.toFloat,
          denominator = ratio.denominator.toFloat,
          cv = Some(ratioCv),
          //numeratorCv = ratio.numeratorSummary.getCv().toFloat,
          //denominatorCv = ratio.denominatorSummary.getCv().toFloat,
          state = ratio.state.map(_.id).getOrElse(0),
          tTestPValue = ratio.tTestPValue,
          zTestPValue = ratio.zTestPValue,
          zScore = ratio.zScore
        )
        
        ratiosByMqPepCluster( mqPepCluster ) += Some(computedRatio)
      }
    }
    
    // --- Map normalized abundances by the corresponding profile cluster ---
    val abundancesByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Float]]
    abundancesByProfileClusterBuilder.sizeHint(filledMatrix.length)
    
    filledMatrix.indices.foreach { rowIndex =>
      val mqPepCluster = mqPepsClusters(rowIndex)
      abundancesByProfileClusterBuilder += mqPepCluster -> filledMatrix(rowIndex)
    }
    
    val abundancesByProfileCluster = abundancesByProfileClusterBuilder.result
    
    // Update the profiles using the obtained results
    val mqProfilesByProtSet = masterQuantProtSets.view.map( _ -> new ArrayBuffer[MasterQuantProteinSetProfile] ).toMap
    
    // Group profiles by Master Quant protein set
    for ( (mqPepCluster,ratios) <- ratiosByMqPepCluster ) {

      val mqProtSet = mqPepCluster.mqProteinSet
      val mqPeptideIds = mqPepCluster.mqPeptides.map(_.id).toArray
      val rawAbundances = rawAbundancesByProfileCluster(mqPepCluster)
      val abundances = abundancesByProfileCluster(mqPepCluster)
      val psmCounts = psmCountsByProfileCluster(mqPepCluster)
      val pepCounts = pepCountsByProfileCluster(mqPepCluster)

      val quantProfile = MasterQuantProteinSetProfile(
        rawAbundances = rawAbundances,
        abundances = abundances, //.map(x => 0f),
        ratios = ratios.toList,
        mqPeptideIds = mqPeptideIds,
        peptideMatchesCounts =  psmCounts,
        peptideCounts = pepCounts
      )
      
      mqProfilesByProtSet(mqProtSet) += quantProfile
    }
    
    // Update Master Quant protein sets properties
    for( (mqProtSet,mqProfiles) <- mqProfilesByProtSet ) {
      //if (mqProfiles.isEmpty) println(mqProtSet.proteinSet.getRepresentativeProteinMatch().map(_.accession).getOrElse("no") )
      
      val mqProtSetProps = mqProtSet.properties.getOrElse( MasterQuantProteinSetProperties() )
      val mqProtSetProfileMap = mqProtSetProps.getMqProtSetProfilesByGroupSetupNumber().getOrElse( mutable.HashMap() )

      mqProtSetProfileMap += (groupSetupNumber -> mqProfiles.toArray)
      mqProtSetProps.setMqProtSetProfilesByGroupSetupNumber( Some(mqProtSetProfileMap) )
      
      mqProtSet.properties = Some(mqProtSetProps)
      //logger.debug("score:"+mqProtSet.proteinSet.peptideSet.score)
      
      val bestMQProtSetProfile = mqProtSet.getBestProfile(groupSetupNumber)
      if (bestMQProtSetProfile.isDefined) {
        //logger.debug("undefined abundance: "+bestMQProtSetProfile.get.abundances.count(ab =>isZeroOrNaN(ab)) )
//        mqProtSet.setAbundancesForQuantChannels(bestMQProtSetProfile.get.abundances,expDesignSetup.qcIds)
        mqProtSet.setAbundancesAndCountsForQuantChannels(bestMQProtSetProfile.get.abundances, bestMQProtSetProfile.get.peptideMatchesCounts,bestMQProtSetProfile.get.peptideCounts, expDesignSetup.qcIds)
        
      } else {
        // FOR TESTS ONLY //
        //logger.debug("empty abundances")
        
        // Set abundances to NaN
        for( qProtSet <- mqProtSet.quantProteinSetMap.values ) {
          qProtSet.abundance = Float.NaN
        }
        
        // Deselect master quant protein set
        /*if( mqProtSet.selectionLevel == 2 ) {
          mqProtSet.selectionLevel = 1
        }*/
      }
      
      // TODO: remove me Reset quant profiles for this masterQuantProtSet
      /*for( masterQuantProtSetProps <- mqProtSet.properties) {
        masterQuantProtSetProps.setMqProtSetProfilesByGroupSetupNumber(None)
      }*/
    }
    
    ()
  }
  
  private def summarizeMatrix(abundanceMatrix: Array[Array[Float]], abSumMethod: MqPeptideAbundanceSummarizingMethod.Value): Array[Float] = {
    if(abundanceMatrix.isEmpty) return Array()
    if(abundanceMatrix.length == 1) abundanceMatrix.head

    import MqPeptideAbundanceSummarizingMethod._
    
    // Summarize raw abundances of the current profile cluster
    val finalAbSumMethod = abSumMethod

    // If the method is not median profile
    val summarizedAbundances = if (finalAbSumMethod == MEDIAN_RATIO_FITTING) {
      LFQSummarizer.summarize(abundanceMatrix, minAbundanceByQcIds)
      
    } else if (!AbundanceSummarizer.advancedMethods.contains(finalAbSumMethod)) {
      AbundanceSummarizer.summarizeAbundanceMatrix(
        abundanceMatrix,
        finalAbSumMethod
      )
    } else { // The method request the ExperimentalDesignSetup
       AdvancedAbundanceSummarizer.summarizeAbundanceMatrix(
        abundanceMatrix,
        finalAbSumMethod,
        expDesignSetup
      )
    } // END OF if the method is median profile
    
    summarizedAbundances
  }
  
  private def coefficientOfVariation(values: Array[Double]): Double = {
    val mean = StatUtils.mean(values)
    Math.sqrt( StatUtils.variance(values, mean) ) / mean
  }
  
  private def standardDeviation(values: Array[Double]): Double = {
    Math.sqrt( StatUtils.variance(values) )
  }
  
  protected def computeAbsoluteErrorModel(
    normalizedMatrix: Array[Array[Float]]
  ): Option[AbsoluteErrorModel] = {
    
      
    // --- Estimate the noise models ---
    val absoluteErrors = new ArrayBuffer[AbsoluteErrorObservation](normalizedMatrix.length)
    val relativeErrors = new ArrayBuffer[RelativeErrorObservation](normalizedMatrix.length)
    
    // Iterator over each row of the abundance matrix in order to compute some statistical models
    normalizedMatrix.foreach { normalizedRow =>

      // Compute statistics at technical replicate level if enough replicates (at least 3)
      if( expDesignSetup.minQCsCountPerSample > 2 ) {
        // Iterate over each sample in order to build the absolute error model using existing sample analysis replicates
        for( sampleNum <- expDesignSetup.allSampleNumbers ) {
          val qcIndices = expDesignSetup.qcIndicesBySampleNum(sampleNum)
          require( qcIndices != null && qcIndices.length > 2, "qcIndices must be defined" )
          
          val sampleAbundances = qcIndices.map( normalizedRow(_) ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
          //println(sampleAbundances)
          
          // Check we have enough abundances (at least 3)
          if( sampleAbundances.length > 2 ) {
            val sampleStatSummary = CommonsStatHelper.calcExtendedStatSummary(sampleAbundances)
            val abundance = sampleStatSummary.getMedian().toFloat
            
            if( !isZeroOrNaN(abundance) )
              absoluteErrors += AbsoluteErrorObservation( abundance, sampleStatSummary.getStandardDeviation.toFloat )
          }
        }
      // Compute statistics at biological sample level
      } else {
        
        // FIXME: find a workaround to compute relative errors (compute all consecutive ratios ?)
        /*val numeratorMedianAbundance = _medianAbundance( _getSamplesMedianAbundance( normalizedRow, numeratorSampleNumbers ) )
        val denominatorMedianAbundance = _medianAbundance( _getSamplesMedianAbundance( normalizedRow, denominatorSampleNumbers ) )
        
        val maxAbundance = math.max(numeratorMedianAbundance,denominatorMedianAbundance)
        
        if( maxAbundance.isNaN == false && maxAbundance > 0 ) {
          relativeErrors += RelativeErrorObservation( maxAbundance, numeratorMedianAbundance/denominatorMedianAbundance)
        }
        */
      }
    }
    
    // Estimate the absolute noise model using technical replicates
    val absoluteNoiseModel = if( !absoluteErrors.isEmpty ) {
      ErrorModelComputer.computeAbsoluteErrorModel(absoluteErrors,nbins=Some(errorModelBinsCount))    
    // Estimate the relative noise model using sample replicates
    } else if( !relativeErrors.isEmpty ) {
      //
      // TODO CBy : cannot understand how that can happen ?? relativeErrors is never updated since l.542 
      //
      this.logger.warn("Insufficient number of analysis replicates => try to estimate absolute noise model using relative observations")
      //this.logger.debug("relativeErrors:" + relativeErrors.length + ", filtered zero :" + relativeErrors.filter(_.abundance > 0).length)
      ErrorModelComputer.computeRelativeErrorModel(relativeErrors, nbins = Some(errorModelBinsCount)).toAbsoluteErrorModel()
    
    // Create a fake Error Model
    } else {
      // TODO: compute the stdDev from relativeErrors ?
      logger.warn("Can't estimate error model: insufficient number of technical replicates")
      //new AbsoluteErrorModel( Seq(AbsoluteErrorBin( abundance = 0f, stdDev = 1f )) )
      return None
    }
    
    Some(absoluteNoiseModel)
  }
  
  protected def inferMissingValues(
    normalizedMatrix: Array[Array[Float]],
    psmCountMatrix: Array[Array[Int]],
    absoluteNoiseModelOpt: Option[AbsoluteErrorModel],
    config: ProfilizerStatConfig
  ): Array[Array[Float]] = {
    
    logger.debug("config.applyMissValInference value: "+ config.applyMissValInference)
    
    if (!config.applyMissValInference) return normalizedMatrix
    
    /*if (minQCsCountPerSample < 3) {
      // TODO: find what to do if when insufficient technical replicates
      throw new Exception("Can't infer missing values: insufficient number of technical replicates")
    }*/
    
      
    // --- Infer missing abundances ---
      
    // Instantiates an abundance inferer
    val inferConfig = config.missValInferenceConfig.get
    val abundanceInferer = MissingAbundancesInferenceMethod.withName( config.missValInferenceMethod ) match {
      case MissingAbundancesInferenceMethod.GAUSSIAN_MODEL => {
        logger.info("Inferring missing values using the gaussian model method...")
        new SmartMissingAbundancesInferer(inferConfig, absoluteNoiseModelOpt.get)
      }
      case MissingAbundancesInferenceMethod.PERCENTILE => {
        logger.info("Inferring missing values using the percentile method...")
        new FixedNoiseMissingAbundancesReplacer(inferConfig)
      }
    }
    
    // Extract abundance matrices for each biological sample
    val abMatrixBySampleNum = expDesignSetup.allSampleNumbers.map( _ -> new ArrayBuffer[Array[Float]] ).toMap      
    normalizedMatrix.foreach { normalizedRow =>
      expDesignSetup.allSampleNumbers.foreach { sampleNum =>
        abMatrixBySampleNum(sampleNum) += expDesignSetup.qcIndicesBySampleNum(sampleNum).map(normalizedRow(_))
      }
    }
    
    // Extract PSM counts for each biological sample
    val psmCountMatrixBySampleNum = expDesignSetup.allSampleNumbers.map( _ -> new ArrayBuffer[Array[Int]] ).toMap      
    psmCountMatrix.foreach { psmCountRow =>
      expDesignSetup.allSampleNumbers.foreach { sampleNum =>
        psmCountMatrixBySampleNum(sampleNum) += expDesignSetup.qcIndicesBySampleNum(sampleNum).map(psmCountRow(_))
      }
    }
    
    val tmpFilledMatrix = Array.ofDim[Float](normalizedMatrix.length,expDesignSetup.quantChannels.length)
    
    for( (sampleNum,sampleAbMatrix) <- abMatrixBySampleNum ) {
      
      val qcIndices = expDesignSetup.qcIndicesBySampleNum(sampleNum)
      val samplePsmCountMatrix = psmCountMatrixBySampleNum(sampleNum).toArray
      
      val inferredSampleMatrix = abundanceInferer.inferAbundances(sampleAbMatrix.toArray, samplePsmCountMatrix)
      
      var filledMatrixRow = 0
      inferredSampleMatrix.foreach { inferredAbundances =>
        
        inferredAbundances.zip(qcIndices).foreach { case (abundance,colIdx) =>
          tmpFilledMatrix(filledMatrixRow)(colIdx) = abundance
        }
        
        filledMatrixRow += 1
      }
    }
    
    tmpFilledMatrix
  }
  
  protected def computeRatios(
    ratioDef: RatioDefinition,
    filledMatrix: Array[Array[Float]],
    psmCountMatrix: Array[Array[Int]],
    absoluteNoiseModelOpt: Option[AbsoluteErrorModel],
    config: ProfilizerStatConfig
  ): Seq[AverageAbundanceRatio] = {
    
    logger.debug(s"computing ratios on a matrix containing ${filledMatrix.length} values...")

    // Retrieve some vars
    val numeratorSampleNumbers = expDesignSetup.sampleNumbersByGroupNumber(ratioDef.numeratorGroupNumber)
    val denominatorSampleNumbers = expDesignSetup.sampleNumbersByGroupNumber(ratioDef.denominatorGroupNumber)
    val allSampleNumbers = numeratorSampleNumbers ++ denominatorSampleNumbers
    require( numeratorSampleNumbers != null && !numeratorSampleNumbers.isEmpty, "numeratorSampleNumbers must be defined" )
    require( denominatorSampleNumbers != null && !denominatorSampleNumbers.isEmpty, "denominatorSampleNumbers must be defined" )
    
    // Map quant channel indices by the sample number
    val qcIndicesBySampleNum = allSampleNumbers.map { sampleNum =>
          sampleNum -> expDesignSetup.quantChannelsBySampleNumber(sampleNum).map( qc => expDesignSetup.qcIdxById(qc.id) )
        }.toMap
    
    def _getSamplesAbundances(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.flatMap( i => i.map( abundances(_) ) )
    }
    /*def _getSamplesMeanAbundance(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.map { i => this._meanAbundance( i.map( abundances(_) ) ) }
    }*/
    def _getSamplesMedianAbundance(abundances: Array[Float], sampleNumbers: Array[Int]): Array[Float] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.map { i => median( i.map( abundances(_) ) ) }
    }
    def _getSamplesPsmCounts(psmCounts: Array[Int], sampleNumbers: Array[Int]): Array[Int] = {
      val qcIndices = sampleNumbers.map( qcIndicesBySampleNum(_) )
      qcIndices.flatMap( i => i.map( psmCounts(_) ) )
    }
    
    // --- Determine the significant abundance changes ---
    
    // Create a new buffer for ratios to be computed
    val ratiosBuffer = new ArrayBuffer[AverageAbundanceRatio](filledMatrix.length)
    
    // Compute the error models
    val absoluteVariationsBuffer = new ArrayBuffer[AbsoluteErrorObservation](filledMatrix.length) // for n sample replicates
    val relativeVariationsBuffer = new ArrayBuffer[RelativeErrorObservation](filledMatrix.length) // for 1 sample replicate
    
    var rowIdx = 0
    filledMatrix.foreach { filledRow =>
      
      var numeratorSummary: ExtendedStatisticalSummary = null
      var denominatorSummary: ExtendedStatisticalSummary = null
      
      // Check if we have enough biological sample replicates to compute statistics at this level
      if( expDesignSetup.minSamplesCountPerGroup > 2 ) {
      
        // Retrieve the entity ID
        //val masterQuantPepId = masterQuantPeptides(rowIdx).id
        
        // Compute numerator and denominator abundances
        val numeratorMedianAbundances = _getSamplesMedianAbundance( filledRow, numeratorSampleNumbers )
        val denominatorMedianAbundances = _getSamplesMedianAbundance( filledRow, denominatorSampleNumbers )
        
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcExtendedStatSummary(numeratorMedianAbundances.map(_.toDouble))
        denominatorSummary = CommonsStatHelper.calcExtendedStatSummary(denominatorMedianAbundances.map(_.toDouble))        
     
        // We can then make absolute statistical validation at the biological sample level
        val (numerator, numStdDev) = ( numeratorSummary.getMedian().toFloat, numeratorSummary.getStandardDeviation.toFloat )
        val (denom, denomStdDev) = ( denominatorSummary.getMedian().toFloat, denominatorSummary.getStandardDeviation.toFloat )
        
        if (!numerator.isNaN &&  !numStdDev.isNaN && !denom.isNaN && !denomStdDev.isNaN) {
          absoluteVariationsBuffer += AbsoluteErrorObservation( numerator, numStdDev )
          absoluteVariationsBuffer += AbsoluteErrorObservation( denom, denomStdDev )
        } else {
          this.logger.trace("Stat summary contains NaN mean/median or NaN standardDeviation")
        }
        
      }
      // Else we merge biological sample data and compute statistics at a lower level
      else {
        // Compute numerator and denominator statistical summaries
        numeratorSummary = CommonsStatHelper.calcExtendedStatSummary(
          _getSamplesAbundances( filledRow, numeratorSampleNumbers ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
        )
        denominatorSummary = CommonsStatHelper.calcExtendedStatSummary(
          _getSamplesAbundances( filledRow, denominatorSampleNumbers ).withFilter( isZeroOrNaN(_) == false ).map(_.toDouble)
        )
      }
      
      // Retrieve PSM counts
      val psmCountRow = psmCountMatrix(rowIdx)
      val numeratorPsmCounts = _getSamplesPsmCounts(psmCountRow, numeratorSampleNumbers)
      val denominatorPsmCounts = _getSamplesPsmCounts(psmCountRow, denominatorSampleNumbers)
      
      // Compute the ratio for this row
      val ratio = AverageAbundanceRatio( rowIdx, numeratorSummary, denominatorSummary, numeratorPsmCounts, denominatorPsmCounts )
      ratiosBuffer += ratio
      
      // Update the relative error model
      if( ratio.ratioValue.isDefined && ratio.ratioValue.get > 0 ) {
        relativeVariationsBuffer += RelativeErrorObservation( ratio.maxAbundance.toFloat, ratio.ratioValue.get )
      }

      rowIdx += 1
    }
    
    if( relativeVariationsBuffer.length < 5 ) {
      logger.warn("Insufficient number of ratios to compute their significativity !")
    } else {
      val relativeVariationModel = ErrorModelComputer.computeRelativeErrorModel(relativeVariationsBuffer,nbins=Some(errorModelBinsCount))
      
      // Retrieve the right tuple of error models
      val absoluteErrorModelOpt = if( expDesignSetup.minSamplesCountPerGroup > 2 ) {
        Some( ErrorModelComputer.computeAbsoluteErrorModel(absoluteVariationsBuffer,nbins=Some(errorModelBinsCount)) )
      } else if( expDesignSetup.minQCsCountPerSample > 2 ) {
        absoluteNoiseModelOpt
      } else None
    
      // Update the variation state of ratios
      AbundanceRatiolizer.updateRatioStates(
        ratiosBuffer,
        relativeVariationModel,
        absoluteErrorModelOpt,
        config
      )
      
    }
    
    ratiosBuffer
  }
  

}
