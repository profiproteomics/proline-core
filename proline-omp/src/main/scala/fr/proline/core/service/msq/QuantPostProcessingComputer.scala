package fr.proline.core.service.msq

import com.typesafe.scalalogging.LazyLogging
import fr.profi.api.service.IService
import fr.profi.jdbc.easy._
import fr.profi.util.MathUtils
import fr.profi.util.collection._
import fr.profi.util.serialization.ProfiJson
import fr.proline.context.{IExecutionContext, MsiDbConnectionContext}
import fr.proline.core.algo.lcms.summarizing._
import fr.proline.core.algo.lcms.{FeatureSummarizer, FeatureSummarizingMethod}
import fr.proline.core.algo.msq.Profilizer
import fr.proline.core.algo.msq.config.profilizer.{MqPeptideAbundanceSummarizingMethod, PostProcessingConfig}
import fr.proline.core.algo.msq.profilizer.AbundanceSummarizer
import fr.proline.core.algo.msq.summarizing.BuildMasterQuantPeptide
import fr.proline.core.dal.helper.UdsDbHelper
import fr.proline.core.dal.{BuildLazyExecutionContext, DoJDBCWork}
import fr.proline.core.om.model.SelectionLevel
import fr.proline.core.om.model.lcms.Feature
import fr.proline.core.om.model.msq.{ExperimentalDesign, MasterQuantPeptideIon, MasterQuantPeptideProperties, QuantResultSummary}
import fr.proline.core.om.provider.PeptideCacheExecutionContext
import fr.proline.core.om.provider.lcms.impl.SQLMapSetProvider
import fr.proline.core.om.provider.msq.impl.{SQLExperimentalDesignProvider, SQLQuantResultSummaryProvider}
import fr.proline.core.orm.uds.repository.ObjectTreeSchemaRepository
import fr.proline.core.orm.uds.{MasterQuantitationChannel, ObjectTree, ObjectTreeSchema, QuantitationMethod}
import fr.proline.repository.IDataStoreConnectorFactory
import org.apache.commons.math3.linear.{Array2DRowRealMatrix, RealMatrix}

import java.io.{File, FilenameFilter}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import scala.io.Source

// Factory for Proline-Cortex
object QuantPostProcessingComputer {

  def apply(
       executionContext: IExecutionContext,
       masterQuantChannelId: Long,
       config: PostProcessingConfig,
       matrixFolder : Option [File] = None
   ): QuantPostProcessingComputer = {

    val udsDbCtx = executionContext.getUDSDbConnectionContext

    val udsDbHelper = new UdsDbHelper( udsDbCtx )
    val quantiId = udsDbHelper.getQuantitationId( masterQuantChannelId ).get

    val expDesignProvider = new SQLExperimentalDesignProvider(executionContext.getUDSDbConnectionContext)
    val expDesign = expDesignProvider.getExperimentalDesign(quantiId).get

    new QuantPostProcessingComputer(
      executionContext = executionContext,
      experimentalDesign = expDesign,
      masterQuantChannelId = masterQuantChannelId,
      config = config,
      matrixFolder
    )
  }

}

class QuantPostProcessingComputer(
  executionContext: IExecutionContext,
  experimentalDesign: ExperimentalDesign,
  masterQuantChannelId: Long,
  config: PostProcessingConfig,
  matrixFolder : Option [File] = None
) extends IService with LazyLogging {
  
  require( executionContext.isJPA,"invalid type of executionContext, JPA type is required")
  
  private var _hasInitiatedExecContext: Boolean = false

  // Secondary constructor
  def this(
    dsFactory: IDataStoreConnectorFactory,
    projectId: Long,
    experimentalDesign: ExperimentalDesign,
    masterQuantChannelId: Long,
    config: PostProcessingConfig
  ) {
    this(
      BuildLazyExecutionContext(dsFactory, projectId, useJPA = true), // Force JPA context
      experimentalDesign,
      masterQuantChannelId,
      config
    )
    _hasInitiatedExecContext = true
  }


  private def getDefaultCorrectionMatrix(qMethod : QuantitationMethod): RealMatrix = {

    val purityCorrectionFileName = qMethod.getName.replace(" ","_")+".txt"
    this.logger.info("Search for purityCorrectionFileName : "+purityCorrectionFileName)
    if(matrixFolder.isEmpty || !matrixFolder.get.isDirectory)
      throw new RuntimeException(" NO DEFAULT purityCorrectionMatrix folder for "+purityCorrectionFileName)

    val matrixFile = matrixFolder.get.listFiles(new FilenameFilter {
        override def accept(dir: File, name: String): Boolean = {
          name.equals(purityCorrectionFileName)
        }
    })

    if(matrixFile.nonEmpty ){
      this.logger.info("Read from file : "+matrixFile.head.getAbsolutePath)
      val fileBSource =Source.fromFile(matrixFile.head)
      val readMatrix = fileBSource.getLines().mkString
      fileBSource.close()
      val readMatrixObj = ProfiJson.deserialize[Array[Array[Double]]](readMatrix)
      new Array2DRowRealMatrix(readMatrixObj)
    } else
        throw new RuntimeException(" NO DEFAULT purityCorrectionMatrix : "+purityCorrectionFileName)

  }

  def runService(): Boolean = {

    this.logger.info("Running service Quant Post Processing Computer.")

    // Get entity manager
    val udsDbCtx = executionContext.getUDSDbConnectionContext
    val udsEM = udsDbCtx.getEntityManager
    val udsDbHelper = new UdsDbHelper(udsDbCtx)

    // Retrieve the quantitation fraction
    val udsMasterQuantChannel = udsEM.find(classOf[MasterQuantitationChannel], masterQuantChannelId)
    require(udsMasterQuantChannel != null, "undefined master quant channel with id=" + udsMasterQuantChannel)

    val qChIdsSorted: Seq[Long] = udsMasterQuantChannel.getQuantitationChannels.asScala.toList.map(_.getId).sorted
    val isIsobaricQuant = udsMasterQuantChannel.getDataset.getMethod.getType.equals(QuantitationMethod.Type.ISOBARIC_TAGGING.toString) //false
    val purityCorrectionMatrix: Option[RealMatrix] = if (!isIsobaricQuant) None else {
      if (config.purityCorrectionMatrix.isDefined) {
        config.purityCorrectionMatrix
      } else {
        Some(getDefaultCorrectionMatrix(udsMasterQuantChannel.getDataset.getMethod))
      }
    }
    logger.debug("Isobaric  quanti ? "+isIsobaricQuant)
    logger.debug("usePurityCorrectionMatrix ? "+config.usePurityCorrectionMatrix)

    if(isIsobaricQuant && config.usePurityCorrectionMatrix && (purityCorrectionMatrix.isEmpty || purityCorrectionMatrix.get==null || !purityCorrectionMatrix.get.isSquare)){
        throw new RuntimeException(" INVALID purityCorrectionMatrix") //for tests ...
    }

    val quantRsmId = udsMasterQuantChannel.getQuantResultSummaryId
    val qcIds = udsDbHelper.getQuantChannelIds(masterQuantChannelId)
    
    // --- 1.1 Load the Quant RSM --- //
    logger.info("Loading the quantitative result summary #"+quantRsmId)
    val quantRsmProvider = new SQLQuantResultSummaryProvider(PeptideCacheExecutionContext(executionContext))

    // Note that it is important to load the Result Set to have all required information
    val quantRSM = quantRsmProvider.getQuantResultSummary(quantRsmId, qcIds, loadResultSet = true, loadProteinMatches = None, loadReporterIons = Some( true)).get
    logger.info("Before Feature summarizer mqPep with selection level == 1 : "+quantRSM.masterQuantPeptides.count(_.selectionLevel == 1))

    // !!! STILL EXPERIMENTAL !!!
    val summarizeFeatures = false
    if( summarizeFeatures ) {
      /// VDS Warning: No code update for Reporter ions purity correction !
      logger.warn("!!! STILL EXPERIMENTAL CODE FEATURE SUMMARIZER !!! NOT Up To Date ")
      // --- 1.2 Load the peakels --- //
      val qcByLcMsMapId = experimentalDesign.masterQuantChannels.head.quantChannels.map { qc =>
        qc.lcmsMapId.get -> qc
      }.toMap
      val lcmsDbCtx = executionContext.getLCMSDbConnectionContext
      val mapSetProvider = new SQLMapSetProvider(lcmsDbCtx = lcmsDbCtx)
      val mapSet = mapSetProvider.getMapSet(udsMasterQuantChannel.getLcmsMapSetId, loadPeakels = true)
      val masterFtById = mapSet.masterMap.features.toSeq.view.map( ft => ft.id -> ft ).toMap
      
      // --- 1.3 Apply some corrections to the MQ peptide ions --- //
      logger.info("Applying some corrections to the master quant peptide ions...")
      
      val ftSummarizer = new FeatureSummarizer(
        peakelPreProcessingMethod = PeakelPreProcessingMethod.NONE,
        peakelSummarizingMethod = PeakelSummarizingMethod.APEX_INTENSITY,
        featureSummarizingMethod = FeatureSummarizingMethod.HIGHEST_THEORETICAL_ISOTOPE
      )
      
      for( mqPep <- quantRSM.masterQuantPeptides ) {
        val mqPepIons = mqPep.masterQuantPeptideIons
        
        // Update master quant peptides ions
        for( mqPepIon <- mqPepIons) {
          
          val qPepIonMap = mqPepIon.quantPeptideIonMap
          val masterFt = masterFtById(mqPepIon.lcmsMasterFeatureId.get)
          
          // Index features by their sample number
          val ftQcIds = new ArrayBuffer[Long](masterFt.children.length)
          val indexedFts = masterFt.children.map{ft:Feature =>
            val qc = qcByLcMsMapId(ft.relations.processedMapId)
            val qcId = qc.id
            ftQcIds += qcId
            
            val qPepIon = qPepIonMap(qcId)
            val qPepIonFtId = qPepIon.lcmsFeatureId.getOrElse(0)
            
            // Retrieve the feature to use as a reference for this cluster
            val mainClusterFt = if(!ft.isCluster) ft
            else {
              val ftOpt = ft.subFeatures.find(_.id == qPepIonFtId)
              if( ftOpt.isDefined) ftOpt.get
              else {
                ft.subFeatures.maxBy(_.intensity)//VDS Warning maxBy may return wrong value if NaN
              }
            }
            /*if(mainClusterFt.relations.peakelItems.isEmpty) {
              println(mainClusterFt)
            }*/
            
            mainClusterFt -> qc.sampleNumber
          }
          
          // Re-compute the features intensities
          val ftIntensities = ftSummarizer.computeFeaturesIntensities(indexedFts)
          if( ftIntensities.count(_.isNaN == false) == 0) {
            /*println(indexedFts.map(_._1.intensity).toList)
            println(ftIntensities.toList)*/
          }
          
          val nonReliableFtsCount =  masterFt.children.count( _.properties.flatMap(_.getIsReliable()).getOrElse(true) == false )
            if (nonReliableFtsCount > 0) {
              masterFt.selectionLevel = 0
          }
            
          val newQPepIonMap = for( (ftIntensity,qcId) <- ftIntensities.zip(ftQcIds) ) yield {
//            val qPepIon = qPepIonMap(qcId).copy( rawAbundance = ftIntensity, abundance = ftIntensity )
            val qPepIon = qPepIonMap(qcId).copy()
            qcId -> qPepIon
          }
          
          mqPepIon.quantPeptideIonMap = newQPepIonMap.toLongMap
          mqPepIon.selectionLevel = masterFt.selectionLevel
        }
        
        // Re-build the master quant peptides
        val newMqPep = BuildMasterQuantPeptide(mqPepIons, mqPep.peptideInstance, mqPep.resultSummaryId,config.pepIonAbundanceSummarizingMethod)
        //WARNING: If this code is activated, verify previous selectionlevel to allow or not its modification
        mqPep.selectionLevel = newMqPep.selectionLevel
        // the next step is mandatory since BuildMasterQuantPeptide updates mqPepIons.masterQuantPeptideId to the new MasterQuantPeptide
        mqPepIons.foreach { mqPepIon =>
            mqPepIon.masterQuantPeptideId = mqPep.id
        }
        //Get properties back
        mqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig= newMqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig
        mqPep.quantPeptideMap = newMqPep.quantPeptideMap
      }
      
      logger.info("After Feature summarizer mqPep with selection level == 0 : "+quantRSM.masterQuantPeptides.withFilter(_.selectionLevel == 0).map(_.id).length)
      quantRSM.masterQuantPeptides
      
    } // end of summarizeFeatures

    //
    // Reset mq peptide selection level, only for AUTO values
    //
    quantRSM.masterQuantPeptides.foreach { mqPep => if (mqPep.selectionLevel == 1) mqPep.selectionLevel = 2 }

    //
    // Reset ions abundances and reporter ions abundances if defined to raw abundances
    //
    val masterQuantPeptideIons = quantRSM.masterQuantPeptides.flatMap(_.masterQuantPeptideIons)
    logger.info(" Pre-first step : Reset & compute new peptideIons/Reporter ions abundances")
    for (mqPepIon <- masterQuantPeptideIons) {
      mqPepIon.setAbundancesForQuantChannels(mqPepIon.getRawAbundancesForQuantChannels(qcIds), qcIds)
      if(isIsobaricQuant)
        _resetAndComputeIonsAbundance(mqPepIon, qChIdsSorted, purityCorrectionMatrix)
    }

    //
    // Change mqPeptide selection level sharing peakels of mqPep sharing features
    //
    logger.info("Run first step : discardPeptidesSharingPeakels or just recompute MQPep Abundance . discardPeptidesSharingPeakels : "+config.discardPepIonsSharingPeakels)
    if (config.discardPepIonsSharingPeakels) {

      val lcmsDbCtx = executionContext.getLCMSDbConnectionContext
      val mapSetProvider = new SQLMapSetProvider(lcmsDbCtx = lcmsDbCtx)
      val mapSet = mapSetProvider.getMapSet(udsMasterQuantChannel.getLcmsMapSetId, loadPeakels = true)
      val masterFtById = mapSet.masterMap.features.toSeq.view.map(ft => ft.id -> ft).toMap

      val mqPepIonIdsByFeatureId = mutable.LongMap[mutable.Set[Long]]()
      quantRSM.masterQuantPeptides.flatMap(_.masterQuantPeptideIons).foreach { mqPepIon => 
        val masterFt = masterFtById(mqPepIon.lcmsMasterFeatureId.get)
        val childFts = masterFt.children.flatMap{ft:Feature => if(!ft.isCluster) Array(ft) else ft.subFeatures }
        childFts.foreach {f:Feature =>
          mqPepIonIdsByFeatureId.getOrElseUpdate(f.id, scala.collection.mutable.Set[Long]()) += mqPepIon.id
        }
      }
      
      val mqPepIonWithSharedFeatures = mqPepIonIdsByFeatureId.filter( _._2.size > 1).flatMap(_._2).toSet

      for (mqPep <- quantRSM.masterQuantPeptides) {

        val mqPepIons = mqPep.masterQuantPeptideIons
        
        for (mqPepIon <- mqPepIons) {
          val masterFt = masterFtById(mqPepIon.lcmsMasterFeatureId.get)
          val sharedFtsCount = masterFt.children.count{ft:Feature =>
            val mainFt = if (!ft.isCluster) ft else ft.subFeatures.maxBy(_.intensity)//VDS Warning maxBy may return wrong value if NaN
            mainFt.getBasePeakel().getOrElse(mainFt.relations.peakelItems(0).getPeakel().get).featuresCount > 1
          }

          if ((sharedFtsCount > 0) || mqPepIonWithSharedFeatures.contains(mqPepIon.id)) {
//            logger.info("master feature deselected due to " + sharedFtsCount + " shared peakels over " + masterFt.children.length)
            masterFt.selectionLevel = 1
          }
          
          mqPepIon.selectionLevel = masterFt.selectionLevel
        }

        //----  Re-build the master quant peptides
        val newMqPep = BuildMasterQuantPeptide(mqPepIons, mqPep.peptideInstance, mqPep.resultSummaryId, config.pepIonAbundanceSummarizingMethod)

        //-- Update selection level
        //If mqPep.selection_level is AUTO, allow change, if selection_level is DESELEC_MANUAL don't change, if selection_level is SELECT_MANUAL allow change to DESELEC_AUTO
        mqPep.selectionLevel match {
          case SelectionLevel.SELECTED_AUTO |  SelectionLevel.DESELECTED_AUTO => mqPep.selectionLevel = newMqPep.selectionLevel
          case SelectionLevel.SELECTED_MANUAL => {
            if(newMqPep.selectionLevel == SelectionLevel.DESELECTED_AUTO)
              mqPep.selectionLevel = newMqPep.selectionLevel
          }
          case SelectionLevel.DESELECTED_MANUAL => {}
        }

        //-- Update properties
        mqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig = newMqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig

        // the next step is mandatory since BuildMasterQuantPeptide updates mqPepIons.masterQuantPeptideId to the new MasterQuantPeptide
        mqPepIons.foreach { mqPepIon =>
          mqPepIon.masterQuantPeptideId = mqPep.id
        }

        // update abundances only
        val abundances = newMqPep.getAbundancesForQuantChannels(qcIds)
        mqPep.setAbundancesForQuantChannels(abundances, qcIds)
      }
      logger.info("After discardPeptidesSharingPeakels  : " + quantRSM.masterQuantPeptides.withFilter(_.selectionLevel < 2).map(_.id).length)

    } else {
      //If discardPeptidesSharingPeakels : mqPeptide abundance has been recalculated, otherwise force recomputing of abundance
      //To do ?? Check MqPeptide previous method => in quant config (label free config) or in first/all MQPep (if post processing already run)
      for (mqPep <- quantRSM.masterQuantPeptides) {
        val mqPepIons = mqPep.masterQuantPeptideIons

        //----  Re-build the master quant peptides
        val newMqPep = BuildMasterQuantPeptide(mqPepIons, mqPep.peptideInstance, mqPep.resultSummaryId, config.pepIonAbundanceSummarizingMethod)

        //-- Update selection level
        //allow change only if mqPep.selection_level is AUTO. No changed done on Ions so keep peptides manual selection
        mqPep.selectionLevel match {
          case SelectionLevel.SELECTED_AUTO |  SelectionLevel.DESELECTED_AUTO => mqPep.selectionLevel = newMqPep.selectionLevel
          case default =>
        }

        //-- Update properties
        mqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig = newMqPep.properties.getOrElse(MasterQuantPeptideProperties()).mqPepIonAbundanceSummarizingConfig

        // the next step is mandatory since BuildMasterQuantPeptide updates mqPepIons.masterQuantPeptideId to the new MasterQuantPeptide
        mqPepIons.foreach { mqPepIon =>
          mqPepIon.masterQuantPeptideId = mqPep.id
        }

        // update abundances only
        val abundances = newMqPep.getAbundancesForQuantChannels(qcIds)
        mqPep.setAbundancesForQuantChannels(abundances, qcIds)
      }
      logger.info("After mqPeptide abundance has been recalculated")
    }

    // --- 2. Instantiate the profilizer --- //
    val profilizer = new Profilizer(
      expDesign = experimentalDesign,
      groupSetupNumber = 1, // TODO: retrieve from params
      masterQCNumber = udsMasterQuantChannel.getNumber
    )
    
    // --- 3. Compute MasterQuantPeptides profiles --- //
    profilizer.computeMasterQuantPeptideProfiles(quantRSM.masterQuantPeptides, config)

    // --- 4. Compute MasterQuantProtSets profiles --- //
    profilizer.computeMasterQuantProtSetProfiles(quantRSM.masterQuantProteinSets, config)

    // --- 5. Update MasterQuantPeptides and MasterQuantProtSets properties --- //
    val msiDbCtx = executionContext.getMSIDbConnectionContext
    updateMasterQuantData(msiDbCtx, quantRSM, qcIds)

    udsDbCtx.beginTransaction()
    
    // Save profilizerConfigSchema as an ObjectTree
    val profilizerConfigSchemaName = ObjectTreeSchema.SchemaName.POST_QUANT_PROCESSING_CONFIG.getKeyName
    val profilizerConfigSchema = ObjectTreeSchemaRepository.loadOrCreateObjectTreeSchema(udsEM,profilizerConfigSchemaName)
    val profilizerConfigObjectTree = new ObjectTree()
    profilizerConfigObjectTree.setSchema(profilizerConfigSchema)
    profilizerConfigObjectTree.setClobData(ProfiJson.serialize(config))
    udsEM.persist(profilizerConfigObjectTree)

    // Link the ObjectTree to the quantitation dataset
    val udsQuantitation = udsMasterQuantChannel.getDataset
    udsQuantitation.putObject(profilizerConfigSchemaName, profilizerConfigObjectTree.getId())
    udsEM.merge(udsQuantitation)
    
    udsDbCtx.commitTransaction()
    
    // Close execution context if initiated locally
    if( this._hasInitiatedExecContext ) executionContext.closeAll()

    this.logger.info("Exiting Quant Post Processing service.")
    
    true
  }

  private def updateMasterQuantData(msiDbCtx: MsiDbConnectionContext,  quantRSM: QuantResultSummary, qcIds: Array[Long]): Unit = {
    DoJDBCWork.tryTransactionWithEzDBC(msiDbCtx) { ezDBC =>

      // TODO: create an UPDATE query builder
      val mqComponentUpdateQuery = "UPDATE master_quant_component SET selection_level = ?, serialized_properties = ? WHERE id = ?"
      val objTreeUpdateQuery = "UPDATE object_tree SET clob_data = ? " +
        "WHERE object_tree.id IN (SELECT object_tree_id FROM master_quant_component WHERE master_quant_component.id = ?)"
      val objTreeIonUpdateQuery = "UPDATE object_tree SET clob_data = ? " +
        "WHERE object_tree.id IN (SELECT master_quant_component.object_tree_id FROM master_quant_component,master_quant_peptide_ion  WHERE master_quant_component.id = master_quant_peptide_ion.master_quant_component_id AND master_quant_peptide_ion.id = ?)"
      val objTreeRepIonUpdateQuery = "UPDATE object_tree SET clob_data = ? " +
        "WHERE object_tree.id IN (SELECT master_quant_component.object_tree_id FROM master_quant_component,master_quant_reporter_ion  WHERE master_quant_component.id = master_quant_reporter_ion.master_quant_component_id AND master_quant_reporter_ion.id = ?)"


      ezDBC.executeInBatch(mqComponentUpdateQuery) { mqComponentUpdateStmt =>
        ezDBC.executeInBatch(objTreeUpdateQuery) { objTreeUpdateStmt =>


          this.logger.info("Updating MasterQuantPeptideIons and MasterQuantReporterIons...")

          for (mqPepIon <- quantRSM.masterQuantPeptideIons) {
            // Retrieve quant peptides sorted by quant channel
            val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
            val quantPeptideIons = qcIds.map {
              quantPeptideIonMap.getOrElse(_, null)
            }

            ezDBC.executeInBatch(objTreeIonUpdateQuery) { objTreeIonUpdateStmt =>
              // Update MasterQuantPeptideIons object tree
              objTreeIonUpdateStmt.executeWith(ProfiJson.serialize(quantPeptideIons), mqPepIon.id)
            }

            // Update associated MasterQuantReporterIons object tree
            mqPepIon.masterQuantReporterIons.foreach(mqRepIon => {
              val qRepIonMap = mqRepIon.quantReporterIonMap
              val quantRepIons = qcIds.map {
                qRepIonMap.getOrElse(_, null)
              }

              ezDBC.executeInBatch(objTreeRepIonUpdateQuery) { objTreeRepIonUpdateStmt =>
                // Update MasterQuantReporterIons object tree
                objTreeRepIonUpdateStmt.executeWith(ProfiJson.serialize(quantRepIons), mqRepIon.id)
              }
            })
          }

          this.logger.info("Updating MasterQuantPeptides...")

          // Iterate over MasterQuantPeptides
          for (mqPep <- quantRSM.masterQuantPeptides) {

            // Update MasterQuantPeptides selection level and properties
            mqComponentUpdateStmt.executeWith(
              mqPep.selectionLevel,
              mqPep.properties.map(props => ProfiJson.serialize(props)),
              mqPep.id
            )

            // Retrieve quant peptides sorted by quant channel
            val quantPeptideMap = mqPep.quantPeptideMap
            val quantPeptides = qcIds.map {
              quantPeptideMap.getOrElse(_, null)
            }

            // Update MasterQuantPeptides object tree
            objTreeUpdateStmt.executeWith(
              ProfiJson.serialize(quantPeptides),
              mqPep.id
            )
          }

          this.logger.info("Updating MasterQuantProtSets...")

          // Iterate over MasterQuantProtSets
          for (mqProtSet <- quantRSM.masterQuantProteinSets) {

            // Update MasterQuantProtSets selection level and properties
            mqComponentUpdateStmt.executeWith(
              mqProtSet.selectionLevel,
              mqProtSet.properties.map(props => ProfiJson.serialize(props)),
              mqProtSet.getMasterQuantComponentId()
            )

            // Retrieve quant protein sets sorted by quant channel
            val quantProtSetMap = mqProtSet.quantProteinSetMap
            val quantProtSets = qcIds.map {
              quantProtSetMap.getOrElse(_, null)
            }

            // Update MasterQuantProtSets object tree
            objTreeUpdateStmt.executeWith(
              ProfiJson.serialize(quantProtSets),
              mqProtSet.getMasterQuantComponentId()
            )
          }
        } // END OF executeInBatch(objTreeUpdateQuery)
      } // END OF executeInBatch(mqComponentUpdateQuery)
    } // END OF tryTransactionWithEzDBC
  }

  private def _resetAndComputeIonsAbundance(mqPepIon: MasterQuantPeptideIon, qChIdsSorted : Seq[Long], purityCorrectionMatrix : Option[RealMatrix]  ) : Unit = {
    val values  = new ArrayBuffer[Array[Double]](1)
    values += Array.empty[Double]

    // val skipPurityCorrection = false //VDS TODO For temp version. To remove
    val mqReporterIonAbundanceMatrix =  new ArrayBuffer[Array[Float]](qChIdsSorted.size)
    val tPurityCorrectionMatrix = if(config.usePurityCorrectionMatrix) MathUtils.transposeMatrix(purityCorrectionMatrix.get.getData) else Array.empty[Array[Double]]

    // If reporter ions, reset values
    mqPepIon.masterQuantReporterIons.foreach(mqRepIon => {

      if (/*!skipPurityCorrection &&*/ config.usePurityCorrectionMatrix) { //Compute Abundance using purityCorrectionMatrix
        val psmIntensitySortedByQChId = mqRepIon.getRawAbundancesForQuantChannels(qChIdsSorted.toArray)
        val pepIntensityAsDoubleArray = new Array[Double](qChIdsSorted.size)
        for (i <- qChIdsSorted.indices) {
          if (psmIntensitySortedByQChId.apply(i).isNaN) {
            pepIntensityAsDoubleArray.update(i, 0)
          } else
            pepIntensityAsDoubleArray.update(i, psmIntensitySortedByQChId.apply(i).toDouble)
        }

        values.update(0, pepIntensityAsDoubleArray)
        val peptideCorrectedValues = MathUtils.matrixSolver(tPurityCorrectionMatrix, values.toArray, true)
        val pepIntensityFinalValue = new Array[Float](peptideCorrectedValues(0).length)
        for (i <- qChIdsSorted.indices) {
          if (psmIntensitySortedByQChId.apply(i).isNaN) { //There was NO intensity before ...
            pepIntensityFinalValue.update(i, Float.NaN)
          } else
            pepIntensityFinalValue.update(i, peptideCorrectedValues(0)(i).toFloat)
        }
        mqRepIon.setAbundancesForQuantChannels(pepIntensityFinalValue, qChIdsSorted)
        //end usePurityCorrectionMatrix

        val qRepIonMap = mqRepIon.quantReporterIonMap
        mqReporterIonAbundanceMatrix += qChIdsSorted.map( qRepIonMap.get(_).map(_.abundance).getOrElse(Float.NaN) ).toArray

      } else {
        //reset to previous value
        mqRepIon.setAbundancesForQuantChannels(mqRepIon.getRawAbundancesForQuantChannels(qChIdsSorted.toArray), qChIdsSorted)
      }
    }) //end for each mqRepIon

    if (/*!skipPurityCorrection &&*/ config.usePurityCorrectionMatrix) {
      // Summarize abundance matrix only if abundance has been calculated
      val summarizedRawAbundanceMatrix = AbundanceSummarizer.summarizeAbundanceMatrix(
        mqReporterIonAbundanceMatrix.toArray,
        MqPeptideAbundanceSummarizingMethod.SUM
      )
      mqPepIon.setAbundancesForQuantChannels(summarizedRawAbundanceMatrix, qChIdsSorted)
    }
  }
}