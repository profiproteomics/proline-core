package fr.proline.core.orm.msi.dto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

public class DSpectrum {

	public static final int MASSES_INDEX = 0;
	public static final int INTENSITIES_INDEX = 1;

	private long m_id;

	private Integer m_firstScan;
	private Integer m_lastScan;

	private Float m_firstTime;
	private Float m_lastTime;

	private byte[] m_intensityList = null;
	private byte[] m_mozList = null;
	private double[][] m_massIntensitiesValues = null;

	private Integer m_precursorCharge;
	private Float m_precursorIntensity;
	private Double m_precursorMoz;

	private String m_title = null;

	public DSpectrum() {
	}

	public DSpectrum(
		long id,
		Integer firstScan,
		Float firstTime,
		Float lastTime,
		byte[] intensityList,
		byte[] mozeList,
		Integer precursorCharge,
		Float precursorIntensity,
		Double precursorMoz,
		String title) {
		m_id = id;

		m_firstScan = firstScan;

		m_firstTime = firstTime;
		m_lastTime = lastTime;

		m_intensityList = intensityList;
		m_mozList = mozeList;

		m_precursorCharge = precursorCharge;
		m_precursorIntensity = precursorIntensity;
		m_precursorMoz = precursorMoz;

		m_title = title;
	}

	public double[] getMasses() {
		return getMassIntensityValues()[MASSES_INDEX];
	}

	public double[] getIntensities() {
		return getMassIntensityValues()[INTENSITIES_INDEX];
	}

	public double[][] getMassIntensityValues() {
		if (m_massIntensitiesValues == null) {
			if ((m_intensityList == null) || (m_mozList == null)) {
				return null;
			}

			ByteBuffer intensityByteBuffer = ByteBuffer.wrap(m_intensityList).order(ByteOrder.LITTLE_ENDIAN);
			FloatBuffer intensityFloatBuffer = intensityByteBuffer.asFloatBuffer();
			double[] intensityDoubleArray = new double[intensityFloatBuffer.remaining()];
			for (int i = 0; i < intensityDoubleArray.length; i++) {
				intensityDoubleArray[i] = (double) intensityFloatBuffer.get();
			}

			ByteBuffer massByteBuffer = ByteBuffer.wrap(m_mozList).order(ByteOrder.LITTLE_ENDIAN);
			DoubleBuffer massDoubleBuffer = massByteBuffer.asDoubleBuffer();
			double[] massDoubleArray = new double[massDoubleBuffer.remaining()];
			for (int i = 0; i < massDoubleArray.length; i++) {
				massDoubleArray[i] = massDoubleBuffer.get();
			}

			int size = intensityDoubleArray.length;
			m_massIntensitiesValues = new double[2][size];
			for (int i = 0; i < size; i++) {
				m_massIntensitiesValues[MASSES_INDEX][i] = massDoubleArray[i];
				m_massIntensitiesValues[INTENSITIES_INDEX][i] = intensityDoubleArray[i];
			}
		}
		return m_massIntensitiesValues;
	}


	public long getId() {
		return m_id;
	}

	public void setId(long id) {
		m_id = id;
	}

	public Integer getFirstScan() {
		return m_firstScan;
	}

	public void setFirstScan(Integer firstScan) {
		m_firstScan = firstScan;
	}

	public Integer getLastScan() {
		return m_lastScan;
	}

	public void setLastScan(Integer lastScan) {
		m_lastScan = lastScan;
	}

	public Float getFirstTime() {
		return m_firstTime;
	}

	public void setFirstTime(Float firstTime) {
		m_firstTime = firstTime;
	}

	public byte[] getIntensityList() {
		return m_intensityList;
	}

	public void setIntensityList(byte[] intensityList) {
		m_intensityList = intensityList;
	}

	public Float getLastTime() {
		return m_lastTime;
	}

	public void setLastTime(Float lastTime) {
		m_lastTime = lastTime;
	}

	public byte[] getMozList() {
		return m_mozList;
	}

	public void setMozList(byte[] mozList) {
		m_mozList = mozList;
	}

	public Integer getPrecursorCharge() {
		return m_precursorCharge;
	}

	public void setPrecursorCharge(Integer precursorCharge) {
		m_precursorCharge = precursorCharge;
	}

	public Float getPrecursorIntensity() {
		return m_precursorIntensity;
	}

	public void setPrecursorIntensity(Float precursorIntensity) {
		m_precursorIntensity = precursorIntensity;
	}

	public Double getPrecursorMoz() {
		return m_precursorMoz;
	}

	public void setPrecursorMoz(final Double precursorMoz) {
		m_precursorMoz = precursorMoz;
	}

	public String getTitle() {
		return m_title;
	}

	public void setTitle(String title) {
		m_title = title;
	}

}
