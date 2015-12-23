package fr.proline.core.orm.uds;

import java.io.Serializable;

import javax.persistence.*;

import fr.proline.core.orm.uds.PeaklistSoftware.SoftwareRelease;

/**
 * The persistent class for the spec_title_parsing_rule database table.
 * 
 */
@Entity
@Table(name = "spec_title_parsing_rule")
public class SpectrumTitleParsingRule implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;

	@Column(name = "first_cycle")
	private String firstCycle;

	@Column(name = "first_scan")
	private String firstScan;

	@Column(name = "first_time")
	private String firstTime;

	@Column(name = "last_cycle")
	private String lastCycle;

	@Column(name = "last_scan")
	private String lastScan;

	@Column(name = "last_time")
	private String lastTime;

	@Column(name = "raw_file_identifier")
	private String rawFileIdentifier;

	public SpectrumTitleParsingRule() {
	}

	public long getId() {
		return id;
	}

	public void setId(final long pId) {
		id = pId;
	}

	public String getFirstCycle() {
		return this.firstCycle;
	}

	public void setFirstCycle(String firstCycle) {
		this.firstCycle = firstCycle;
	}

	public String getFirstScan() {
		return this.firstScan;
	}

	public void setFirstScan(String firstScan) {
		this.firstScan = firstScan;
	}

	public String getFirstTime() {
		return this.firstTime;
	}

	public void setFirstTime(String firstTime) {
		this.firstTime = firstTime;
	}

	public String getLastCycle() {
		return this.lastCycle;
	}

	public void setLastCycle(String lastCycle) {
		this.lastCycle = lastCycle;
	}

	public String getLastScan() {
		return this.lastScan;
	}

	public void setLastScan(String lastScan) {
		this.lastScan = lastScan;
	}

	public String getLastTime() {
		return this.lastTime;
	}

	public void setLastTime(String lastTime) {
		this.lastTime = lastTime;
	}

	public String getRawFileIdentifier() {
		return this.rawFileIdentifier;
	}

	public void setRawFileIdentifier(String rawFileId) {
		this.rawFileIdentifier = rawFileId;
	}
	
	public enum ParsingRule {
		
		EXTRACT_MSN_RULE(SoftwareRelease.EXTRACT_MSN, "(.+)\\.\\d+\\.\\d+\\.\\d+\\.dta",null,null,".+\\.(\\d+)\\.\\d+\\.\\d+\\.dta",".+\\.\\d+\\.(\\d+)\\.\\d+\\.dta",null,null),
		DATA_ANALYSIS_4_0_RULE(SoftwareRelease.DATA_ANALYSIS_4_0,null,null,null,null,null,"Cmpd.+MSn.+, (\\d+\\.\\d+) min","Cmpd.+MSn.+, (\\d+\\.\\d+) min"),
		DATA_ANALYSIS_4_1_RULE(SoftwareRelease.DATA_ANALYSIS_4_1,null,null,null,"Cmpd.+MS\\d.+, \\d+\\.\\d+ min #(\\d+)","Cmpd.+MS\\d.+, \\d+\\.\\d+ min #(\\d+)","Cmpd.+MS\\d.+, (\\d+\\.\\d+) min","Cmpd.+MS\\d.+, (\\d+\\.\\d+) min"),
		MASCOT_DLL_RULE(SoftwareRelease.MASCOT_DLL,"^File: (.+?)\\.wiff,","Cycle\\(s\\): (\\d+) \\(Experiment \\d+\\)||Cycle\\(s\\): (\\d+), \\d+","Cycle\\(s\\): (\\d+) \\(Experiment \\d+\\)||Cycle\\(s\\): \\d+, (\\d+)",null,null,"Elution: (.+?) to .+? min||.+Elution: (.+?) min","Elution: .+? to (.+?) min||.+Elution: (.+?) min"),
		MASCOT_DISTILLER_RULE(SoftwareRelease.MASCOT_DISTILLER,"\\[.+\\\\(.+?)\\..+\\]",null,null,"in range (\\d+) \\(rt=||Scan (\\d+) \\(rt=","\\) to (\\d+) \\(rt=||Scan (\\d+) \\(rt=","in range \\d+ \\(rt=(\\d+.\\d+)\\)||\\(rt=(\\d+.\\d+)\\)","\\) to \\d+ \\(rt=(\\d+.\\d+)\\)||\\(rt=(\\d+.\\d+)\\)"),
		MAX_QUANT_RULE(SoftwareRelease.MAX_QUANT,"^RawFile: (.+?) FinneganScanNumber:",null,null,"FinneganScanNumber: (\\d+)","FinneganScanNumber: (\\d+)",null,null),
		PROLINE_RULE(SoftwareRelease.PROLINE,"raw_file_identifier:(\\w+?);","first_cycle:(\\d+);","last_cycle:(\\d+);","first_scan:(\\d+);","last_scan:(\\d+);","first_time:(\\d+\\.\\d+);","last_time:(\\d+\\.\\d+);"),
		PROTEIN_PILOT_RULE(SoftwareRelease.PROTEIN_PILOT,"File:\"(\\w+)\\.wiff","Locus:\\d\\.\\d\\.\\d\\.(\\d+)\\.\\d+ File:","Locus:\\d\\.\\d\\.\\d\\.(\\d+)\\.\\d+ File:",null,null,null,null),
		PROTEOME_DISCOVER_RULE(SoftwareRelease.PROTEOME_DISCOVER,null,null,null,"Spectrum\\d+\\s+scans:(\\d+?),","Spectrum\\d+\\s+scans:(\\d+?),",null,null),
		PROTEO_WIZARD_2_0_RULE(SoftwareRelease.PROTEO_WIZARD_2_0,null,null,null,"scan=(\\d+)","scan=(\\d+)",null,null),
		PROTEO_WIZARD_2_1_RULE(SoftwareRelease.PROTEO_WIZARD_2_1,"(.+)\\.\\d+\\.\\d+\\.\\d+$",null,null,".+\\.(\\d+)\\.\\d+\\.\\d+$",".+\\.\\d+\\.(\\d+)\\.\\d+$",null,null),
		PROTEO_WIZARD_3_0_RULE(SoftwareRelease.PROTEO_WIZARD_3_0,"File:\"(.+?)\\..+\",",null,null,"scan=(\\d+)","scan=(\\d+)",null,null),
		SPECTRUM_MILL_RULE(SoftwareRelease.SPECTRUM_MILL,null,null,null,null,null,"Cmpd.+MSn.+, (\\d+\\.\\d+) min","Cmpd.+MSn.+, (\\d+\\.\\d+) min");

		private final PeaklistSoftware.SoftwareRelease m_peaklistSoftware;
		private final String m_rawFileIdentifierRegex;
		private final String m_firstCycleRegex;
		private final String m_lastCycleRegex;
		private final String m_firstScanRegex;
		private final String m_lastScanRegex;
		private final String m_firstTimeRegex;
		private final String m_lastTimeRegex;

		private ParsingRule(
			final PeaklistSoftware.SoftwareRelease peaklistSoftware,
			final String rawFileIdentifierRegex,
			final String firstCycleRegex,
			final String lastCycleRegex,
			final String firstScanRegex,
			final String lastScanRegex,
			final String firstTimeRegex,
			final String lastTimeRegex
		) {
			m_peaklistSoftware = peaklistSoftware;
			m_rawFileIdentifierRegex = rawFileIdentifierRegex;
			m_firstCycleRegex = firstCycleRegex;
			m_lastCycleRegex = lastCycleRegex;
			m_firstScanRegex = firstScanRegex;
			m_lastScanRegex = lastScanRegex;
			m_firstTimeRegex = firstTimeRegex;
			m_lastTimeRegex = lastTimeRegex;
		}
		
		public PeaklistSoftware.SoftwareRelease getPeaklistSoftware() {
			return m_peaklistSoftware;
		}

		public String getRawFileIdentifierRegex() {
			return m_rawFileIdentifierRegex;
		}

		public String getFirstCycleRegex() {
			return m_firstCycleRegex;
		}

		public String getLastCycleRegex() {
			return m_lastCycleRegex;
		}

		public String getFirstScanRegex() {
			return m_firstScanRegex;
		}

		public String getLastScanRegex() {
			return m_lastScanRegex;
		}

		public String getFirstTimeRegex() {
			return m_firstTimeRegex;
		}

		public String getLastTimeRegex() {
			return m_lastTimeRegex;
		}

		@Override
		public String toString() {
			return this.name();
		}

	}

}
