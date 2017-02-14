package fr.proline.core.om.provider.msq.impl

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import scala.collection.mutable.LongMap
import fr.profi.util.collection._
import fr.profi.util.primitives._
import fr.proline.context.UdsDbConnectionContext
import fr.proline.core.om.provider.msq.IExperimentalDesignProvider
import fr.proline.core.om.model.msq._
import fr.proline.core.dal.DoJDBCReturningWork
import fr.proline.core.dal.tables.SelectQueryBuilder._
import fr.proline.core.dal.tables.SelectQueryBuilder1
import fr.proline.core.dal.tables.SelectQueryBuilder2
import fr.proline.core.dal.tables.SelectQueryBuilder3
import fr.proline.core.dal.tables.uds.UdsDbBiologicalGroupTable
import fr.proline.core.dal.tables.uds.UdsDbBiologicalGroupBiologicalSampleItemTable
import fr.proline.core.dal.tables.uds.UdsDbBiologicalSampleTable
import fr.proline.core.dal.tables.uds.UdsDbBiologicalSampleSampleAnalysisMapTable
import fr.proline.core.dal.tables.uds.UdsDbGroupSetupTable
import fr.proline.core.dal.tables.uds.UdsDbGroupSetupBiologicalGroupMapTable
import fr.proline.core.dal.tables.uds.UdsDbMasterQuantChannelTable
import fr.proline.core.dal.tables.uds.UdsDbQuantChannelTable
import fr.proline.core.dal.tables.uds.UdsDbSampleAnalysisTable
import fr.proline.core.dal.tables.uds.UdsDbRatioDefinitionTable

class SQLExperimentalDesignProvider(val udsDbCtx: UdsDbConnectionContext) extends IExperimentalDesignProvider {

  val BioGroupCols = UdsDbBiologicalGroupTable.columns
  val BioSampleCols = UdsDbBiologicalSampleTable.columns
  val GroupSetupCols = UdsDbGroupSetupTable.columns
  val QuantChannelCols = UdsDbQuantChannelTable.columns
  val MQChannelCols = UdsDbMasterQuantChannelTable.columns
  val GroupSampleMappingCols = UdsDbBiologicalGroupBiologicalSampleItemTable.columns
  val GroupSetupMappingCols = UdsDbGroupSetupBiologicalGroupMapTable.columns
  val SampleAnalysisMappingCols = UdsDbBiologicalSampleSampleAnalysisMapTable.columns
  val RatioDefCols = UdsDbRatioDefinitionTable.columns
  
  def getExperimentalDesign( quantitationId:Long ): Option[ExperimentalDesign] = {
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx) { udsEzDBC =>
      
      // Retrieve biological samples
      val bioSampleQueryBuilder = new SelectQueryBuilder1(UdsDbBiologicalSampleTable)
      val bioSampleQuery = bioSampleQueryBuilder.mkSelectQuery( (t1,c1) => List(t1.*) ->
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~" ORDER BY "~ t1.NUMBER
      )
      
      val bioSampleById = new LongMap[BiologicalSample]
      udsEzDBC.selectAndProcess(bioSampleQuery) { r =>
        val sampleId = r.getLong(BioSampleCols.ID)
        
        bioSampleById(sampleId) = BiologicalSample(
          id = sampleId,
          number = r.getInt(BioSampleCols.NUMBER),
          name = r.getString(BioSampleCols.NAME)
        )
      }
      if( bioSampleById.size == 0 ) return None
      
      val bioSamples = bioSampleById.values.toArray.sortBy( _.number )
      
      // Retrieve quant channels
      val qcQueryBuilder = new SelectQueryBuilder1(UdsDbQuantChannelTable)
      val qcQuery = qcQueryBuilder.mkSelectQuery( (t1,c1) => List(t1.*) ->
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId~
        " ORDER BY "~ t1.NUMBER
      )
      
      val mqcIdByQcId = new LongMap[Long]()
      val quantChannels = udsEzDBC.select(qcQuery) { r =>
        
        val qcId = r.getLong(QuantChannelCols.ID)
        val mqcId = r.getLong(QuantChannelCols.MASTER_QUANT_CHANNEL_ID)
        mqcIdByQcId(qcId) = mqcId
        
        QuantChannel(
          id = qcId,
          number = r.getInt(QuantChannelCols.NUMBER),
          name = r.getStringOption(QuantChannelCols.NAME).getOrElse(""),
          sampleNumber = bioSampleById( r.getLong(QuantChannelCols.BIOLOGICAL_SAMPLE_ID) ).number,
          identResultSummaryId = r.getLong(QuantChannelCols.IDENT_RESULT_SUMMARY_ID),
          lcmsMapId = r.getLongOption(QuantChannelCols.LCMS_MAP_ID),
          runId = r.getLongOption(QuantChannelCols.RUN_ID),
          quantLabelId = r.getLongOption(QuantChannelCols.QUANT_LABEL_ID)
        )
      }
      val quantChannelsByMqcId = quantChannels.groupBy( qc => mqcIdByQcId(qc.id) )
      
      // Retrieve master quant channels
      val mqcQueryBuilder = new SelectQueryBuilder1(UdsDbMasterQuantChannelTable)
      val mqcQuery = mqcQueryBuilder.mkSelectQuery( (t1,c1) => List(t1.*) ->
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~
        " ORDER BY "~ t1.NUMBER
      )
      
      val masterQuantChannels = udsEzDBC.select(mqcQuery) { r =>
        
        val mqcId = r.getLong(MQChannelCols.ID)
        
        MasterQuantChannel(
          id = mqcId,
          number = r.getInt(MQChannelCols.NUMBER),
          name = r.getStringOption(MQChannelCols.NAME),
          quantResultSummaryId = r.getLongOption(MQChannelCols.QUANT_RESULT_SUMMARY_ID),
          lcmsMapSetId = r.getLongOption(MQChannelCols.LCMS_MAP_SET_ID),
          quantChannels = quantChannelsByMqcId(mqcId).toArray
        )
      }
      
      lazy val sampleIdSetByGroupId = {
        val sampleGroupMappingQueryBuilder = new SelectQueryBuilder2(UdsDbBiologicalGroupTable,UdsDbBiologicalGroupBiologicalSampleItemTable)
        val sampleGroupMappingQuery = sampleGroupMappingQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t2.*) ->  
          " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~
          " AND "~ t1.ID ~" = "~ t2.BIOLOGICAL_GROUP_ID
        )
        
        val sampleGroupMapping = new LongMap[HashSet[Long]]
        udsEzDBC.selectAndProcess(sampleGroupMappingQuery) { r =>
          val groupId = r.getLong(GroupSampleMappingCols.BIOLOGICAL_GROUP_ID)
          val sampleId = r.getLong(GroupSampleMappingCols.BIOLOGICAL_SAMPLE_ID)
          sampleGroupMapping.getOrElseUpdate(groupId, new HashSet[Long]) += sampleId
        }
        
        sampleGroupMapping
      }
      
      // Retrieve biological groups
      val bioGroupQueryBuilder = new SelectQueryBuilder2(UdsDbBiologicalGroupTable,UdsDbGroupSetupBiologicalGroupMapTable)
      val bioGroupQuery = bioGroupQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t1.*,t2.GROUP_SETUP_ID) -> 
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~
        " AND "~ t1.ID ~" = "~ t2.BIOLOGICAL_GROUP_ID ~
        " ORDER BY "~ t1.NUMBER
      )
      
      val bioGroupsByGroupSetupId = new LongMap[HashSet[Long]]
      val bioGroupById = new LongMap[BiologicalGroup]
      udsEzDBC.selectAndProcess(bioGroupQuery) { r =>
        
        val groupId = r.getLong(BioGroupCols.ID)
        val sampleNumbers = sampleIdSetByGroupId(groupId).map( bioSampleById(_) ).map(_.number)
        
        val groupSetupId = r.getLong(GroupSetupMappingCols.GROUP_SETUP_ID)
        bioGroupsByGroupSetupId.getOrElseUpdate(groupSetupId, new HashSet[Long]) += groupId
        
        bioGroupById(groupId) = BiologicalGroup(
          id = groupId,
          number = r.getInt(BioGroupCols.NUMBER),
          name = r.getString(BioGroupCols.NAME),
          sampleNumbers = sampleNumbers.toArray
        )
      }
      
      // Retrieve ratio definitions
      val ratioDefQueryBuilder = new SelectQueryBuilder2(UdsDbRatioDefinitionTable,UdsDbGroupSetupTable)
      val ratioDefQuery = ratioDefQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t1.*) ->  
        " WHERE "~ t2.QUANTITATION_ID ~" = "~ quantitationId ~
        " AND "~ t1.GROUP_SETUP_ID ~" = "~ t2.ID ~
        " ORDER BY "~ t1.NUMBER
      )
      
      val ratioDefsByGSId = new LongMap[ArrayBuffer[RatioDefinition]]
      udsEzDBC.selectAndProcess(ratioDefQuery) { r =>

        val groupSetupId = r.getLong(RatioDefCols.GROUP_SETUP_ID)
        
        val ratioDef = RatioDefinition(
          id =  r.getLong(RatioDefCols.ID),
          number = r.getInt(RatioDefCols.NUMBER),
          numeratorGroupNumber = bioGroupById( r.getLong(RatioDefCols.NUMERATOR_ID) ).number,
          denominatorGroupNumber = bioGroupById( r.getLong(RatioDefCols.DENOMINATOR_ID) ).number
        )
        
        ratioDefsByGSId.getOrElseUpdate(groupSetupId, new ArrayBuffer[RatioDefinition]) += ratioDef
      }
      
      // Retrieve group setups
      val groupSetupQueryBuilder = new SelectQueryBuilder1(UdsDbGroupSetupTable)
      val groupSetupQuery = groupSetupQueryBuilder.mkSelectQuery( (t1,c1) => List(t1.*) ->  
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~
        " ORDER BY "~ t1.NUMBER
      )
      
      val groupSetups = udsEzDBC.select(groupSetupQuery) { r =>

        val groupSetupId = r.getLong(GroupSetupCols.ID)
        val bioGroups = bioGroupsByGroupSetupId( groupSetupId ).toArray.map( bioGroupById(_) ).sortBy(_.number)
        
        GroupSetup(
          id = groupSetupId,
          number = r.getInt(GroupSetupCols.NUMBER),
          name = r.getString(GroupSetupCols.NAME),
          ratioDefinitions = ratioDefsByGSId.get(groupSetupId).map(_.toArray.sortBy(_.number)).getOrElse(Array()),
          biologicalGroups = bioGroups
        )
      }
      
      Some(
        ExperimentalDesign(
          biologicalSamples = bioSamples,
          groupSetups = groupSetups.toArray,
          masterQuantChannels = masterQuantChannels.toArray
        )
      )
    } // END of DoJDBCReturningWork

  }
  
}