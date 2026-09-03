package sk.knizat.tennisclub.dao.impl;

import org.springframework.stereotype.Repository;
import sk.knizat.tennisclub.dao.AbstractDao;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.entity.User;

import java.util.Optional;

/** JPQL implementation of {@link UserDao}. */
@Repository
public class UserDaoImpl extends AbstractDao<User> implements UserDao {

    public UserDaoImpl() {
        super(User.class);
    }

    @Override
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return singleResult(em.createQuery(
                        "SELECT u FROM User u WHERE u.phoneNumber = :phoneNumber AND u.deleted = false", User.class)
                .setParameter("phoneNumber", phoneNumber));
    }
}
