package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.InstrumentConfig;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.core.orm.utils.StringUtils;

public class MsiInstrumentConfigRepository extends JPARepository {

    public MsiInstrumentConfigRepository(final EntityManager msiEm) {
	super(msiEm);
    }

    public InstrumentConfig findInstrumConfForNameAndMs1AndMsn(final String name, final String ms1Analyzer,
	    final String msnAnalyzer) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	if (StringUtils.isEmpty(ms1Analyzer)) {
	    throw new IllegalArgumentException("Invalid ms1Analyzer");
	}

	InstrumentConfig result = null;

	TypedQuery<InstrumentConfig> query = null;

	if (msnAnalyzer == null) { // Assume NULL <> "" (empty)
	    query = getEntityManager().createNamedQuery("findMsiInstrumConfForNameAndMs1",
		    InstrumentConfig.class);
	} else {
	    query = getEntityManager().createNamedQuery("findMsiInstrumConfForNameAndMs1AndMsn",
		    InstrumentConfig.class);
	    query.setParameter("msnAnalyzer", msnAnalyzer.toLowerCase());
	}

	/* In all cases give InstrumentConfiguration name and ms1Analyzer */
	query.setParameter("name", name.toLowerCase());
	query.setParameter("ms1Analyzer", ms1Analyzer.toLowerCase());

	final List<InstrumentConfig> instrumConfs = query.getResultList();

	if ((instrumConfs != null) && !instrumConfs.isEmpty()) {

	    if (instrumConfs.size() == 1) {
		result = instrumConfs.get(0);
	    } else {
		throw new RuntimeException(
			"There are more than one InstrumentConfiguration for given name, ms1Analyzer and msnAnalyzer");
	    }

	}

	return result;
    }

}
