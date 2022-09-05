package fr.proline.core.orm.lcms;

import java.util.ArrayList;
import java.util.List;

import org.msgpack.MessagePack;
import org.msgpack.annotation.Message;

/**
 * class needed for the deserialisation of the peaks (data from peakel.peaks clob)
 * @author MB243701
 *
 */
@Message
public class PeakelDataMatrix implements IPeakelData {

	private List<Long> scanIds;
	private List<Float> elutionTimes;
	private List<Double> mzValues;
	private List<Float> intensityValues;

	public PeakelDataMatrix() {

	}

	public PeakelDataMatrix(
		List<Long> scanIds,
		List<Float> elutionTimes,
		List<Double> mzValues,
		List<Float> intensityValues
	) {
		super();
		this.scanIds = scanIds;
		this.elutionTimes = elutionTimes;
		this.mzValues = mzValues;
		this.intensityValues = intensityValues;
	}

	@Override
	public List<Long> getScanIds() {
		return scanIds;
	}

	public void setScanIds(List<Long> scanIds) {
		this.scanIds = scanIds;
	}

	@Override
	public List<Float> getElutionTimes() {
		return elutionTimes;
	}

	public void setElutionTimes(List<Float> elutionTimes) {
		this.elutionTimes = elutionTimes;
	}

	@Override
	public List<Double> getMzValues() {
		return mzValues;
	}

	public void setMzValues(List<Double> mzValues) {
		this.mzValues = mzValues;
	}

	@Override
	public List<Float> getIntensityValues() {
		return intensityValues;
	}

	public void setIntensityValues(List<Float> intensityValues) {
		this.intensityValues = intensityValues;
	}

	public Peak getPeak(int id) {
		if (id < 0 || id >= getNbPeaks()) {
			return null;
		}
		return new Peak(scanIds.get(id), mzValues.get(id), elutionTimes.get(id), intensityValues.get(id));
	}

	public int getNbPeaks() {
		return mzValues == null ? 0 : mzValues.size();
	}

	public static PeakelDataMatrix getPeaks(byte[] blob) throws Exception {
		MessagePack msgpack = new MessagePack();
		// the return object is not a PeakelDataMatrix: ArrayValueImpl object => rebuild the different list with Java objects
		Object o = msgpack.read(blob);
		PeakelDataMatrix peakelDataMatrix = new PeakelDataMatrix();
		// same here, it's not a List of Float or Double, etc. msgPack object to be transform as Float, Double, etc...
		List so = (List) ((List) o).get(0);
		List eo = (List) ((List) o).get(1);
		List mo = (List) ((List) o).get(2);
		List io = (List) ((List) o).get(3);

		List<Long> scanIds = new ArrayList<>();
		int nb = so.size();
		for (int i = 0; i < nb; i++) {
			scanIds.add(Long.valueOf(so.get(i).toString()));
		}
		List<Float> elutionTimes = new ArrayList<>();
		nb = eo.size();
		for (int i = 0; i < nb; i++) {
			elutionTimes.add(Float.valueOf(eo.get(i).toString()));
		}
		List<Double> mzValues = new ArrayList<>();
		nb = mo.size();
		for (int i = 0; i < nb; i++) {
			mzValues.add( Double.valueOf(mo.get(i).toString()));
		}
		List<Float> intensities = new ArrayList<>();
		nb = io.size();
		for (int i = 0; i < nb; i++) {
			intensities.add( Float.valueOf(io.get(i).toString()));
		}

		peakelDataMatrix.setScanIds(scanIds);
		peakelDataMatrix.setElutionTimes(elutionTimes);
		peakelDataMatrix.setMzValues(mzValues);
		peakelDataMatrix.setIntensityValues(intensities);
		return peakelDataMatrix;

	}
}
