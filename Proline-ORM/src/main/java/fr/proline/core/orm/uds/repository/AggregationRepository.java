package fr.proline.core.orm.uds.repository;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;

import fr.proline.core.orm.uds.Aggregation;
import fr.proline.core.orm.uds.Aggregation.ChildNature;
import fr.proline.repository.util.JPAUtils;

public final class AggregationRepository {

  private AggregationRepository() {
  }

  public static Aggregation findAggregationByType(final EntityManager udsEm, final ChildNature childNature) {

    JPAUtils.checkEntityManager(udsEm);
    
    final TypedQuery<Aggregation> query = udsEm.createNamedQuery("findAggregationByType", Aggregation.class);
    query.setParameter("child_nature", childNature);
    
    return query.getSingleResult();
  }
  
}