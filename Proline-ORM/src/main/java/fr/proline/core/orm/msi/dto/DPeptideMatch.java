package fr.proline.core.orm.msi.dto;

import fr.proline.core.orm.msi.Peptide;

/**
 *
 * @author JM235353
 */
public class DPeptideMatch {
    private long m_id;
    private Integer m_rank;
    private int m_charge;
    private Float m_deltaMoz;
    private double m_experimentalMoz;
    private int m_missedCleavage;
    private Float m_score;
    private Peptide m_p;
    private long m_resultSetId;
    
    private DMsQuery m_msQuery;
    private boolean m_msQuerySet;
    
    private DProteinMatch[] m_proteinMatchArray = null;
    private String m_proteinSetStringList = null;
    

    
    public DPeptideMatch(long id, Integer rank, int charge, Float deltaMoz, double experimentalMoz, int missedCleavage, Float score, long resultSetId) {
        m_id = id;
        m_rank = rank;
        m_charge = charge;
        m_deltaMoz = deltaMoz;
        m_experimentalMoz = experimentalMoz;
        m_missedCleavage = missedCleavage;
        m_score = score;
        m_resultSetId = resultSetId;
        
        m_p = null;
        m_msQuery = null;
        m_msQuerySet = false;
        m_proteinMatchArray = null;
    }
    
    public long getId() {
        return m_id;
    }
    
    public void setPeptide(Peptide p) {
        this.m_p = p;
    }
    
    public Peptide getPeptide() {
        return m_p;
    }
    
    public Float getScore() {
        return m_score;
    }
    
    public Integer getRank() {
    	return m_rank;
    }
    
    public int getCharge() {
        return m_charge;
    }
    
    public double getExperimentalMoz() {
        return m_experimentalMoz;
    }
    
    public Float getDeltaMoz() {
        return m_deltaMoz;
    }
    
    public int getMissedCleavage() {
        return m_missedCleavage;
    }
    
    public long getResultSetId() {
        return m_resultSetId;
    }
    
    public void setMsQuery(DMsQuery msQuery) {
        m_msQuery = msQuery;
        m_msQuerySet = true;
    }
    
    public DMsQuery getMsQuery() {
        return m_msQuery;
    }  
    
    public boolean isMsQuerySet() {
        return m_msQuerySet;
    }
    
    public void setProteinSetStringList(String s) {
        m_proteinSetStringList = s;
    }

    public String getProteinSetStringList() {
        return m_proteinSetStringList;
    }

    public DProteinMatch[] getProteinMatches() {
        return m_proteinMatchArray;
    }

    public void setProteinMatches(DProteinMatch[] proteinMatchArray) {
        m_proteinMatchArray = proteinMatchArray;
    }

}
