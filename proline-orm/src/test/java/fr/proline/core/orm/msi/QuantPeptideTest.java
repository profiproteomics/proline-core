package fr.proline.core.orm.msi;

import java.util.Map;

import org.junit.Test;

import fr.proline.core.orm.msi.dto.DQuantPeptideIon;

public class QuantPeptideTest {

	@Test
	public void test() {
		String ot = "[null,null,{\"raw_abundance\":2236695.8,\"abundance\":2236695.8,\"moz\":1198.1135811313136,\"elution_time\":8057.5454,\"duration\":18.445312,\"corrected_elution_time\":8057.5454,\"scan_number\":46341,\"peptide_matches_count\":1,\"ms2_matching_frequency\":1.0,\"best_peptide_match_score\":133.86,\"quant_channel_id\":337,\"peptide_id\":398974,\"peptide_instance_id\":345284,\"ms_query_ids\":[1781075],\"lcms_feature_id\":4868505,\"lcms_master_feature_id\":4948644,\"selection_level\":2},{\"raw_abundance\":2104327.2,\"abundance\":2104327.2,\"moz\":1198.112706048294,\"elution_time\":8110.1987,\"duration\":18.762695,\"corrected_elution_time\":8110.1987,\"scan_number\":44562,\"peptide_matches_count\":1,\"ms2_matching_frequency\":1.0,\"best_peptide_match_score\":102.91,\"quant_channel_id\":338,\"peptide_id\":398974,\"peptide_instance_id\":455345,\"ms_query_ids\":[1541597],\"lcms_feature_id\":4895340,\"lcms_master_feature_id\":4948644,\"selection_level\":2},{\"raw_abundance\":3000436.0,\"abundance\":3000436.0,\"moz\":1198.1126338670592,\"elution_time\":8054.387,\"duration\":16.082031,\"corrected_elution_time\":8054.387,\"scan_number\":45871,\"peptide_matches_count\":1,\"ms2_matching_frequency\":1.0,\"best_peptide_match_score\":113.37,\"quant_channel_id\":339,\"peptide_id\":398974,\"peptide_instance_id\":461188,\"ms_query_ids\":[1676717],\"lcms_feature_id\":4915072,\"lcms_master_feature_id\":4948644,\"selection_level\":2},{\"raw_abundance\":1811121.9,\"abundance\":1811121.9,\"moz\":1198.1095199354615,\"elution_time\":8070.6836,\"duration\":15.780762,\"corrected_elution_time\":8070.6836,\"scan_number\":45309,\"peptide_matches_count\":0,\"predicted_elution_time\":8075.417,\"quant_channel_id\":340,\"lcms_feature_id\":4933390,\"lcms_master_feature_id\":4948644,\"selection_level\":2,\"is_reliable\":true}]";
		MasterQuantPeptideIon mqPepIon = new MasterQuantPeptideIon();
		Map<Long, DQuantPeptideIon> map = mqPepIon.parseQuantPeptideIonFromProperties(ot);
		System.out.println(map.size());
	}

}
