package de.schwabe.jakartamcpserverdemo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PersonService {

    @PersistenceContext(unitName = "default")
    private EntityManager em;

    @Transactional
    public List<Person> findAll() {
        return em.createQuery("select p from Person p order by p.id", Person.class).getResultList();
    }

    @Transactional
    public Optional<Person> findById(long id) {
        return Optional.ofNullable(em.find(Person.class, id));
    }
}
