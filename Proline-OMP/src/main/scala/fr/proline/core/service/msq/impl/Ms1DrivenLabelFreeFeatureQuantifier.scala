package fr.proline.core.service.msq.impl

import collection.JavaConversions._
import collection.JavaConverters.asJavaCollectionConverter
import collection.mutable.{HashMap,HashSet}
import collection.mutable.ArrayBuffer
import javax.persistence.EntityManager
import com.weiglewilczek.slf4s.Logging
import com.codahale.jerkson.Json.generate
import fr.proline.core.algo.msi.ResultSummaryMerger
import fr.proline.core.dal.{DatabaseManagement,LcmsDb,MsiDb,PsDb,MsiDbSpectrumTable}
import fr.proline.core.service.msq.IQuantifier
import fr.proline.core.utils.ms._
import fr.proline.core.om.model.msi._
import fr.proline.core.om.model.msq._
import fr.proline.core.om.provider.msi.impl.SQLResultSummaryProvider
import fr.proline.core.orm.uds.{QuantitationFraction => UdsQuantFraction}
import fr.proline.core.orm.msi.{MasterQuantPeptideIon => MsiMasterQuantPepIon,
                                MasterQuantComponent => MsiMasterQuantComponent,
                                ObjectTree => MsiObjectTree,
                                PeptideInstance => MsiPeptideInstance,
                                PeptideInstancePeptideMatchMap => MsiPepInstPepMatchMap,
                                PeptideInstancePeptideMatchMapPK => MsiPepInstPepMatchMapPK,
                                PeptideMatch => MsiPeptideMatch,
                                PeptideMatchRelation => MsiPeptideMatchRelation,
                                PeptideSet => MsiPeptideSet,
                                PeptideSetPeptideInstanceItem => MsiPeptideSetItem,
                                PeptideSetPeptideInstanceItemPK => MsiPeptideSetItemPK,
                                //PeptideSetProteinMatchMap => MsiPepSetProtMatchMap,
                                ProteinSetProteinMatchItem => MsiProtSetProtMatchItem,
                                ProteinSetProteinMatchItemPK => MsiProtSetProtMatchItemPK,
                                ProteinMatch => MsiProteinMatch,
                                ProteinSet => MsiProteinSet,
                                ResultSet => MsiResultSet,
                                ResultSummary => MsiResultSummary,
                                SequenceMatch => MsiSequenceMatch
                                }



class Ms1DrivenLabelFreeFeatureQuantifier(
        val dbManager: DatabaseManagement,
        val udsEm: EntityManager,
        val udsQuantFraction: UdsQuantFraction
        ) extends IQuantifier with Logging {
  
  // Retrieve some vars
  val projectId = udsQuantFraction.getQuantitation.getProject.getId
  // TODO: retrieve the LCMSdb using the dbManager when it is implemented
  val lcmsDb = LcmsDb(projectId)
  val msiDb = MsiDb( dbManager, projectId )
  val msiEm = msiDb.entityManager
  // TODO: retrieve the PSdb using the dbManager when it is implemented
  val psDbConfig = PsDb.buildConfigFromDatabaseConnector( dbManager.psDBConnector )
  val psDb = new PsDb( psDbConfig )
  
  // TODO: require some parameters
  val mozTolInPPM = 10
  
  val udsQuantChannels = this.udsQuantFraction.getQuantitationChannels
  val quantChannelIds = udsQuantChannels.map { _.getId } toArray
  
  val identRsIdByRsmId = {
    
    val rsmIds = udsQuantChannels.map { _.getIdentResultSummaryId }
    val msiDbTx = this.msiDb.getOrCreateTransaction
    msiDbTx.select( "SELECT id, result_set_id FROM result_summary WHERE id IN("+rsmIds.mkString(",")+")" ) { r =>
      ( r.nextInt.get -> r.nextInt.get )
    } toMap
    
  }
  
  val msiIdentResultSets = {   
    val identRsIds = identRsIdByRsmId.values.asJavaCollection  
    msiEm.createQuery("FROM fr.proline.core.orm.msi.ResultSet WHERE id IN (:ids)",
                      classOf[fr.proline.core.orm.msi.ResultSet] )
                      .setParameter("ids", identRsIds).getResultList().toList
  }
  
  val identRsIdByLcmsMapId = {
    udsQuantChannels.map { qc => qc.getLcmsMapId.intValue -> identRsIdByRsmId(qc.getIdentResultSummaryId) } toMap
  }
  
  val quantChannelIdByLcmsMapId = {
    udsQuantChannels.map { qc => qc.getLcmsMapId.intValue -> qc.getId.intValue } toMap
  }
  
  val lcmsMapIdByIdentRsId = {   
    udsQuantChannels.map { qc => identRsIdByRsmId(qc.getIdentResultSummaryId) -> qc.getLcmsMapId.intValue } toMap
  }
  
  val lcmsMapSet = {    
    
    val mapSetId = udsQuantFraction.getLcmsMapSetId()    

    if( mapSetId < 1 ) throw new Exception( "a LCMS map set must be created first" )
      //require Pairs::Lcms::Module::Loader::MapSet
      //val mapSetLoader = new Pairs::Lcms::Module::Loader::MapSet()
      //mapSet = mapSetLoader.getMapSet( mapSetId )
    
    this.logger.info( "loading LCMS map set..." )
    val mapSetLoader = new fr.proline.core.om.provider.lcms.impl.MapSetLoader( lcmsDb )
    mapSetLoader.getMapSet(mapSetId)

  }
  
  val lcmsMapIds = lcmsMapSet.getChildMapIds
  
  val lcmsRunIds = {
    val runMapIds = lcmsMapSet.getRunMapIds
    val lcmsDbTx = lcmsDb.getOrCreateTransaction()
    lcmsDbTx.select( "SELECT run_id FROM run_map WHERE id IN("+runMapIds.mkString(",")+")" ) { _.nextInt.get }
  }
  
  // Retrieve corresponding peaklist ids
  // TODO: update the ORM definition so that peaklistId is available from msiSearch object    
  val identRsIdByPeaklistId = msiIdentResultSets map { rs => rs.getMsiSearch.getPeaklist.getId -> rs.getId } toMap
  val peaklistIds = this.identRsIdByPeaklistId.keys
  //val peaklistIds = msiIdentResultSets map { _.getMsiSearch().getPeaklist().getId() }
  
  val ms2SpectrumHeaders = {
    
    // Load MS2 spectrum headers
    this.logger.info( "loading MS2 spectrum headers..." )
    
    var spectrumColNames: Seq[String] = null
    val msiDbTx = msiDb.getOrCreateTransaction()
    msiDbTx.select( "SELECT id,first_cycle,first_scan,first_time,peaklist_id FROM spectrum WHERE peaklist_id IN("+
                    peaklistIds.mkString(",")+")" ) { r =>
      if( spectrumColNames == null ) { spectrumColNames = r.columnNames }
      spectrumColNames.map( colName => ( colName -> r.nextObject.get ) ).toMap
    }
    
  }
  
  val spectrumIdMap = {
    
    val firstScanColName = MsiDbSpectrumTable.columns.firstScan
    val peaklistIdColName = MsiDbSpectrumTable.columns.peaklistId
    
    // Map spectrum id by scan number and result set id
    val spectrumIdMap = HashMap[Int,HashMap[Int,Int]]()
    
    for( spectrumHeader <- this.ms2SpectrumHeaders ) {
      
      if( spectrumHeader(firstScanColName) == null ) {
        throw new Exception("a scan id must be defined for each MS2 spectrum")
      }      
      
      val identRsId = identRsIdByPeaklistId( spectrumHeader(peaklistIdColName).asInstanceOf[Int] )
      val scanNumber = spectrumHeader(firstScanColName).asInstanceOf[Int]
      val spectrumId = spectrumHeader("id").asInstanceOf[Int]
      
      spectrumIdMap.getOrElseUpdate(identRsId, new HashMap[Int,Int] )(scanNumber) = spectrumId
    }
    
    spectrumIdMap.toMap
  }
  
  val ms2ScanHeaderRecords = {   
    this.logger.info( "loading MS2 scan headers..." )
    val runIds = this.lcmsRunIds
    this.lcmsDb.selectRecordsAsMaps( "SELECT id, initial_id, cycle, time FROM scan WHERE ms_level = 2 AND run_id IN ("+
                                     runIds.mkString(",")+")")
  }
   
  val ms2ScanNumbersByFtId = {
    
    val ms2ScanNumberById = ms2ScanHeaderRecords.map { r =>
      r("id").asInstanceOf[Int] -> r("initial_id").asInstanceOf[Int]
    } toMap
    
    
    val lcmsMapSet = this.lcmsMapSet
    val runMapIds = lcmsMapSet.getRunMapIds
    
    this.logger.info( "loading MS2 scans/features map..." )
    val ms2ScanNumbersByFtId = new HashMap[Int,ArrayBuffer[Int]]
    
    val lcmsDbTx = this.lcmsDb.getOrCreateTransaction()
    lcmsDbTx.selectAndProcess( "SELECT feature_id, ms2_event_id FROM feature_ms2_event WHERE run_map_id IN("+
                               runMapIds.mkString(",")+")") { r =>
      val( featureId, ms2ScanId ) = ( r.nextInt.get, r.nextInt.get )
      val ms2ScanNumber = ms2ScanNumberById(ms2ScanId)
      ms2ScanNumbersByFtId.getOrElseUpdate(featureId, new ArrayBuffer[Int] ) += ms2ScanNumber
    }
    
    ms2ScanNumbersByFtId.toMap
  }  

  
  def quantify(): Unit = {
    
    // Instantiate a RSM provider
    val rsmProvider = new SQLResultSummaryProvider( msiDb, psDb )
    
    //val( resultSummaries, identRsIds, proteinIdMap, pepInstanceById, pepInstanceIdByPepMatchById )
    // Define some vars
    val identResultSummaries = new collection.mutable.ListBuffer[ResultSummary]()
    val identPepInstById = new HashMap[Int,PeptideInstance]()
    val identPepMatchById = new HashMap[Int,PeptideMatch]()
    val identPepInstIdByPepMatchId = new HashMap[Int,Int]()
    val identProteinIdSet = new collection.mutable.HashSet[Int]()
    
    // Load the result summaries corresponding to the quant channels
    for( quantChannel <- udsQuantChannels ) {
      
      val qcId = quantChannel.getId()
      
      // Retrieve the result summary
      val identRsmId = quantChannel.getIdentResultSummaryId
      if( identRsmId == 0 ) {
        throw new Exception("the quant_channel with id='"+qcId+"' is not assocciated with an identification result summary")
      }
      
      this.logger.info( "loading result summary..." )
      val identResultSummary = rsmProvider.getResultSummary( identRsmId, true ).get
      
      // Map peptide instances
      val peptideInstances = identResultSummary.peptideInstances
      for( pepInstance <- peptideInstances ) {
        val pepInstanceId = pepInstance.id
        identPepInstById(pepInstanceId) = pepInstance
        pepInstance.getPeptideMatchIds.foreach { identPepInstIdByPepMatchId(_) = pepInstanceId }
      }
      
      // Retrieve protein ids
      val rs = identResultSummary.resultSet.get
      rs.proteinMatches.foreach { p => if( p.getProteinId != 0 ) identProteinIdSet += p.getProteinId }
      
      // Map peptide matches by their id
      rs.peptideMatches.foreach { p => identPepMatchById(p.id) = p }
      
      identResultSummaries += identResultSummary
      
    }
    
    // Retrieve sequence length mapped by the corresponding protein id
    val seqLengthByProtId = this.msiDbHelper.getSeqLengthByBioSeqId( identProteinIdSet.toList )
    
    // Merge result summaries
    val resultSummaryMerger = new ResultSummaryMerger()
    this.logger.info( "merging result summaries..." )
    val mergedResultSummary = resultSummaryMerger.mergeResultSummaries( identResultSummaries, seqLengthByProtId )
    
    // Retrieve peptide matches of the merged result summary
    val identPeptideMatches = mergedResultSummary.resultSet.get.peptideMatches
    val identPepMatchBySpectrumId = identPeptideMatches.map { p => p.getMs2Query.spectrumId -> p } toMap
    //val childPepMatchById = map { _.id = _ } childPeptideMatches
    
    // Retrieve some vars 
    val refMapAlnSetByMapId = lcmsMapSet.getRefMapAlnSetByMapId.get
    val masterMap = lcmsMapSet.masterMap
    //val alnRefMapId = lcmsMapSet.alnRefMapId
    
    val pepInstIdSetByFtId = new HashMap[Int,HashSet[Int]]
    val masterFeatures = masterMap.features
    for( masterFt <- masterFeatures ) {
      
      // Iterate over each master feature child
      val ftChildren = masterFt.children
      for( ftChild <- ftChildren ) {
        
        val childMapId = ftChild.relations.mapId        
        
        // Check if we have a result set for this map (this map may be in the map_set but not used)
        if( identRsIdByLcmsMapId.contains(childMapId) ) {
        
          val identRsId = identRsIdByLcmsMapId( childMapId )
          val specIdByScanNum = spectrumIdMap(identRsId)
          
          // Retrieve MS2 scan numbers which are related to this feature
          val tmpFtIds = if( ftChild.isCluster ) ftChild.subFeatures.map { _.id } else Array(ftChild.id)
          val ms2ScanNumbers = tmpFtIds.flatMap { i => ms2ScanNumbersByFtId.getOrElse( i, ArrayBuffer.empty[Int] ) }
          
          // Stop if no MS2 available
          if( ms2ScanNumbers.length > 0 ) {
                      
            // Retrieve corresponding MS2 spectra ids and remove existing redundancy
            val scanNumBySpecId = ms2ScanNumbers.filter( specIdByScanNum.contains(_) )
                                                .map( n => (specIdByScanNum(n) -> n) )
                                                .toMap
            val nrSpectrumIds = scanNumBySpecId.keys
            
            // Retrieve corresponding peptide matches if they exist
            for( spectrumId <- nrSpectrumIds ) {
              if( identPepMatchBySpectrumId.contains(spectrumId) ) {
                val identPepMatch = identPepMatchBySpectrumId(spectrumId)
                
                // Check that peptide match charge is the same than the feature one
                val msQuery = identPepMatch.msQuery
                if( ftChild.charge == msQuery.charge ) {
                  
                  // TMP FIX FOR MOZ TOLERANCE OF FEATURE/MS2 SPECTRUM
                  val deltaMoz = math.abs(ftChild.moz - msQuery.moz)
                  val pepMozTolInDalton = calcMozTolInDalton( ftChild.moz, mozTolInPPM ,"ppm")
                  
                  if( deltaMoz <= pepMozTolInDalton ) {
                    val pepInstanceId = identPepInstIdByPepMatchId(identPepMatch.id)
                    pepInstIdSetByFtId.getOrElseUpdate(ftChild.id, new HashSet[Int]) += pepInstanceId
                  }
                  
                  //////// Map the current feature to the peptide match
                  //val pepMatchProperties = identPepMatch.properties
                  //if( defined pepMatchProperties(feature_id) )
                  //  {
                  //  die "the peptide match with id='".identPepMatch.id."' is already associated with a LCMS feature" .
                  //  pepMatchProperties(feature_id) . ftChild.dump
                  //  }
                  //pepMatchProperties(feature_id) = ftChild.id
                  //
                  //push( masterFtPepMatches, identPepMatch )
                }
              }
            }
          }
        }
      }
    }
    
    // Commit SQL transaction
    // FIXME: remove this line when transaction sharing is fixed
    val msiDbTx = this.msiDb.commitTransaction()
    
    // Begin new ORM transaction
    msiEm.getTransaction().begin()
    udsEm.getTransaction().begin()
    
    // Import LCMS features into peptide ions
    this.logger.info( "importing LCMS features..." )
    
    val rsmById = identResultSummaries.map { rsm => rsm.id -> rsm } toMap 
    val lcmsMapById = lcmsMapSet.childMaps.map { m => m.id -> m } toMap
    val normFactorByMapId = lcmsMapSet.getNormalizationFactorByMapId
    
    val quantPepIonsByFtId = new HashMap[Int,ArrayBuffer[QuantPeptideIon]]
    for( udsQuantChannel <- udsQuantChannels ) {
      
      // Retrieve some vars
      val udsQuantChannelId = udsQuantChannel.getId
      val identRsmId = udsQuantChannel.getIdentResultSummaryId
      val identResultSummary = rsmById(identRsmId)
      val lcmsMapId = udsQuantChannel.getLcmsMapId
      val lcmsMap = lcmsMapById(lcmsMapId)
      val normFactor = normFactorByMapId(lcmsMapId)
      
      val lcmsFeatures = lcmsMap.features
      for( feature <- lcmsFeatures ) {
        
        // Try to retrieve matching peptide instances
        var matchingPepInstsAsOpts = Array(Option.empty[PeptideInstance])
        if( pepInstIdSetByFtId.contains(feature.id) ) {
          matchingPepInstsAsOpts = pepInstIdSetByFtId(feature.id).toArray.map { i => Some(identPepInstById(i)) }
        }
        
        val ftIntensity = feature.intensity
        
        for( matchingPepInstAsOpt <- matchingPepInstsAsOpts ) {
          
          var( peptideId, pepInstId, pepMatchesCount) = (Option.empty[Int],Option.empty[Int],0)
          var( msQueryIds, bestPepMatchScore ) = (Option.empty[Array[Int]], Option.empty[Float])
          
          if( matchingPepInstAsOpt != None ) {
            val pepInst = matchingPepInstAsOpt.get
            peptideId = Some(pepInst.peptide.id)
            pepInstId = Some(pepInst.id)
            
            // Retrieve the number of peptide matches and the best peptide match score
            val pepMatches = pepInst.getPeptideMatchIds.map { identPepMatchById(_) }
            pepMatchesCount = pepMatches.length
            msQueryIds = Some( pepMatches.map { _.msQuery.id } )
            bestPepMatchScore = Some( pepMatches.reduce( (a,b) => if( a.score > b.score ) a else b ).score )
          }
          
          // Create a quant peptide ion corresponding the this LCMS feature
          val quantPeptideIon = new QuantPeptideIon(
                                      rawAbundance = ftIntensity.toFloat,
                                      abundance = ftIntensity.toFloat * normFactor,
                                      moz = feature.moz,
                                      elutionTime = feature.elutionTime,
                                      scanNumber = feature.relations.apexScanInitialId,
                                      peptideMatchesCount = pepMatchesCount,
                                      bestPeptideMatchScore = bestPepMatchScore,
                                      quantChannelId = udsQuantChannelId,
                                      peptideId = peptideId,
                                      peptideInstanceId = pepInstId,
                                      msQueryIds = msQueryIds,
                                      lcmsFeatureId = feature.id
                                      )
          quantPepIonsByFtId.getOrElseUpdate( feature.id, new ArrayBuffer[QuantPeptideIon]() ) += quantPeptideIon
        }
      }
    }
    
    val curSQLTime = new java.sql.Timestamp(new java.util.Date().getTime)
    
    // TODO: provide the RS name in the parameters
    val msiQuantResultSet = new MsiResultSet()
    msiQuantResultSet.setName("")
    msiQuantResultSet.setType(MsiResultSet.Type.QUANTITATION)
    msiQuantResultSet.setModificationTimestamp(curSQLTime)
    msiEm.persist(msiQuantResultSet)
    
    //val quantRsId = msiQuantResultSet.getId
    
    // Link this quantitative result set to the ident target MSI searches
    /*val rdbIdentMsiSearches = this.msiIdentResultSets.map { _.getMsiSearch() }
    for( rdbIdentMsiSearch <- rdbIdentMsiSearches ) {
      setMsiSearch
      new Pairs::Msi::RDBO::ResultSetMsiSearchMap(
                              result_set_id = quantRsId,
                              msi_search_id = rdbIdentMsiSearch.id,
                              db = msiRdb
                            ).save
    }*/
    
    // Link this quantitative result set to the identification result sets
    msiQuantResultSet.setChildren( msiIdentResultSets.toSet[MsiResultSet] )
    msiEm.persist(msiQuantResultSet)
    val quantRsId = msiQuantResultSet.getId()
    
    // Create corresponding result summary
    val msiQuantRSM = new MsiResultSummary()
    msiQuantRSM.setModificationTimestamp(curSQLTime)
    msiQuantRSM.setResultSet(msiQuantResultSet)
    msiEm.persist(msiQuantRSM)

    val quantRsmId = msiQuantRSM.getId
    
    // Update quant result summary id of the quantitation fraction
    udsQuantFraction.setQuantResultSummaryId(quantRsmId)
    udsEm.persist(udsQuantFraction)
    
    // Retrieve peptide instances of the merged result summary
    val masterPepInstances = mergedResultSummary.peptideInstances
    val masterPepMatchById = mergedResultSummary.resultSet.get.peptideMatchById
    
    // Iterate over merged peptide instances to create quant peptide instances
    this.logger.info( "create quant peptide instances..." )

    // Define some vars
    val masterPepInstByPepId = new HashMap[Int,PeptideInstance]
    val msiMasterPepInstById = new HashMap[Int,MsiPeptideInstance]
    val masterPepMatchIdByBestChildId = new HashMap[Int,Int]
    
    for( masterPepInstance <- masterPepInstances ) {
      
      val peptideId = masterPepInstance.peptide.id
      val identPepMatchIds = masterPepInstance.getPeptideMatchIds
      
      val msiMasterPepInstance = new MsiPeptideInstance()
      msiMasterPepInstance.setPeptideMatchCount(identPepMatchIds.length)
      msiMasterPepInstance.setProteinMatchCount(masterPepInstance.proteinMatchesCount)
      msiMasterPepInstance.setProteinSetCount(masterPepInstance.proteinSetsCount)
      msiMasterPepInstance.setSelectionLevel(2)
      msiMasterPepInstance.setPeptideId(peptideId)
      msiMasterPepInstance.setBestPeptideMatchId( masterPepInstance.bestPeptideMatchId )
      msiMasterPepInstance.setResultSummary(msiQuantRSM)
      msiEm.persist(msiMasterPepInstance)
      
      val masterPepInstanceId = msiMasterPepInstance.getId
      
      // Update the peptide instance id
      masterPepInstance.id = masterPepInstanceId 
      
      // Map the peptide instance by the peptide id
      masterPepInstByPepId( peptideId ) = masterPepInstance
      // TODO: remove if this mapping when ORM is updated
      msiMasterPepInstById( masterPepInstanceId ) = msiMasterPepInstance 
      
      // Retrieve the best peptide match
      val identPepMatches = identPepMatchIds.map { masterPepMatchById(_) }
      val bestPepMatch = identPepMatches.reduce { (a,b) => if( a.score > b.score ) a else b } 
      
      // Create a quant peptide match which correspond to the best peptide match of this peptide instance
      val msiMasterPepMatch = new MsiPeptideMatch()
      msiMasterPepMatch.setCharge(bestPepMatch.msQuery.charge)
      msiMasterPepMatch.setExperimentalMoz(bestPepMatch.msQuery.moz)
      msiMasterPepMatch.setScore(bestPepMatch.score)
      msiMasterPepMatch.setRank(bestPepMatch.rank)
      msiMasterPepMatch.setDeltaMoz(bestPepMatch.deltaMoz)
      msiMasterPepMatch.setMissedCleavage(bestPepMatch.missedCleavage)
      msiMasterPepMatch.setFragmentMatchCount(bestPepMatch.fragmentMatchesCount)
      msiMasterPepMatch.setIsDecoy(false)
      msiMasterPepMatch.setPeptideId(bestPepMatch.peptide.id)
      
      // FIXME: retrieve the right scoring_id
      msiMasterPepMatch.setScoringId(1)
      
      // FIXME: change the ORM to allow these mappings
      //msiMasterPepMatch.setBestPeptideMatchId(bestPepMatch.id) 
      //msiMasterPepMatch.setMsQueryId(bestPepMatch.msQueryId)
      
      // FIXME: remove this mapping when the ORM is updated
      val msiMSQFake = new fr.proline.core.orm.msi.MsQuery
      msiMSQFake.setId( bestPepMatch.msQuery.id )
      msiMasterPepMatch.setMsQuery( msiMSQFake )
      
      msiMasterPepMatch.setResultSet(msiQuantResultSet)
      if( bestPepMatch.properties != None ) msiMasterPepMatch.setSerializedProperties(generate(bestPepMatch.properties))
      
      // Save master peptide match
      msiEm.persist(msiMasterPepMatch)
      
      val quantPepMatchId = msiMasterPepMatch.getId
      
      // Map this quant peptide match to the quant peptide instance
      val msiPepInstMatchPK = new MsiPepInstPepMatchMapPK()
      msiPepInstMatchPK.setPeptideInstanceId( masterPepInstanceId )  
      msiPepInstMatchPK.setPeptideMatchId( quantPepMatchId )
      
      val msiPepInstMatch = new MsiPepInstPepMatchMap()
      msiPepInstMatch.setId( msiPepInstMatchPK )
      msiPepInstMatch.setPeptideInstance( msiMasterPepInstance )
      msiPepInstMatch.setResultSummary( msiQuantRSM )
      
      //msiMasterPepInstance.setPeptidesMatches(Set(msiMasterPepMatch))
      msiEm.persist( msiMasterPepInstance )
      
      // Map quant peptide match id by best identified peptide match id
      masterPepMatchIdByBestChildId( bestPepMatch.id ) = quantPepMatchId
      
      // Map this quant peptide match to identified peptide matches
      for( identPepMatchId <- identPepMatchIds ) {
        val msiPepMatchRelation = new MsiPeptideMatchRelation()
        msiPepMatchRelation.setParentPeptideMatch(msiMasterPepMatch)
        // FIXME: allows to set the child peptide match id
        msiPepMatchRelation.setChildPeptideMatch(msiMasterPepMatch)
        // FIXME: rename to setParentResultSet
        msiPepMatchRelation.setParentResultSetId(msiQuantResultSet)
      }
    }
    
    // Iterate over master features to create master quant peptide ions
    for( masterFt <- masterFeatures ) {
      
      // Create an inner function which will help to instantiate Master Quant peptides
      def newMasterQuantPeptide( quantPepIonMap: Map[Int,QuantPeptideIon],
                                 masterPepInstAsOpt: Option[PeptideInstance],
                                 lcmsFtId: Option[Int] ): MasterQuantPeptide = {
        
        val mqPepIon = new MasterQuantPeptideIon(
          id = MasterQuantPeptideIon.generateNewId(),
          unlabeledMoz = masterFt.moz,
          charge = masterFt.charge,
          elutionTime = masterFt.elutionTime,
          peptideMatchesCount = 0,
          bestPeptideMatchId = None,
          resultSummaryId = quantRsmId,
          lcmsFeatureId = lcmsFtId,
          selectionLevel = 2,
          quantPeptideIonMap = quantPepIonMap
        )
        
        var( pepMatchesCount, protMatchesCount, pepId, peptInstId) = (0,0,0,0)
        if( masterPepInstAsOpt != None ) {
          val masterPepInst = masterPepInstAsOpt.get
          pepMatchesCount = masterPepInst.peptideMatchesCount
          protMatchesCount = masterPepInst.proteinMatchesCount
          pepId = masterPepInst.peptide.id
          peptInstId = masterPepInst.id
        }
       
        new MasterQuantPeptide(
          id = MasterQuantPeptide.generateNewId(),
          peptideMatchesCount = pepMatchesCount,
          proteinMatchesCount = protMatchesCount,
          quantPeptideMap = null,
          masterQuantPeptideIons = Array(mqPepIon),
          peptideId = pepId,
          peptideInstanceId = peptInstId,
          selectionLevel = 2
         )
        
      }

      // Retrieve peptide ids related to the feature children
      val peptideIdSet = new HashSet[Int]
      
      for( ftChild <- masterFt.children ) {
        
        // Check if we have a result set for this map (that map may be in the map_set but not used)
        if( identRsIdByLcmsMapId.contains( ftChild.relations.mapId ) ) {
          val childPeptideIons = quantPepIonsByFtId(ftChild.id)
          
          for( childPepIon <- childPeptideIons ) {
            if( childPepIon.peptideId != None ) peptideIdSet += childPepIon.peptideId.get
          }
        }
      }
      
      // Convert master feature into master quant peptide ion
      val peptideIds = peptideIdSet.toList     
      if( peptideIds.length > 0 ) {
        val masterPepInsts = peptideIds.map { masterPepInstByPepId(_) }
        //val quantPepIonsByPepId = new HashMap[Int,ArrayBuffer[QuantPeptideIon]]
        
        // Iterate over each quant peptide instance which is matching the master feature
        // Create a master quant peptide ion for each peptide instance
        for( masterPepInst <- masterPepInsts ) {
          
          val tmpPeptideId = masterPepInst.peptide.id
          val quantPeptideIonMap = new HashMap[Int,QuantPeptideIon]
          
          // Link master quant peptide ion to identified peptide ions
          for( ftChild <- masterFt.children ) {
            
            val mapId = ftChild.relations.mapId
            
            // Check if we have a result set for this map (that map may be in the map_set but not used)
            if( identRsIdByLcmsMapId.contains( mapId ) ) {
              
              val childPeptideIons = quantPepIonsByFtId(ftChild.id)
              val matchingPepChildPepIons = childPeptideIons.filter { p => p.peptideId != None && p.peptideId.get == tmpPeptideId }
              if( matchingPepChildPepIons.length > 1 ) {
                throw new Exception("peptide ion identification conflict")
              }
              
              // Try to retrieve peptide ion corresponding to current peptide instance
              val qcId = quantChannelIdByLcmsMapId(mapId)
              quantPeptideIonMap(qcId) = if(matchingPepChildPepIons.length == 1) matchingPepChildPepIons(0)
                                         else childPeptideIons(0) // TODO: check that length is zero
              
            }
          }
          
          this._storeMasterQuantPeptide(
                 newMasterQuantPeptide( quantPeptideIonMap.toMap, Some(masterPepInst), Some(masterFt.id) ),
                 msiQuantRSM
                 )
          
        }
      } else {
        
        // Create a master quant peptide ion which is not linked to a peptide instance
        
        val quantPeptideIonMap = new HashMap[Int,QuantPeptideIon]        
        for( ftChild <- masterFt.children ) {          
          
          // Check if we have a result set for this map (that map may be in the map_set but not used)
          val mapId = ftChild.relations.mapId
          if( identRsIdByLcmsMapId.contains( mapId ) ) {
          
            // TODO: find what to do if more than one peptide ion
            // Currently select the most abundant
            val childPeptideIon = quantPepIonsByFtId(ftChild.id).reduce { (a,b) =>
                                    if(a.rawAbundance > b.rawAbundance) a else b
                                  }
            val qcId = quantChannelIdByLcmsMapId(mapId)
            quantPeptideIonMap(qcId) = childPeptideIon

          }
        }
        
        this._storeMasterQuantPeptide(
               newMasterQuantPeptide( quantPeptideIonMap.toMap, None, Some(masterFt.id) ),
               msiQuantRSM
               )
        
      }
    }
    
    // TODO create quant peptide ions which are not related to a LCMS features = identified peptide ions but not quantified
    
    // Retrieve some vars
    val masterPeptideSets = mergedResultSummary.peptideSets
    this.logger.info( "number of grouped peptide sets: " + masterPeptideSets.length )
    val masterProteinSets = mergedResultSummary.proteinSets
    this.logger.info( "number of grouped protein sets: " + masterProteinSets.length )
    val masterProtSetById = mergedResultSummary.proteinSetById
    val masterProtMatchById = mergedResultSummary.resultSet.get.proteinMatchById
    
    // Iterate over identified peptide sets to create quantified peptide sets
    this.logger.info( "storing quantified peptide sets..." )
    for( masterPeptideSet <- masterPeptideSets ) {
      
      if( masterPeptideSet.isSubset == false ) {
      
        val masterProteinSet = masterProtSetById.get( masterPeptideSet.getProteinSetId )
        val samesetPepInstances = masterPeptideSet.getPeptideInstances
        
        if( masterProteinSet == None )
          throw new Exception( "missing protein set with id=" + masterPeptideSet.getProteinSetId )
        
        //////// Check if the protein set has at least a peptide instance with a relevant quantitation
        //val isProteinSetQuantitationRelevant = 0
        //for( tmpPepInstance <- samesetPeptideInstances ) {
        //  val rdbQuantPepInstance = quantPepByIdentPepId( tmpPepInstance.id )
        //  if( rdbQuantPepInstance.isQuantitationRelevant ) {
        //    isProteinSetQuantitationRelevant = 1
        //    last
        //  }
        //}
        
        // Determine the typical protein match id using the sequence coverage
        var typicalProtMatchId = masterProteinSet.get.getTypicalProteinMatchId // if( idfProteinSet == None ) 0 else 
        if( typicalProtMatchId == 0 ) {
          typicalProtMatchId = masterPeptideSet.proteinMatchIds.reduce { (a,b) => 
            if( masterProtMatchById(a).coverage > masterProtMatchById(b).coverage ) a else b
          }
        }
                                          
        // Store master protein set
        val msiMasterProteinSet = new MsiProteinSet()
        msiMasterProteinSet.setIsValidated( true )
        msiMasterProteinSet.setSelectionLevel( 2 )
        msiMasterProteinSet.setProteinMatchId( typicalProtMatchId )
        // FIXME: retrieve the right scoring id
        msiMasterProteinSet.setScoringId(4)
        msiMasterProteinSet.setResultSummary( msiQuantRSM )        
        msiEm.persist( msiMasterProteinSet )
        
        val masterProteinSetId = msiMasterProteinSet.getId
        
        // Store master peptide set      
        val msiMasterPeptideSet = new MsiPeptideSet()
        msiMasterPeptideSet.setIsSubset( false )
        msiMasterPeptideSet.setPeptideCount( samesetPepInstances.length )
        msiMasterPeptideSet.setPeptideMatchCount( masterPeptideSet.peptideMatchesCount )
        msiMasterPeptideSet.setProteinSet( msiMasterProteinSet )
        msiMasterPeptideSet.setResultSummaryId( quantRsmId )        
        msiEm.persist( msiMasterPeptideSet )
        
        val masterPeptideSetId = msiMasterPeptideSet.getId
        
        // Link master peptide set to master peptide instances
        for( tmpPepInstance <- samesetPepInstances ) {
          val msiMasterPepInst = msiMasterPepInstById( tmpPepInstance.id )

          val msiPepSetItemPK = new MsiPeptideSetItemPK()          
          msiPepSetItemPK.setPeptideSetId( msiMasterPeptideSet.getId )
          msiPepSetItemPK.setPeptideInstanceId( msiMasterPepInst.getId )          
          
          // TODO: change JPA definition to skip this double mapping
          val msiPepSetItem = new MsiPeptideSetItem()
          msiPepSetItem.setId(msiPepSetItemPK )
          msiPepSetItem.setPeptideSet( msiMasterPeptideSet )
          msiPepSetItem.setPeptideInstance( msiMasterPepInst ) 
          msiPepSetItem.setResultSummary( msiQuantRSM )
          
          msiEm.persist( msiPepSetItem )
        }
        
        // Store master protein matches
        for( protMatchId <- masterPeptideSet.proteinMatchIds) {
          
          val masterProtMatch = masterProtMatchById(protMatchId)
          
          val msiMasterProtMatch = new MsiProteinMatch()
          msiMasterProtMatch.setAccession( masterProtMatch.accession )
          msiMasterProtMatch.setDescription( masterProtMatch.description )
          msiMasterProtMatch.setGeneName( masterProtMatch.geneName )
          msiMasterProtMatch.setScore( masterProtMatch.score )
          msiMasterProtMatch.setCoverage( masterProtMatch.coverage )
          msiMasterProtMatch.setPeptideCount( masterProtMatch.sequenceMatches.length )
          msiMasterProtMatch.setPeptideMatchCount( masterProtMatch.peptideMatchesCount )
          msiMasterProtMatch.setIsDecoy( masterProtMatch.isDecoy )
          msiMasterProtMatch.setIsLastBioSequence( masterProtMatch.isLastBioSequence )
          msiMasterProtMatch.setTaxonId( masterProtMatch.taxonId )
          msiMasterProtMatch.setBioSequenceId( masterProtMatch.getProteinId )
          // FIXME: retrieve the right scoring id
          msiMasterProtMatch.setScoringId( 3 )
          msiMasterProtMatch.setResultSet( msiQuantResultSet )
          msiEm.persist( msiMasterProtMatch )
          
          val masterProtMatchId = msiMasterProtMatch.getId
          
          // TODO: map protein_match to seq_databases
          
          // TODO: Map master protein match to master peptide set => ORM has to be fixed
          /*val msiPepSetProtMatchMap = new MsiPepSetProtMatchMap()
          new Pairs::Msi::RDBO::PeptideSetProteinMatchMap(
                                  peptide_set_id = quantPeptideSetId,
                                  protein_match_id = quantProtMatchId,
                                  result_summary_id = quantRsmId,
                                  db = msiRdb
                                ).save*/
          
          // Map master protein match to master protein set
          val msiProtSetProtMatchItemPK = new MsiProtSetProtMatchItemPK()
          msiProtSetProtMatchItemPK.setProteinSetId( msiMasterProteinSet.getId )
          msiProtSetProtMatchItemPK.setProteinMatchId( msiMasterProtMatch.getId )
          
          // TODO: change JPA definition
          val msiProtSetProtMatchItem = new MsiProtSetProtMatchItem()
          msiProtSetProtMatchItem.setId( msiProtSetProtMatchItemPK )
          msiProtSetProtMatchItem.setProteinSet( msiMasterProteinSet )
          msiProtSetProtMatchItem.setProteinMatch( msiMasterProtMatch )
          msiProtSetProtMatchItem.setResultSummary( msiQuantRSM )
          msiEm.persist( msiProtSetProtMatchItem )
          
          // Map master protein match to master peptide matches using master sequence matches
          val seqMatches = masterProtMatch.sequenceMatches
          val mappedMasterPepMatchesIdSet = new HashSet[Int]
          
          for( seqMatch <- seqMatches ) {
            val bestPepMatchId = seqMatch.getBestPeptideMatchId
            if( masterPepMatchIdByBestChildId.contains(bestPepMatchId) ) {
              val masterPepMatchId = masterPepMatchIdByBestChildId(bestPepMatchId)
  
              if( mappedMasterPepMatchesIdSet.contains(masterPepMatchId) == false ) {
                mappedMasterPepMatchesIdSet(masterPepMatchId) = true
                
                val msiMasterSeqMatchPK = new fr.proline.core.orm.msi.SequenceMatchPK()
                msiMasterSeqMatchPK.setProteinMatchId(masterProtMatchId)
                msiMasterSeqMatchPK.setPeptideId(seqMatch.getPeptideId)
                msiMasterSeqMatchPK.setStart(seqMatch.start)
                msiMasterSeqMatchPK.setStop(seqMatch.end)
                
                val msiMasterSeqMatch = new MsiSequenceMatch()
                msiMasterSeqMatch.setId( msiMasterSeqMatchPK )
                msiMasterSeqMatch.setResidueBefore( seqMatch.residueBefore.toString ) // TODO: change ORM mapping to Char
                msiMasterSeqMatch.setResidueBefore( seqMatch.residueAfter.toString ) // TODO: change ORM mapping to Char
                msiMasterSeqMatch.setIsDecoy( false )
                msiMasterSeqMatch.setBestPeptideMatchId( seqMatch.getBestPeptideMatchId )
                msiMasterSeqMatch.setResultSetId( quantRsId )
                msiEm.persist( msiMasterSeqMatch )
                
              }
            }
          }
        }
      }
    }
    
    // Commit ORM transaction
    msiEm.getTransaction().commit()
    udsEm.getTransaction().commit()
    
    this.logger.info("fraction has been quantified !")
    
    ()

  }
  
  protected def _storeMasterQuantPeptide( mqPep: MasterQuantPeptide, msiRSM: MsiResultSummary ) = {
    
    val schemaName = "label_free_peptide_ions"
    // TODO: load the schema
    val schemaFake = new fr.proline.core.orm.msi.ObjectTreeSchema()
    schemaFake.setName( schemaName )
    schemaFake.setType( "JSON" )
    schemaFake.setVersion( "0.1" )
    schemaFake.setSchema( "" )
    
    for( mqPepIon <- mqPep.masterQuantPeptideIons ) {
      
      val quantPeptideIonMap = mqPepIon.quantPeptideIonMap
      val quantPepIons = quantChannelIds.map { quantPeptideIonMap.getOrElse(_,null) }
      
      // Store the object tree
      val msiMQCObjectTree = new MsiObjectTree()
      msiMQCObjectTree.setSchema( schemaFake )
      msiMQCObjectTree.setSerializedData( generate[Array[QuantPeptideIon]](quantPepIons) )      
      this.msiEm.persist(msiMQCObjectTree)
    
      // Store master quant component
      val msiMQC = new MsiMasterQuantComponent()
      msiMQC.setSelectionLevel(mqPepIon.selectionLevel)
      if( mqPepIon.properties != None ) msiMQC.setSerializedProperties( generate(mqPepIon.properties) )
      msiMQC.setObjectTreeId(1)
      msiMQC.setSchemaName(schemaName)
      msiMQC.setResultSummary(msiRSM)
      
      this.msiEm.persist(msiMQC)
      
      // Store master quant peptide ion
      val msiMQPepIon = new MsiMasterQuantPepIon()
      msiMQPepIon.setCharge(mqPepIon.charge)
      msiMQPepIon.setMoz(mqPepIon.unlabeledMoz)
      msiMQPepIon.setElutionTime(mqPepIon.elutionTime)
      if( mqPepIon.properties != None ) msiMQPepIon.setSerializedProperties( generate(mqPepIon.properties) )
      
      msiMQPepIon.setMasterQuantComponent( msiMQC )
      msiMQPepIon.setResultSummary(msiRSM)
      
      if( mqPep.peptideId > 0 ) msiMQPepIon.setPeptideId( mqPep.peptideId )
      if( mqPep.peptideInstanceId > 0 ) msiMQPepIon.setPeptideInstanceId( mqPep.peptideInstanceId )    
      if( mqPepIon.lcmsFeatureId != None ) msiMQPepIon.setLcmsFeatureId( mqPepIon.lcmsFeatureId.get ) 
      if( mqPepIon.bestPeptideMatchId != None ) msiMQPepIon.setBestPeptideMatchId( mqPepIon.bestPeptideMatchId.get )
      if( mqPepIon.unmodifiedPeptideIonId != None ) msiMQPepIon.setUnmodifiedPeptideIonId( mqPepIon.unmodifiedPeptideIonId.get )
      
      this.msiEm.persist(msiMQPepIon)
    }
  }
  
}