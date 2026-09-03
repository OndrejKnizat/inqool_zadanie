package sk.knizat.tennisclub.dao;

import sk.knizat.tennisclub.entity.User;

import java.util.Optional;

/** DAO of {@link User}. */
public interface UserDao extends GenericDao<User> {

    /** Finds a non-deleted user by the (already normalised) phone number. */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /** Number of non-deleted users with the ADMIN role (guards against locking every administrator out). */
    long countActiveAdmins();
}
