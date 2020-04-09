package fr.proline.core.orm.msi.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.proline.core.orm.util.JsonSerializer;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;


public class QuantPropertiestest {

    String mqPepBestPropJsonStr ="{\"mq_prot_set_ids\":[14879],\"mq_pep_profile_by_group_setup_number\":{\"1\":{\"ratios\":[{\"numerator\":532647.7,\"denominator\":532647.7,\"state\":-1}]}},\"mq_pep_ion_abundance_summarizing_config\":{\"method_name\":\"BEST_ION\",\"mq_peptide_ion_sel_level_by_id\":{\"49966\":2}}}";
    String mqPepDiscardBestPropJsonStr ="{\"discarding_reason\":\"Modified peptide\",\"mq_prot_set_ids\":[14876],\"mq_pep_profile_by_group_setup_number\":{\"1\":{\"ratios\":[{\"numerator\":128417.19,\"denominator\":128417.19,\"state\":-1}]}},\"mq_pep_ion_abundance_summarizing_config\":{\"method_name\":\"BEST_ION\",\"mq_peptide_ion_sel_level_by_id\":{\"49974\":2}}}";
    String mqPepSumPropJsonStr ="{\"mq_prot_set_ids\":[6563],\"mq_pep_profile_by_group_setup_number\":{\"1\":{\"ratios\":[{\"numerator\":87770.22,\"denominator\":411021.38,\"state\":-1}]}},\"mq_pep_ion_abundance_summarizing_config\":{\"method_name\":\"SUM\",\"mq_peptide_ion_sel_level_by_id\":{\"45306\":2}}}";
    String mqPepDiscardSumPropJsonStr ="{\"discarding_reason\":\"Modified peptide\",\"mq_prot_set_ids\":[6563],\"mq_pep_profile_by_group_setup_number\":{\"1\":{\"ratios\":[{\"numerator\":388993.94,\"denominator\":2088263.5,\"state\":-1}]}},\"mq_pep_ion_abundance_summarizing_config\":{\"method_name\":\"SUM\",\"mq_peptide_ion_sel_level_by_id\":{\"45260\":2}}}";

    String mqPSPropJsonStr = "{\"selection_changed\":false,\"mq_prot_set_profiles_by_group_setup_number\":{\"1\":[{\"name\":\"\",\"raw_abundances\":[3273238.8,3378430.5,2731649.0,2823391.8,2.3852338E7,2.4917038E7,2.640852E7,2.680362E7],\"abundances\":[3273238.8,3378430.5,2731649.0,2823391.8,2.3852338E7,2.4917038E7,2.640852E7,2.680362E7],\"ratios\":[{\"numerator\":3048315.2,\"denominator\":2.566278E7,\"cv\":0.2877196,\"state\":-1}],\"mq_peptide_ids\":[87347,87338,87412,87518,87558,87326,87499,87505,87406,87235,87429,87254,87361,87425,87427,87288,87484],\"peptide_matches_counts\":[]}]},\"mq_peptide_sel_level_by_id\":{\"87406\":2,\"87505\":2,\"87361\":2,\"87427\":2,\"87412\":2,\"87558\":2,\"87235\":2,\"87429\":2,\"87267\":1,\"87288\":2,\"87425\":2,\"87347\":2,\"87518\":2,\"87326\":2,\"87254\":2,\"87499\":2,\"87338\":2,\"87484\":2},\"mq_peptide_ion_sel_level_by_id\":{\"45373\":2,\"45340\":2,\"45421\":2,\"45376\":2,\"45403\":2,\"45271\":2,\"45289\":2,\"45301\":2,\"45411\":2,\"45333\":2,\"45414\":2,\"45300\":2,\"45282\":2,\"45363\":2,\"45327\":2,\"45366\":2,\"45375\":2,\"45374\":2,\"45332\":2,\"45404\":2,\"45320\":2,\"45442\":2}}";
    String mqPSPropEmptylistJsonStr = "{\"selection_changed\":false,\"mq_prot_set_profiles_by_group_setup_number\":{\"1\":[]},\"mq_peptide_sel_level_by_id\":{\"87468\":1},\"mq_peptide_ion_sel_level_by_id\":{\"45395\":2}}";


    @Test
    public void readMQPepProp(){
        try {
            ObjectMapper objectMapper = JsonSerializer.getMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            MasterQuantPeptideProperties  mqPepProperties = objectMapper.readValue(mqPepBestPropJsonStr, MasterQuantPeptideProperties.class);
            assertEquals(1, mqPepProperties.getMqPepProfileByGroupSetupNumber().size());
            assertEquals(1, mqPepProperties.getMqProtSetIds().size());
            assertNotNull(mqPepProperties.m_pepIonAbSummarizingConfig);
            assertEquals(1,mqPepProperties.m_pepIonAbSummarizingConfig.getmMqPeptideIonSelLevelById().size());
            assertNull(mqPepProperties.getDiscardingReason());

            mqPepProperties = objectMapper.readValue(mqPepDiscardBestPropJsonStr, MasterQuantPeptideProperties.class);
            assertEquals(1, mqPepProperties.getMqPepProfileByGroupSetupNumber().size());
            assertEquals(1, mqPepProperties.getMqProtSetIds().size());
            assertNotNull(mqPepProperties.m_pepIonAbSummarizingConfig);
            assertNotNull(mqPepProperties.getDiscardingReason());

            mqPepProperties = objectMapper.readValue(mqPepSumPropJsonStr, MasterQuantPeptideProperties.class);
            assertEquals(1, mqPepProperties.getMqPepProfileByGroupSetupNumber().size());
            assertEquals(1, mqPepProperties.getMqProtSetIds().size());
            assertNotNull(mqPepProperties.m_pepIonAbSummarizingConfig);
            assertNull(mqPepProperties.getDiscardingReason());

            mqPepProperties = objectMapper.readValue(mqPepDiscardSumPropJsonStr, MasterQuantPeptideProperties.class);
            assertEquals(1, mqPepProperties.getMqPepProfileByGroupSetupNumber().size());
            assertEquals(1, mqPepProperties.getMqProtSetIds().size());
            assertNotNull(mqPepProperties.m_pepIonAbSummarizingConfig);
            assertNotNull(mqPepProperties.getDiscardingReason());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }


    @Test
    public void restMQPSProp(){

        try {
            ObjectMapper objectMapper = JsonSerializer.getMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            MasterQuantProteinSetProperties mqProtSetProperties = objectMapper.readValue(mqPSPropJsonStr, MasterQuantProteinSetProperties.class);
            assertEquals(1, mqProtSetProperties.getMqProtSetProfilesByGroupSetupNumber().size());
            assertEquals(1, mqProtSetProperties.getMqProtSetProfilesByGroupSetupNumber().get("1").size());
            assertEquals(8, mqProtSetProperties.getMqProtSetProfilesByGroupSetupNumber().get("1").get(0).abundances.size());
            assertEquals(17, mqProtSetProperties.getSelectedMasterQuantPeptideIds().size());
            assertEquals(22, mqProtSetProperties.getSelectedMasterQuantPeptideIonIds().size());


            mqProtSetProperties = objectMapper.readValue(mqPSPropEmptylistJsonStr, MasterQuantProteinSetProperties.class);
            assertEquals(1, mqProtSetProperties.getMqProtSetProfilesByGroupSetupNumber().size());
            assertEquals(0, mqProtSetProperties.getMqProtSetProfilesByGroupSetupNumber().get("1").size());
            assertEquals(0, mqProtSetProperties.getSelectedMasterQuantPeptideIds().size());
            assertEquals(1, mqProtSetProperties.getMqPeptideIonSelLevelById().size());
            assertEquals(1, mqProtSetProperties.getSelectedMasterQuantPeptideIonIds().size());
        } catch (Exception e) {
            fail(e.getMessage());
        }


    }
}
