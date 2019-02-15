package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.InstrumentConfig;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class MsiInstrumentConfigRepository {

	private MsiInstrumentConfigRepository() {
	}

	public static InstrumentConfig findInstrumConfForNameAndMs1AndMsn(
		final EntityManager msiEm,
		final String name,
		final String ms1Analyzer,
		final String msnAnalyzer) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(name)) {
			throw new IllegalArgumentException("Invalid name");
		}

		if (StringUtils.isEmpty(ms1Analyzer)) {
			throw new IllegalArgumentException("Invalid ms1Analyzer");
		}

		InstrumentConfig result = null;

		TypedQuery<InstrumentConfig> query = null;

		if (msnAnalyzer == null) { // Assume NULL <> "" (empty)
			query = msiEm.createNamedQuery("findMsiInstrumConfForNameAndMs1", InstrumentConfig.class);
		} else {
			query = msiEm.createNamedQuery("findMsiInstrumConfForNameAndMs1AndMsn", InstrumentConfig.class);
			query.setParameter("msnAnalyzer", msnAnalyzer.toUpperCase());
		}

		/* In all cases give InstrumentConfiguration name and ms1Analyzer */
		query.setParameter("name", name.toUpperCase());
		query.setParameter("ms1Analyzer", ms1Analyzer.toUpperCase());

		final List<InstrumentConfig> instrumConfs = query.getResultList();

		if ((instrumConfs != null) && !instrumConfs.isEmpty()) {

			if (instrumConfs.size() == 1) {
				result = instrumConfs.get(0);
			} else {
				throw new NonUniqueResultException(
					"There are more than one InstrumentConfiguration for given name, ms1Analyzer and msnAnalyzer");
			}

		}

		return result;
	}

}
