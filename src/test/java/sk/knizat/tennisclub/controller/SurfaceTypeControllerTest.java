package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.service.SurfaceTypeService;
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

@WebMvcTest(SurfaceTypeController.class)
@WithMockUser(roles = AbstractWebMvcTest.ROLE_ADMIN)
class SurfaceTypeControllerTest extends AbstractWebMvcTest {

    private static final String BASE = "/api/surface-types";
    private static final Instant T = Instant.parse("2026-06-01T10:00:00Z");
    private static final SurfaceTypeResponse CLAY =
            new SurfaceTypeResponse(1L, "Clay", new BigDecimal("2.50"), T, T);
    private static final String VALID_BODY = "{\"name\":\"Clay\",\"pricePerMinute\":2.5}";

    @MockitoBean
    private SurfaceTypeService service;

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_returnList_when_getAll() throws Exception {
        when(service.findAll()).thenReturn(List.of(CLAY));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Clay"))
                .andExpect(jsonPath("$[0].pricePerMinute").value(2.5))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-01T10:00:00Z"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-06-01T10:00:00Z"));
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_returnOne_when_getByIdExists() throws Exception {
        when(service.findById(1L)).thenReturn(CLAY);

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Clay"));
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_return404Problem_when_getByIdMissing() throws Exception {
        when(service.findById(9L)).thenThrow(NotFoundException.of("SurfaceType", 9L));

        mockMvc.perform(get(BASE + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("SurfaceType with id 9 not found"));
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
        when(service.create(any())).thenReturn(CLAY);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.pricePerMinute").value(2.5));

        verify(service).create(new SurfaceTypeRequest("Clay", new BigDecimal("2.5")));
    }

    @Test
    void should_return400WithErrors_when_postInvalidBody() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"pricePerMinute\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.pricePerMinute").exists());
    }

    @Test
    void should_return400WithErrors_when_priceHasTooManyDecimals() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clay\",\"pricePerMinute\":1.234}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pricePerMinute").exists())
                .andExpect(jsonPath("$.errors.name").doesNotExist());
    }

    @Test
    void should_return400Problem_when_bodyUnreadable() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed"));
    }

    @Test
    void should_return409Problem_when_postDuplicateName() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("SurfaceType with name 'Clay' already exists"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("SurfaceType with name 'Clay' already exists"));
    }

    @Test
    void should_return400Problem_when_serviceThrowsValidationException() throws Exception {
        when(service.create(any())).thenThrow(new ValidationException("business rule broken"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("business rule broken"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void should_return200_when_putValid() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(CLAY);

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Clay"));
    }

    @Test
    void should_return400WithErrors_when_putInvalidBody() throws Exception {
        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clay\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pricePerMinute").exists());
    }

    @Test
    void should_return404_when_putMissing() throws Exception {
        when(service.update(eq(9L), any())).thenThrow(NotFoundException.of("SurfaceType", 9L));

        mockMvc.perform(put(BASE + "/9").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return204_when_deleteSucceeds() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @Test
    void should_return409_when_deleteBlockedByCourts() throws Exception {
        doThrow(new ConflictException("in use")).when(service).delete(1L);

        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("in use"));
    }

    @Test
    void should_return404_when_deleteMissing() throws Exception {
        doThrow(NotFoundException.of("SurfaceType", 9L)).when(service).delete(9L);

        mockMvc.perform(delete(BASE + "/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void should_return401Problem_when_noToken() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    @Test
    @WithMockUser(roles = ROLE_USER)
    void should_return403Problem_when_userRoleOnAdminEndpoint() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verify(service, never()).create(any());
    }
}
