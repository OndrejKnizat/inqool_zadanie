package sk.knizat.tennisclub.mapper;

import org.junit.jupiter.api.Test;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    private static User user(String hash, Role role) {
        User user = new User();
        user.setId(5L);
        user.setPhoneNumber("+421900000001");
        user.setName("Jane");
        user.setPasswordHash(hash);
        user.setRole(role);
        return user;
    }

    @Test
    void should_mapFieldsWithoutHash_when_toResponse() {
        UserResponse response = mapper.toResponse(user("hash", Role.ADMIN));

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.phoneNumber()).isEqualTo("+421900000001");
        assertThat(response.name()).isEqualTo("Jane");
        assertThat(response.role()).isEqualTo(RoleDto.ADMIN);
        assertThat(response.canLogin()).isTrue();
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void should_reportCannotLogin_when_noPasswordHash() {
        assertThat(mapper.toResponse(user(null, Role.USER)).canLogin()).isFalse();
    }

    @Test
    void should_carryHash_when_toAuthUser() {
        AuthUser authUser = mapper.toAuthUser(user("hash", Role.USER));

        assertThat(authUser).isEqualTo(new AuthUser(5L, "+421900000001", "hash", RoleDto.USER));
        assertThat(authUser.canLogin()).isTrue();
        assertThat(mapper.toAuthUser(user(null, Role.USER)).canLogin()).isFalse();
    }

    @Test
    void should_returnNull_when_entityNull() {
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toAuthUser(null)).isNull();
    }

    @Test
    void should_convertRolesByName_when_enumHelpersUsed() {
        assertThat(mapper.toEntity(RoleDto.ADMIN)).isEqualTo(Role.ADMIN);
        assertThat(mapper.toDto(Role.USER)).isEqualTo(RoleDto.USER);
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDto(null)).isNull();
    }
}
