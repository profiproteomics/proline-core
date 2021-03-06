package fr.proline.core.orm.msi.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.msi.Ptm;
import fr.proline.core.orm.msi.PtmClassification;
import fr.proline.core.orm.msi.PtmEvidence;
import fr.proline.core.orm.msi.PtmEvidence.Type;
import fr.proline.core.orm.msi.PtmSpecificity;
import fr.proline.repository.util.JPAUtils;
import fr.profi.util.StringUtils;

public final class MsiPtmRepository {

	private MsiPtmRepository() {
	}

	/**
	 * Retrieves a <code>Ptm</code> entity from MSi Db by given name (matched ignoring case with
	 * <code>shortName</code> or <code>fullName</code>).
	 * 
	 * @param name
	 *            Name (<code>shortName</code> or <code>fullName</code>) of the <code>Ptm</code> entity, must
	 *            not be empty.
	 * @return Ptm entity or <code>null</code> if not found.
	 */
	public static Ptm findPtmForName(final EntityManager msiEm, final String name) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(name)) {
			throw new IllegalArgumentException("Invalid name");
		}

		Ptm result = null;

		final TypedQuery<Ptm> query = msiEm.createNamedQuery("findMsiPtmForName", Ptm.class);
		query.setParameter("name", name.toUpperCase());

		final List<Ptm> ptms = query.getResultList();

		if ((ptms != null) && !ptms.isEmpty()) {

			if (ptms.size() == 1) {
				result = ptms.get(0);
			} else {
				throw new NonUniqueResultException("There are more than one Ptm for given name");
			}

		}

		return result;
	}

	/**
	 * Retrieves a <code>Ptm</code> entity from MSi Db by given name (matched ignoring case with
	 * <code>shortName</code> ).
	 * 
	 * @param shortName
	 *            <code>shortName</code> of the <code>Ptm</code> entity, must not be empty.
	 * @return Ptm entity or <code>null</code> if not found.
	 */
	// TODO: merge code with the findPtmForName method
	public static Ptm findPtmForShortName(final EntityManager msiEm, final String shortName) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(shortName)) {
			throw new IllegalArgumentException("Invalid short name");
		}

		Ptm result = null;

		final TypedQuery<Ptm> query = msiEm.createNamedQuery("findMsiPtmForShortName", Ptm.class);
		query.setParameter("name", shortName.toUpperCase());

		final List<Ptm> ptms = query.getResultList();

		if ((ptms != null) && !ptms.isEmpty()) {

			if (ptms.size() == 1) {
				result = ptms.get(0);
			} else {
				throw new NonUniqueResultException("There are more than one Ptm for given short name");
			}

		}

		return result;
	}

	public static PtmEvidence findPtmEvidenceByPtmAndType(
		final EntityManager msiEm,
		final long ptmId,
		final Type type) {

		JPAUtils.checkEntityManager(msiEm);

		if (type == null) {
			throw new IllegalArgumentException("Type is null");
		}

		PtmEvidence result = null;

		final TypedQuery<PtmEvidence> query = msiEm.createNamedQuery("findPtmEvidenceByPtmAndType",
			PtmEvidence.class);
		query.setParameter("ptm_id", Long.valueOf(ptmId));
		query.setParameter("type", type);

		final List<PtmEvidence> ptmEvids = query.getResultList();

		if ((ptmEvids != null) && !ptmEvids.isEmpty()) {

			if (ptmEvids.size() == 1) {
				result = ptmEvids.get(0);
			} else {
				throw new NonUniqueResultException(
					"There are more than one PtmEvidence for given ptmId and type");
			}

		}

		return result;
	}

	/**
	 * Retrieves a <code>PtmSpecificity</code> entity from MSi Db by given name, location and residue.
	 * 
	 * @param ptmShortName
	 *            <code>shortName</code> of the Ptm associated with the <code>PtmSpecificity</code> to
	 *            retrieve. Must not be empty, matched ignoring case.
	 * @param location
	 *            Location of the <code>PtmSpecificity</code>. Must not be empty, matched ignoring case.
	 * @param residue
	 *            <code>PtmSpecificity</code> residue. Can be <code>null</code> (for C-term or N-term...)
	 * @return PtmSpecificity entity or <code>null</code> if not found.
	 */
	public static PtmSpecificity findPtmSpecificityForNameLocResidue(
		final EntityManager msiEm,
		final String ptmShortName,
		final String location,
		final Character residue) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(ptmShortName)) {
			throw new IllegalArgumentException("Invalid ptmShortName");
		}

		if (StringUtils.isEmpty(location)) {
			throw new IllegalArgumentException("Invalid location");
		}

		PtmSpecificity result = null;

		TypedQuery<PtmSpecificity> query = null;

		if (residue == null) {
			query = msiEm.createNamedQuery("findMsiPtmSpecForNameAndLoc", PtmSpecificity.class);
		} else {
			query = msiEm.createNamedQuery("findMsiPtmSpecForNameLocResidue", PtmSpecificity.class);
			query.setParameter("residue", residue);
		}

		query.setParameter("location", location.toUpperCase());
		query.setParameter("ptmShortName", ptmShortName.toUpperCase());

		final List<PtmSpecificity> ptms = query.getResultList();

		if ((ptms != null) && !ptms.isEmpty()) {

			if (ptms.size() == 1) {
				result = ptms.get(0);
			} else {
				throw new NonUniqueResultException(
					"There are more than one PtmSpecificity for given name, location and residue");
			}

		}

		return result;
	}

	/**
	 * Retrieves a <code>PtmClassification</code> entity from MSi Db by given name (matched ignoring case).
	 * 
	 * @param name
	 *            Name of the <code>PtmClassification</code> entity, must not be empty.
	 * @return PtmClassification entity or <code>null</code> if not found.
	 */
	public static PtmClassification findPtmClassificationForName(final EntityManager msiEm, final String name) {

		JPAUtils.checkEntityManager(msiEm);

		if (StringUtils.isEmpty(name)) {
			throw new IllegalArgumentException("Invalid name");
		}

		PtmClassification result = null;

		final TypedQuery<PtmClassification> query = msiEm.createNamedQuery("findPtmClassificationForName",
			PtmClassification.class);
		query.setParameter("name", name.toUpperCase());

		final List<PtmClassification> classifications = query.getResultList();

		if ((classifications != null) && !classifications.isEmpty()) {

			if (classifications.size() == 1) {
				result = classifications.get(0);
			} else {
				throw new NonUniqueResultException("There are more than one PtmClassification for given name");
			}

		}

		return result;
	}

}
