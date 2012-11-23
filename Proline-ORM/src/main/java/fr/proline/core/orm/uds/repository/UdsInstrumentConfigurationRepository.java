package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.InstrumentConfiguration;
import fr.proline.core.orm.utils.JPARepository;
import fr.proline.util.StringUtils;

public class UdsInstrumentConfigurationRepository extends JPARepository {

    public UdsInstrumentConfigurationRepository(final EntityManager udsEm) {
	super(udsEm);
    }

    public InstrumentConfiguration findInstrumConfForNameAndMs1AndMsn(final String name,
	    final String ms1Analyzer, final String msnAnalyzer) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	if (StringUtils.isEmpty(ms1Analyzer)) {
	    throw new IllegalArgumentException("Invalid ms1Analyzer");
	}

	InstrumentConfiguration result = null;

	TypedQuery<InstrumentConfiguration> query = null;

	if (msnAnalyzer == null) { // Assume NULL <> "" (empty)
	    query = getEntityManager().createNamedQuery("findUdsInstrumConfForNameAndMs1",
		    InstrumentConfiguration.class);
	} else {
	    query = getEntityManager().createNamedQuery("findUdsInstrumConfForNameAndMs1AndMsn",
		    InstrumentConfiguration.class);
	    query.setParameter("msnAnalyzer", msnAnalyzer.toUpperCase());
	}

	/* In all cases give InstrumentConfiguration name and ms1Analyzer */
	query.setParameter("name", name.toUpperCase());
	query.setParameter("ms1Analyzer", ms1Analyzer.toUpperCase());

	final List<InstrumentConfiguration> instrumConfs = query.getResultList();

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
