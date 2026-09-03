package sk.knizat.tennisclub.dao;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.support.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration tests of {@link UserDao}. */
class UserDaoTest extends AbstractDaoTest {

    @Autowired
    private UserDao userDao;

    @Test
    void should_findByPhoneNumber_when_nonDeletedExists() {
        User user = fixtures.persistUser("+421900000001");
        fixtures.flushAndClear();

        assertThat(userDao.findByPhoneNumber("+421900000001")).get()
                .extracting(User::getId, User::getRole).containsExactly(user.getId(), Role.USER);
        assertThat(userDao.findByPhoneNumber("+421900000002")).isEmpty();
    }

    @Test
    void should_ignoreDeleted_when_findByPhoneNumber() {
        fixtures.persistDeleted(Fixtures.user("+421900000001", "Deleted"));
        User active = fixtures.persistUser("+421900000001");
        fixtures.flushAndClear();

        assertThat(userDao.findByPhoneNumber("+421900000001")).get()
                .extracting(User::getId).isEqualTo(active.getId());

        userDao.softDelete(userDao.findById(active.getId()).orElseThrow(), clock.instant());
        fixtures.flushAndClear();

        assertThat(userDao.findByPhoneNumber("+421900000001")).isEmpty();
        assertThat(userDao.findById(active.getId())).isEmpty();
        assertThat(userDao.existsById(active.getId())).isFalse();
    }

    @Test
    void should_persistAndListUsers_when_saveAndFindAll() {
        User saved = userDao.save(Fixtures.user("+421900000009", "New"));
        fixtures.flushAndClear();

        assertThat(userDao.findAll()).extracting(User::getId).containsExactly(saved.getId());
        assertThat(userDao.existsById(saved.getId())).isTrue();
    }

    @Test
    void should_rejectSecondActiveUser_when_phoneAlreadyUsed() {
        userDao.save(Fixtures.user("+421900000777", "First"));

        assertThatThrownBy(() -> userDao.save(Fixtures.user("+421900000777", "Second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allowSamePhone_when_previousUserIsSoftDeleted() {
        fixtures.persistDeleted(Fixtures.user("+421900000778", "Old"));

        User again = userDao.save(Fixtures.user("+421900000778", "New"));

        assertThat(userDao.findByPhoneNumber("+421900000778")).get().isEqualTo(again);
    }

    @Test
    void should_countOnlyActiveAdmins_when_countActiveAdmins() {
        User admin = Fixtures.user("+421900000801", "Admin");
        admin.setRole(Role.ADMIN);
        fixtures.persist(admin);
        User deletedAdmin = Fixtures.user("+421900000802", "Gone");
        deletedAdmin.setRole(Role.ADMIN);
        fixtures.persistDeleted(deletedAdmin);
        fixtures.persistUser("+421900000803");
        fixtures.flushAndClear();

        assertThat(userDao.countActiveAdmins()).isEqualTo(1);
    }
}
