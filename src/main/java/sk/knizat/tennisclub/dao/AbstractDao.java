package sk.knizat.tennisclub.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import sk.knizat.tennisclub.entity.BaseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Base implementation of {@link GenericDao} on top of a plain {@link EntityManager} and JPQL.
 * <p>
 * Every query carries an explicit {@code e.deleted = false} predicate. {@link #findById(Long)} deliberately
 * uses a JPQL query instead of {@code em.find}: {@code em.find} would return a soft-deleted row (and could even
 * serve it from the first-level cache), whereas the query applies the deleted filter uniformly with the other
 * reads and triggers the same auto-flush semantics.
 * <p>
 * The {@code EntityManager} is the single sanctioned field injection in the project: without Spring Data there
 * is no {@code EntityManager} bean, so {@code @PersistenceContext} obtains the shared, transaction-scoped proxy.
 *
 * @param <T> entity type
 */
public abstract class AbstractDao<T extends BaseEntity> implements GenericDao<T> {

    @PersistenceContext
    protected EntityManager em;

    private final Class<T> entityClass;
    private final String entityName;

    /**
     * @param entityClass the entity class; its JPQL entity name is the simple class name (no entity in this
     *                    project overrides {@code @Entity(name)})
     */
    protected AbstractDao(Class<T> entityClass) {
        this.entityClass = entityClass;
        this.entityName = entityClass.getSimpleName();
    }

    /** JPQL entity name of {@code T}. */
    protected String entityName() {
        return entityName;
    }

    @Override
    public Optional<T> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        TypedQuery<T> query = em.createQuery(
                "SELECT e FROM " + entityName() + " e WHERE e.id = :id AND e.deleted = false", entityClass);
        query.setParameter("id", id);
        return singleResult(query);
    }

    @Override
    public List<T> findAll() {
        return em.createQuery(
                        "SELECT e FROM " + entityName() + " e WHERE e.deleted = false ORDER BY e.id", entityClass)
                .getResultList();
    }

    @Override
    public T save(T entity) {
        T managed;
        if (entity.getId() == null) {
            em.persist(entity);
            managed = entity;
        } else {
            managed = em.merge(entity);
        }
        em.flush();
        return managed;
    }

    @Override
    public void softDelete(T entity, Instant now) {
        entity.markDeleted(now);
        em.merge(entity);
        em.flush();
    }

    @Override
    public boolean existsById(Long id) {
        if (id == null) {
            return false;
        }
        Long count = em.createQuery(
                        "SELECT COUNT(e) FROM " + entityName() + " e WHERE e.id = :id AND e.deleted = false",
                        Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return count > 0;
    }

    /**
     * Executes a query expected to return at most one row.
     *
     * @throws NonUniqueResultException when more than one row matches; business keys are unique only among
     *                                  non-deleted rows and that is enforced in the service layer, so a
     *                                  duplicate here is a data inconsistency that must not be hidden
     */
    protected <R> Optional<R> singleResult(TypedQuery<R> query) {
        List<R> rows = query.getResultList();
        if (rows.size() > 1) {
            throw new NonUniqueResultException("Expected at most one row but found " + rows.size());
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Evaluates a {@code SELECT COUNT(...)} query as an existence check. */
    protected boolean exists(TypedQuery<Long> countQuery) {
        return countQuery.getSingleResult() > 0;
    }
}
