package fr.proline.core.orm.uds.repository;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NonUniqueResultException;
import javax.persistence.TypedQuery;

import fr.profi.util.StringUtils;
import fr.proline.core.orm.uds.QuantitationMethod;
import fr.proline.repository.util.JPAUtils;

public class QuantitationMethodRepository {

	private QuantitationMethodRepository() {
	}

	public static QuantitationMethod findQuantMethodForTypeAndAbundanceUnit(
		final EntityManager udsEm,
		final String typeName,
		final String abundanceUnit) {

		JPAUtils.checkEntityManager(udsEm);

		if (StringUtils.isEmpty(typeName)) {
			throw new IllegalArgumentException("Invalid type name");
		}

		if (StringUtils.isEmpty(abundanceUnit)) {
			throw new IllegalArgumentException("Invalid abundanceUnit");
		}

		QuantitationMethod result = null;

		TypedQuery<QuantitationMethod> query = udsEm.createNamedQuery("findQuantMethodForTypeAndUnit", QuantitationMethod.class);
		query.setParameter("searchType", typeName.toUpperCase());
		query.setParameter("searchAbundanceUnit", abundanceUnit.toUpperCase());

		final List<QuantitationMethod> quantMethods = query.getResultList();

		if ((quantMethods != null) && !quantMethods.isEmpty()) {

			if (quantMethods.size() == 1) {
				result = quantMethods.get(0);
			} else {
				throw new NonUniqueResultException("There are more than one QuantitationMethod for given type and abundanceUnit");
			}

		}

		return result;
	}

}
