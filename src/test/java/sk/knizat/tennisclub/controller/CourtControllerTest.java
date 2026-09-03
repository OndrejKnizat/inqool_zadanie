package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.service.CourtService;
import sk.knizat.tennisclub.support.AbstractWebMvcTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourtController.class)
@WithMockUser(roles = AbstractWebMvcTest.ROLE_ADMIN)
class CourtControllerTest extends AbstractWebMvcTest {

    private static final String BASE = "/api/courts";
    private static final Instant T = Instant.parse("2026-06-01T10:00:00Z");
    private static final SurfaceTypeResponse CLAY =
            new SurfaceTypeResponse(3L, "Clay", new BigDecimal("2.50"), T, T);
    private static final CourtResponse COURT_1 = new CourtResponse(1L, 1, "Centre court", CLAY, T, T);
    private static final String VALID_BODY = "{\"courtNumber\":1,\"name\":\"Centre court\",\"surfaceTypeId\":3}";

    @MockitoBean
    private CourtService service;

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_returnListWithNestedSurface_when_getAll() throws Exception {
        when(service.findAll()).thenReturn(List.of(COURT_1));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].courtNumber").value(1))
                .andExpect(jsonPath("$[0].name").value("Centre court"))
                .andExpect(jsonPath("$[0].surfaceType.id").value(3))
                .andExpect(jsonPath("$[0].surfaceType.name").value("Clay"))
                .andExpect(jsonPath("$[0].surfaceType.pricePerMinute").value(2.5))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-06-01T10:00:00Z"));
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_returnOne_when_getByIdExists() throws Exception {
        when(service.findById(1L)).thenReturn(COURT_1);

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.courtNumber").value(1))
                .andExpect(jsonPath("$.surfaceType.name").value("Clay"));
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_return404Problem_when_getByIdMissing() throws Exception {
        when(service.findById(9L)).thenThrow(NotFoundException.of("Court", 9L));

        mockMvc.perform(get(BASE + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("Court with id 9 not found"));
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_return400Problem_when_idIsNotANumber() throws Exception {
        mockMvc.perform(get(BASE + "/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value("Parameter 'id' must be of type Long"));
    }

    @Test
    void should_return201WithLocation_when_postValid() throws Exception {
        when(service.create(any())).thenReturn(COURT_1);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.surfaceType.id").value(3));

        verify(service).create(new CourtRequest(1, "Centre court", 3L));
    }

    @Test
    void should_return201_when_postWithoutName() throws Exception {
        when(service.create(any())).thenReturn(new CourtResponse(2L, 2, null, CLAY, T, T));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtNumber\":2,\"surfaceTypeId\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").doesNotExist());

        verify(service).create(new CourtRequest(2, null, 3L));
    }

    @Test
    void should_return400WithErrors_when_postInvalidBody() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtNumber\":0,\"name\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.courtNumber").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.surfaceTypeId").exists());
    }

    @Test
    void should_return400WithErrors_when_courtNumberMissing() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surfaceTypeId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courtNumber").exists())
                .andExpect(jsonPath("$.errors.surfaceTypeId").doesNotExist());
    }

    @Test
    void should_return400Problem_when_bodyUnreadable() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void should_return409Problem_when_postDuplicateNumber() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Court with number 1 already exists"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Court with number 1 already exists"));
    }

    @Test
    void should_return400Problem_when_postUnknownSurface() throws Exception {
        when(service.create(any())).thenThrow(new ValidationException("surfaceTypeId 3 does not exist"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("surfaceTypeId 3 does not exist"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void should_return200_when_putValid() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(COURT_1);

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.surfaceType.name").value("Clay"));

        verify(service).update(1L, new CourtRequest(1, "Centre court", 3L));
    }

    @Test
    void should_return400WithErrors_when_putInvalidBody() throws Exception {
        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtNumber\":-1,\"surfaceTypeId\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courtNumber").exists());
    }

    @Test
    void should_return404_when_putMissing() throws Exception {
        when(service.update(eq(9L), any())).thenThrow(NotFoundException.of("Court", 9L));

        mockMvc.perform(put(BASE + "/9").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return409_when_putNumberTakenByOther() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new ConflictException("Court with number 1 already exists"));

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Court with number 1 already exists"));
    }

    @Test
    void should_return204_when_deleteSucceeds() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @Test
    void should_return409_when_deleteBlockedByReservations() throws Exception {
        doThrow(new ConflictException("has unfinished reservations")).when(service).delete(1L);

        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("has unfinished reservations"));
    }

    @Test
    void should_return404_when_deleteMissing() throws Exception {
        doThrow(NotFoundException.of("Court", 9L)).when(service).delete(9L);

        mockMvc.perform(delete(BASE + "/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void should_return401Problem_when_noToken() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"))
                .andExpect(jsonPath("$.instance").value(BASE + "/1"));

        verify(service, never()).delete(any());
    }

    @Test
    @WithAnonymousUser
    void should_return401Problem_when_tokenRejectedByDecoder() throws Exception {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("expired"));

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    @WithAnonymousUser
    void should_return401Problem_when_refreshTokenUsedAsAccessToken() throws Exception {
        when(jwtDecoder.decode("refresh")).thenReturn(Jwt.withTokenValue("refresh")
                .header("alg", "HS256").subject("+421900000001")
                .claim("role", "ADMIN").claim("type", "refresh").build());

        mockMvc.perform(get(BASE).header(HttpHeaders.AUTHORIZATION, "Bearer refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    @WithAnonymousUser
    void should_authenticateFromRoleClaim_when_accessTokenValid() throws Exception {
        when(jwtDecoder.decode("access")).thenReturn(Jwt.withTokenValue("access")
                .header("alg", "HS256").subject("+421900000001")
                .claim("role", "ADMIN").claim("type", "access").build());

        mockMvc.perform(delete(BASE + "/1").header(HttpHeaders.AUTHORIZATION, "Bearer access"))
                .andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_return403Problem_when_userRoleOnAdminEndpoint() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Insufficient role for this operation"))
                .andExpect(jsonPath("$.instance").value(BASE + "/1"));

        verify(service, never()).delete(any());
    }
}
