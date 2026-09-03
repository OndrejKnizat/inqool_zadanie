package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.CreateUserRequest;
import sk.knizat.tennisclub.dto.user.UpdateUserRequest;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.service.UserService;
import sk.knizat.tennisclub.support.AbstractWebMvcTest;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@WithMockUser(username = UserControllerTest.ADMIN_PHONE, roles = AbstractWebMvcTest.ROLE_ADMIN)
class UserControllerTest extends AbstractWebMvcTest {

    static final String ADMIN_PHONE = "+421900000900";
    static final String USER_PHONE = "+421900000901";

    private static final String BASE = "/api/users";
    private static final Instant T = Instant.parse("2026-06-01T10:00:00Z");
    private static final UserResponse ADMIN = new UserResponse(1L, ADMIN_PHONE, "Admin", RoleDto.ADMIN, true, T, T);
    private static final UserResponse JANE = new UserResponse(2L, USER_PHONE, "Jane", RoleDto.USER, false, T, T);
    private static final String VALID_CREATE =
            "{\"phoneNumber\":\"+421 900 000 901\",\"name\":\"Jane\",\"password\":\"jane-secret\",\"role\":\"USER\"}";
    private static final String VALID_UPDATE = "{\"name\":\"Jane Doe\",\"role\":\"ADMIN\"}";

    @MockitoBean
    private UserService service;

    @Test
    void should_returnListWithoutPasswordHash_when_getAllAsAdmin() throws Exception {
        when(service.findAll()).thenReturn(List.of(ADMIN, JANE));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].phoneNumber").value(ADMIN_PHONE))
                .andExpect(jsonPath("$[0].name").value("Admin"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].canLogin").value(true))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-06-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].canLogin").value(false))
                .andExpect(jsonPath("$[1].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return403Problem_when_getAllAsUser() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Insufficient role for this operation"))
                .andExpect(jsonPath("$.instance").value(BASE));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return403_when_headAllAsUser() throws Exception {
        mockMvc.perform(head(BASE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void should_return401Problem_when_getAllAnonymous() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"))
                .andExpect(jsonPath("$.instance").value(BASE));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_returnOwnAccount_when_getMeAsUser() throws Exception {
        when(service.findByPhoneNumber(USER_PHONE)).thenReturn(JANE);

        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.phoneNumber").value(USER_PHONE))
                .andExpect(jsonPath("$.name").value("Jane"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(service).findByPhoneNumber(USER_PHONE);
    }

    @Test
    void should_returnOwnAccount_when_getMeAsAdmin() throws Exception {
        when(service.findByPhoneNumber(ADMIN_PHONE)).thenReturn(ADMIN);

        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return404Problem_when_getMeForDeletedAccount() throws Exception {
        when(service.findByPhoneNumber(USER_PHONE))
                .thenThrow(new NotFoundException("User with phone number " + USER_PHONE + " not found"));

        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User with phone number " + USER_PHONE + " not found"));
    }

    @Test
    @WithAnonymousUser
    void should_return401_when_getMeAnonymous() throws Exception {
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void should_returnOne_when_getByIdExists() throws Exception {
        when(service.findById(2L)).thenReturn(JANE);

        mockMvc.perform(get(BASE + "/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.phoneNumber").value(USER_PHONE))
                .andExpect(jsonPath("$.canLogin").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void should_return404Problem_when_getByIdMissing() throws Exception {
        when(service.findById(9L)).thenThrow(NotFoundException.of("User", 9L));

        mockMvc.perform(get(BASE + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("User with id 9 not found"));
    }

    @Test
    void should_return400Problem_when_idIsNotANumber() throws Exception {
        mockMvc.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return403_when_getByIdAsUser() throws Exception {
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(service);
    }

    @Test
    void should_return201WithLocation_when_postValid() throws Exception {
        when(service.create(any())).thenReturn(JANE);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/2")))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.phoneNumber").value(USER_PHONE))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(service).create(new CreateUserRequest("+421 900 000 901", "Jane", "jane-secret", RoleDto.USER));
    }

    @Test
    void should_return400WithErrors_when_postWeakPasswordBadPhoneAndMissingRole() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"12\",\"name\":\"Jane\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists())
                .andExpect(jsonPath("$.errors.name").doesNotExist());

        verifyNoInteractions(service);
    }

    @Test
    void should_return400WithErrors_when_postBlankFields() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\" \",\"name\":\"\",\"password\":\"" + "p".repeat(73)
                                + "\",\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").doesNotExist());
    }

    @Test
    void should_return400Problem_when_postUnknownRole() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+421900000901\",\"name\":\"Jane\",\"password\":\"jane-secret\","
                                + "\"role\":\"ROOT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request"));

        verifyNoInteractions(service);
    }

    @Test
    void should_return409Problem_when_postDuplicatePhone() throws Exception {
        when(service.create(any()))
                .thenThrow(new ConflictException("User with phone number " + USER_PHONE + " already exists"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("User with phone number " + USER_PHONE + " already exists"));
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return403_when_postAsUser() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void should_return200_when_putWithoutPassword() throws Exception {
        when(service.update(eq(2L), any()))
                .thenReturn(new UserResponse(2L, USER_PHONE, "Jane Doe", RoleDto.ADMIN, false, T, T));

        mockMvc.perform(put(BASE + "/2").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(service).update(2L, new UpdateUserRequest("Jane Doe", RoleDto.ADMIN, null));
    }

    @Test
    void should_return200_when_putWithPassword() throws Exception {
        when(service.update(eq(2L), any()))
                .thenReturn(new UserResponse(2L, USER_PHONE, "Jane", RoleDto.USER, true, T, T));

        mockMvc.perform(put(BASE + "/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane\",\"role\":\"USER\",\"password\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canLogin").value(true));

        verify(service).update(2L, new UpdateUserRequest("Jane", RoleDto.USER, "new-secret"));
    }

    @Test
    void should_return400WithErrors_when_putInvalidBody() throws Exception {
        mockMvc.perform(put(BASE + "/2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.role").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(service);
    }

    @Test
    void should_return404_when_putMissing() throws Exception {
        when(service.update(eq(9L), any())).thenThrow(NotFoundException.of("User", 9L));

        mockMvc.perform(put(BASE + "/9").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("User with id 9 not found"));
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return403_when_putAsUser() throws Exception {
        mockMvc.perform(put(BASE + "/2").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void should_return204AndPassPrincipalPhone_when_deleteSucceeds() throws Exception {
        mockMvc.perform(delete(BASE + "/2"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(2L, ADMIN_PHONE);
    }

    @Test
    void should_return409Problem_when_deleteSelf() throws Exception {
        doThrow(new ConflictException("User with id 1 is the current user and cannot delete itself"))
                .when(service).delete(1L, ADMIN_PHONE);

        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("User with id 1 is the current user and cannot delete itself"));
    }

    @Test
    void should_return409Problem_when_deleteBlockedByReservations() throws Exception {
        doThrow(new ConflictException("User with id 2 has unfinished reservations and cannot be deleted"))
                .when(service).delete(2L, ADMIN_PHONE);

        mockMvc.perform(delete(BASE + "/2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("User with id 2 has unfinished reservations and cannot be deleted"));
    }

    @Test
    void should_return404_when_deleteMissing() throws Exception {
        doThrow(NotFoundException.of("User", 9L)).when(service).delete(9L, ADMIN_PHONE);

        mockMvc.perform(delete(BASE + "/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return403Problem_when_deleteAsUser() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.instance").value(BASE + "/1"));

        verify(service, never()).delete(any(), any());
    }

    @Test
    @WithAnonymousUser
    void should_return401Problem_when_deleteAnonymous() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(username = USER_PHONE, roles = ROLE_USER)
    void should_return200_when_headMeAsUser() throws Exception {
        when(service.findByPhoneNumber(USER_PHONE)).thenReturn(JANE);

        mockMvc.perform(head(BASE + "/me")).andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    void should_return401_when_postAnonymous() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void should_return401_when_putAnonymous() throws Exception {
        mockMvc.perform(put(BASE + "/2").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithAnonymousUser
    void should_return401_when_getByIdAnonymous() throws Exception {
        mockMvc.perform(get(BASE + "/2")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
