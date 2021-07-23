package fr.proline.core.algo.msq

import com.typesafe.scalalogging.LazyLogging
import fr.profi.util.collection._
import fr.profi.util.primitives.isZeroOrNaN
import fr.proline.core.algo.msq.config.profilizer._
import fr.proline.core.algo.msq.profilizer._
import fr.proline.core.algo.msq.profilizer.filtering._
import fr.proline.core.algo.msq.summarizing.BuildMasterQuantPeptide
import fr.proline.core.om.model.SelectionLevel
import fr.proline.core.om.model.msq._

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
    
    // --- Apply PTMs filter if requested ---
    if(config.discardModifiedPeptides) {
      require( config.modifiedPeptideFilteringMethod.isDefined , "config.modifiedPeptideFilteringMethod is empty")
      require( config.modifiedPeptideFilterConfig.isDefined && !(config.modifiedPeptideFilterConfig.get.ptmDefinitionIdsToDiscard.isEmpty && !config.modifiedPeptideFilterConfig.get.ptmPattern.isDefined), "No modifications specified for discard Modified peptide")
      ModifiedPeptideFilter.discardPeptides(masterQuantPeptides, config.modifiedPeptideFilteringMethod.get, config.modifiedPeptideFilterConfig.get)
    }

    // Keep master quant peptides passing all filters (i.e. have a selection level higher than 1)
    //
    // val( mqPepIonsAfterAllFilters, deselectedMqPeps ) = masterQuantPeptides.partition( _.selectionLevel >= 2 )
    //
    // FIXME: DBO => should we work only with filtered mqPeps ?
    // FIXME: CBy => selectionLevel < 2 are kept, this means that normalization & inference are also done on deselected masterQuantComponents

    //VDS: Changed behaviour: mqPeptideIon are used for normalisation etc. Values are then used for mqPeptides values.
    // NORMALIZATION WARNING : Peptide config normalization is currently done on all Peptide IONS .... To be changed
    val mqPepIonsAfterAllFilters = masterQuantPeptides.flatMap(_.masterQuantPeptideIons).asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]

    //
    // Reset quant peptide abundance of deselected master quant peptides
    // DBO: is this useful ???
    //
    // for( mqPep <- deselectedMqPeps; (qcid,qPep) <- mqPep.quantPeptideMap ) {
    //  qPep.abundance = Float.NaN
    // }
    //
     

    // --- Compute the abundance matrix ---
    val rawAbundanceMatrix: Array[Array[Float]] = mqPepIonsAfterAllFilters.map( _.getRawAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray
    
    // --- Normalize the abundance matrix ---
    //
    // TODO modify this to take into account PEPTIDE or ION
    //
    // NORMALIZATION WARNING : Peptide config normalization is currently done on all Peptide IONS .... To be changed
    val normalizedRawAbundMatrix = if( !config.pepIonApplyNormalization ) rawAbundanceMatrix else AbundanceNormalizer.normalizeAbundances(rawAbundanceMatrix)
    
    require( normalizedRawAbundMatrix.length == rawAbundanceMatrix.length, "error during normalization, some peptides were lost...")

    //Used for LFQ Summarizing method
    minAbundanceByQcIds = normalizedRawAbundMatrix.transpose.map { abundanceCol =>
       val abundanceForAQch =  abundanceCol.filter( !isZeroOrNaN(_))
       if(abundanceForAQch.isEmpty)
         Float.NaN
       else
         abundanceForAQch.reduceLeft(_ min _)
    }
     
    // Update master quant component abundances after normalization
    for( (mqQuantComponent, abundances) <- mqPepIonsAfterAllFilters.zip(normalizedRawAbundMatrix) ) {
      mqQuantComponent.setAbundancesForQuantChannels(abundances,expDesignSetup.qcIds)
    }

    //VDS: Changed behaviour: mqPeptideIon are used for normalisation etc. Values are then used for mqPeptides values.
    for (mqPep <- masterQuantPeptides) {
      val mqPepIons = mqPep.masterQuantPeptideIons

      // Re-build the master quant peptides
      val newMqPep = BuildMasterQuantPeptide(mqPepIons, mqPep.peptideInstance, mqPep.resultSummaryId, config.pepIonAbundanceSummarizingMethod)

      val abundances = newMqPep.getAbundancesForQuantChannels(expDesignSetup.qcIds)
      val psmCounts = newMqPep.getPepMatchesCountsForQuantChannels(expDesignSetup.qcIds)

      mqPep.setAbundancesAndPsmCountForQuantChannels(abundances, psmCounts, expDesignSetup.qcIds)

      //Get properties back
      mqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig= newMqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig
      // the next step is mandatory since BuildMasterQuantPeptide updates mqPepIons.masterQuantPeptideId to the new MasterQuantPeptide
      mqPepIons.foreach { mqPepIon =>
        mqPepIon.masterQuantPeptideId = mqPep.id
      }
    }

    logger.info("After computeMasterQuantPeptideProfiles mqPep with selection level == 1 : "+masterQuantPeptides.count(_.selectionLevel == 1))
    logger.info("After computeMasterQuantPeptideProfiles mqPep with selection level == 2 : "+masterQuantPeptides.count(_.selectionLevel == 2))

    ()
  }
  
  def computeMasterQuantProtSetProfiles( masterQuantProtSets: Seq[MasterQuantProteinSet], config: PostProcessingConfig) {

    logger.info("computing master quant protein set profiles...")



    val useRazor = config.peptidesSelectionMethod.equals(MqPeptidesSelectionMethod.RAZOR_AND_SPECIFIC)
    //variables used only if razor
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
              // masterQuantPeptide is shared between proteinSet. Assign to only one regarding RAZOR RULE
              //-- TODO If razor config is MOST_SPECIFIC_PEP_SELECTION
              val mqProtIds = mqpep.getMasterQuantProteinSetIds().get // if isProteinSetSpecific defined getMasterQuantProteinSetIds should also...
              val nbrSpecPepByProtSetId = mutable.LongMap.empty[Int]
              mqProtIds.map(prId =>
                { nbrSpecPepByProtSetId += (prId -> nbrSpecificMqPepByMqProtSetId.getOrElse(prId,0)) }
              )
              val maxNbrSp = nbrSpecPepByProtSetId.maxBy(_._2)._2
              logger.trace("-------- RAZOR max number for "+ mqpep.id +" of specific pep  =" +maxNbrSp)
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

          } ///End mqPep not already seen,
        } //End Use Razor


        if (protSetSelLvl != newAutoSelLvl) {
          protSetSelLvlMap.update(mqPepId, newAutoSelLvl)
        }

      } // End for ProteinSets
      
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
    val psmCountsByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Int]]
    psmCountsByProfileClusterBuilder.sizeHint(mqPepsClusters.length)
    val pepCountsByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Int]]
    pepCountsByProfileClusterBuilder.sizeHint(mqPepsClusters.length)

    val abSumMethod = MqPeptideAbundanceSummarizingMethod.withName(config.peptideAbundanceSummarizingMethod)
    
    for( mqPepsCluster <- mqPepsClusters ) {

      val clusteredMqCompos = {
        if (config.isMqPeptideAbundanceSummarizerBasedOn(QuantComponentItem.QUANT_PEPTIDES)) {
          mqPepsCluster.mqPeptides.asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]
        } else {
          mqPepsCluster.mqPeptides.flatMap(_.masterQuantPeptideIons).asInstanceOf[Seq[MasterQuantComponent[QuantComponent]]]
        }
      }
         
      // Map MasterQuantPeptides by Id
      val mqPepsById = mqPepsCluster.mqPeptides.map(_.id).zip(mqPepsCluster.mqPeptides).toMap
      
      // Summarize raw abundances of the current profile cluster
      val mqPepRawAbundanceMatrix = clusteredMqCompos.map( _.getRawAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray

      //Recalculate raw abundance by using SUM method.
      val summarizedRawAbundances = this.summarizeMatrix(mqPepRawAbundanceMatrix, MqPeptideAbundanceSummarizingMethod.SUM)
      rawAbundancesByProfileClusterBuilder += mqPepsCluster -> summarizedRawAbundances
      
      // Summarize abundances of the current profile cluster
      val mqPepAbundanceMatrix = clusteredMqCompos.map( _.getAbundancesForQuantChannels(expDesignSetup.qcIds) ).toArray

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
      val psmCountMatrix = clusteredMqCompos.map( _.getPepMatchesCountsForQuantChannels(expDesignSetup.qcIds) ).toArray
      val psmCountSummarized = psmCountMatrix.transpose.map( _.sum )
      psmCountsByProfileClusterBuilder += mqPepsCluster -> psmCountSummarized

    }
    
    // --- Map raw abundances by the corresponding MQ peptides cluster ---
    val rawAbundancesByProfileCluster = rawAbundancesByProfileClusterBuilder.result
    val psmCountsByProfileCluster = psmCountsByProfileClusterBuilder.result
    val pepCountsByProfileCluster = pepCountsByProfileClusterBuilder.result()

    // --- Normalize the abundance matrix ---
    val normalizedMatrix = if( !config.proteinSetApplyNormalization ) abundanceMatrixBuffer.toArray
    else AbundanceNormalizer.normalizeAbundances(abundanceMatrixBuffer.toArray)
    assert(normalizedMatrix.length == abundanceMatrixBuffer.length)

    // --- Map normalized abundances by the corresponding profile cluster ---
    val abundancesByProfileClusterBuilder = Map.newBuilder[MasterQuantPeptidesCluster,Array[Float]]
    abundancesByProfileClusterBuilder.sizeHint(normalizedMatrix.length)

    normalizedMatrix.indices.foreach { rowIndex =>
      val mqPepCluster = mqPepsClusters(rowIndex)
      abundancesByProfileClusterBuilder += mqPepCluster -> normalizedMatrix(rowIndex)
    }
    
    val abundancesByProfileCluster = abundancesByProfileClusterBuilder.result

    // Update the profiles using the obtained results
    val mqProfilesByProtSet = masterQuantProtSets.view.map( _ -> new ArrayBuffer[MasterQuantProteinSetProfile] ).toMap

    // Group profiles by Master Quant protein set
    for ( mqPepCluster <- mqPepsClusters ) {

      val mqProtSet = mqPepCluster.mqProteinSet
      val mqPeptideIds = mqPepCluster.mqPeptides.map(_.id).toArray
      val rawAbundances = rawAbundancesByProfileCluster(mqPepCluster)
      val abundances = abundancesByProfileCluster(mqPepCluster)
      val psmCounts = psmCountsByProfileCluster(mqPepCluster)
      val pepCounts = pepCountsByProfileCluster(mqPepCluster)

      val quantProfile = MasterQuantProteinSetProfile(
        rawAbundances = rawAbundances,
        abundances = abundances, //.map(x => 0f),
        ratios = List(),
        mqPeptideIds = mqPeptideIds,
        peptideMatchesCounts =  psmCounts,
        peptideCounts = pepCounts
      )
      
      mqProfilesByProtSet(mqProtSet) += quantProfile
    }
    
    // Update Master Quant protein sets properties
    for( (mqProtSet,mqProfiles) <- mqProfilesByProtSet ) {

      val mqProtSetProps = mqProtSet.properties.getOrElse( MasterQuantProteinSetProperties() )
      val mqProtSetProfileMap = mqProtSetProps.getMqProtSetProfilesByGroupSetupNumber().getOrElse( mutable.HashMap() )

      mqProtSetProfileMap += (groupSetupNumber -> mqProfiles.toArray)
      mqProtSetProps.setMqProtSetProfilesByGroupSetupNumber( Some(mqProtSetProfileMap) )
      mqProtSet.properties = Some(mqProtSetProps)

      val bestMQProtSetProfile = mqProtSet.getBestProfile(groupSetupNumber)
      if (bestMQProtSetProfile.isDefined) {
        mqProtSet.setAbundancesAndCountsForQuantChannels(bestMQProtSetProfile.get.abundances, bestMQProtSetProfile.get.peptideMatchesCounts,bestMQProtSetProfile.get.peptideCounts, expDesignSetup.qcIds)
        
      } else {

        //No Profile for current ProteinSet. This means no peptides quantitation associated to it. Set all values to Nan/0
        // Set abundances to NaN
        for( qProtSet <- mqProtSet.quantProteinSetMap.values ) {
          qProtSet.abundance = Float.NaN
          qProtSet.peptidesCount = Some(0)
          qProtSet.peptideMatchesCount = 0
        }
        
        // Deselect master quant protein set
        /*if( mqProtSet.selectionLevel == 2 ) {
          mqProtSet.selectionLevel = 1
        }*/
      }

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
}
