package fr.proline.core.orm.uds.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Fractionation;
import fr.proline.core.orm.uds.Fractionation.FractionationType;
import fr.proline.repository.util.JPAUtils;

public final class FractionationRepository {

  private FractionationRepository() {
  }

  public static Fractionation findFractionationByType(final EntityManager udsEm, final FractionationType fractionationType) {

    JPAUtils.checkEntityManager(udsEm);
    
    final TypedQuery<Fractionation> query = udsEm.createNamedQuery("findFractionationByType", Fractionation.class);
    query.setParameter("type", fractionationType);
    // WARN Throws NoResultException if there is no result
    return query.getSingleResult();
  }
  
}