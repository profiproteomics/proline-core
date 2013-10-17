package fr.proline.core.orm.uds.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Activation;
import fr.proline.core.orm.uds.Activation.ActivationType;
import fr.proline.repository.util.JPAUtils;

public final class ActivationRepository {

  private ActivationRepository() {
  }

  public static Activation findActivationByType(final EntityManager udsEm, final ActivationType activationType) {

    JPAUtils.checkEntityManager(udsEm);
    
    final TypedQuery<Activation> query = udsEm.createNamedQuery("findActivationByType", Activation.class);
    query.setParameter("type", activationType);
    // WARN Throws NoResultException if there is no result
    return query.getSingleResult();
  }
  
}