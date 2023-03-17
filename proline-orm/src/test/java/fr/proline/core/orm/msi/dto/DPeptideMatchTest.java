package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.Peptide;
import org.junit.Assert;
import org.junit.Test;


public class DPeptideMatchTest {

    String serializedPropJson =  "{\"mascot_properties\":{\"expectation_value\":0.009677573176870657,\"ambiguity_string\":\"5,X,Q\",\"used_peaks_count\":13},\"isotope_offset\":0}";

    String sequence = "GYDQXAYDGKDYIALNEDLR";


    @Test
    public void disambiguatedSeqTest(){

        Peptide p = new Peptide();
        p.setSequence(sequence);
        DPeptideMatch pm = new DPeptideMatch(-1,1,2,0.002f,666.0,0,25.0f,-1,1,1,serializedPropJson);
        pm.setPeptide(p);

        long start = System.currentTimeMillis();
        for (int k = 0; k < 100000; k++) {
            String seq = pm.getDisambiguatedSeq();
            Assert.assertEquals(seq, "GYDQQAYDGKDYIALNEDLR");
        }
        long stop = System.currentTimeMillis();
        System.out.println("elapsed time (ms): "+ (stop - start));
    }


}
