package fr.proline.core.orm.lcms;

import java.util.List;

public interface IPeakelData {
	public abstract List<Long> getScanIds();

	public abstract List<Float> getElutionTimes();

	public abstract List<Double> getMzValues();

	public abstract List<Float> getIntensityValues();
}
