package fr.proline.core.om.provider.msq.impl

import fr.proline.context.DatabaseConnectionContext
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
import fr.proline.util.primitives._
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet

class SQLExperimentalDesignProvider(val udsDbCtx: DatabaseConnectionContext) extends IExperimentalDesignProvider {

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
    
    DoJDBCReturningWork.withEzDBC(udsDbCtx, { udsEzDBC =>
      
      // Retrieve biological samples
      val bioSampleQueryBuilder = new SelectQueryBuilder2(UdsDbBiologicalSampleTable,UdsDbBiologicalGroupBiologicalSampleItemTable)
      val bioSampleQuery = bioSampleQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t1.*,t2.BIOLOGICAL_GROUP_ID) ->  
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~
        " AND "~ t1.ID ~" = "~ t2.BIOLOGICAL_SAMPLE_ID
      )
      
      val sampleIdsByGroupId = new HashMap[Long,HashSet[Long]]
      val bioSampleById = new HashMap[Long,BiologicalSample]
      udsEzDBC.selectAndProcess(bioSampleQuery) { r =>
        val sampleId = toLong( r.getAny(BioSampleCols.ID) )
        val groupId = toLong( r.getAny(GroupSampleMappingCols.BIOLOGICAL_GROUP_ID) )
        sampleIdsByGroupId.getOrElseUpdate(groupId, new HashSet[Long]) += sampleId
        
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
      
      val mqcIdByQcId = new HashMap[Long,Long]
      val quantChannels = udsEzDBC.select(qcQuery) { r =>
        
        val qcId = toLong(r.getAny(QuantChannelCols.ID) )
        val mqcId = toLong(r.getAny(QuantChannelCols.MASTER_QUANT_CHANNEL_ID) )
        mqcIdByQcId(qcId) = mqcId
        
        QuantChannel(
          id = qcId,
          number = r.getInt(QuantChannelCols.NUMBER),
          sampleNumber = bioSampleById( toLong(r.getAny(QuantChannelCols.BIOLOGICAL_SAMPLE_ID) ) ).number,
          identResultSummaryId = toLong(r.getAny(QuantChannelCols.IDENT_RESULT_SUMMARY_ID) ),
          lcmsMapId = r.getAnyOption(QuantChannelCols.LCMS_MAP_ID).map( toLong(_) ),
          runId = r.getAnyOption(QuantChannelCols.RUN_ID).map( toLong(_) ),
          quantLabelId = r.getAnyOption(QuantChannelCols.QUANT_LABEL_ID).map( v =>  { println(v); toLong(v)} )
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
        
        val mqcId = toLong(r.getAny(MQChannelCols.ID) )
        
        MasterQuantChannel(
          id = mqcId,
          number = r.getInt(MQChannelCols.NUMBER),
          name = r.getStringOption(MQChannelCols.NAME),
          lcmsMapSetId = r.getAnyOption(MQChannelCols.LCMS_MAP_SET_ID).map( toLong(_) ),
          quantChannels = quantChannelsByMqcId(mqcId).toArray
        )        
      }
      
      // Retrieve biological groups
      val bioGroupQueryBuilder = new SelectQueryBuilder2(UdsDbBiologicalGroupTable,UdsDbGroupSetupBiologicalGroupMapTable)
      val bioGroupQuery = bioGroupQueryBuilder.mkSelectQuery( (t1,c1,t2,c2) => List(t1.*,t2.GROUP_SETUP_ID) -> 
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId ~
        " AND "~ t1.ID ~" = "~ t2.BIOLOGICAL_GROUP_ID
      )
      
      val bioGroupsByGroupSetupId = new HashMap[Long,HashSet[Long]]
      val bioGroupById = new HashMap[Long,BiologicalGroup]
      udsEzDBC.selectAndProcess(bioGroupQuery) { r =>
        
        val groupId = toLong( r.getAny(BioGroupCols.ID) )
        val sampleNumbers = sampleIdsByGroupId(groupId).map( bioSampleById(_) ).map(_.number)
        
        val groupSetupId = toLong( r.getAny(GroupSetupMappingCols.GROUP_SETUP_ID) )
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
        " AND "~ t1.GROUP_SETUP_ID ~" = "~ t2.ID
      )
      
      val ratioDefsByGSId = new HashMap[Long,ArrayBuffer[RatioDefinition]]
      udsEzDBC.selectAndProcess(ratioDefQuery) { r =>

        val groupSetupId = toLong( r.getAny(RatioDefCols.GROUP_SETUP_ID) )
        
        val ratioDef = RatioDefinition(
          id = toLong( r.getAny(RatioDefCols.ID) ),
          number = r.getInt(RatioDefCols.NUMBER),
          numeratorGroupNumber = bioGroupById( r.getLong(RatioDefCols.NUMERATOR_ID) ).number,
          denominatorGroupNumber = bioGroupById( r.getLong(RatioDefCols.DENOMINATOR_ID) ).number
        )
        
        ratioDefsByGSId.getOrElseUpdate(groupSetupId, new ArrayBuffer[RatioDefinition]) += ratioDef
        
        ratioDef
      }
      
      // Retrieve group setups
      val groupSetupQueryBuilder = new SelectQueryBuilder1(UdsDbGroupSetupTable)
      val groupSetupQuery = groupSetupQueryBuilder.mkSelectQuery( (t1,c1) => List(t1.*) ->  
        " WHERE "~ t1.QUANTITATION_ID ~" = "~ quantitationId
      )
      
      var gsNumber = 0
      val groupSetups = udsEzDBC.select(groupSetupQuery) { r =>
        gsNumber += 1

        val groupSetupId = toLong( r.getAny(GroupSetupCols.ID) )
        val bioGroups = bioGroupsByGroupSetupId( groupSetupId ).toArray.map( bioGroupById(_) ).sortBy(_.number)
        
        GroupSetup(
          id = groupSetupId,
          number = gsNumber,
          name = r.getString(GroupSetupCols.NAME),
          ratioDefinitions = ratioDefsByGSId( groupSetupId ).toArray.sortBy(_.number),
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
    })

  }
  
}