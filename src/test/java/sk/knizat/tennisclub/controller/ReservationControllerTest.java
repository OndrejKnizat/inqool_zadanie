package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sk.knizat.tennisclub.config.SecurityConfig;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.CustomerResponse;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.reservation.UpdateReservationRequest;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.service.ReservationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(ReservationController.class)
@Import(SecurityConfig.class)
class ReservationControllerTest {

    private static final String BASE = "/api/reservations";
    private static final Instant T = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant START = Instant.parse("2026-06-02T10:00:00Z");
    private static final Instant END = Instant.parse("2026-06-02T11:30:00Z");
    private static final String PHONE = "+421900000001";
    private static final ReservationResponse RESERVATION = new ReservationResponse(1L, 1, "Centre court",
            START, END, GameTypeDto.SINGLES, new BigDecimal("180.00"), new CustomerResponse(PHONE, "Jane"), T);
    private static final String VALID_CREATE = "{\"courtNumber\":1,\"startTime\":\"2026-06-02T10:00:00Z\","
            + "\"endTime\":\"2026-06-02T11:30:00Z\",\"gameType\":\"SINGLES\",\"phoneNumber\":\"+421 900 000 001\","
            + "\"customerName\":\"Jane\"}";
    private static final String VALID_UPDATE = "{\"courtNumber\":2,\"startTime\":\"2026-06-02T10:00:00Z\","
            + "\"endTime\":\"2026-06-02T11:30:00Z\",\"gameType\":\"DOUBLES\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    @Test
    void should_returnListWithNestedCustomer_when_getAllWithoutFilters() throws Exception {
        when(service.findAll(null, null, false)).thenReturn(List.of(RESERVATION));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"price\":180.00")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].courtNumber").value(1))
                .andExpect(jsonPath("$[0].courtName").value("Centre court"))
                .andExpect(jsonPath("$[0].startTime").value("2026-06-02T10:00:00Z"))
                .andExpect(jsonPath("$[0].endTime").value("2026-06-02T11:30:00Z"))
                .andExpect(jsonPath("$[0].gameType").value("SINGLES"))
                .andExpect(jsonPath("$[0].price").value(180.00))
                .andExpect(jsonPath("$[0].customer.phoneNumber").value(PHONE))
                .andExpect(jsonPath("$[0].customer.name").value("Jane"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-06-01T10:00:00Z"));
    }

    @Test
    void should_forwardQueryParams_when_getAllWithFilters() throws Exception {
        when(service.findAll(1, PHONE, true)).thenReturn(List.of());

        mockMvc.perform(get(BASE).param("courtNumber", "1").param("phoneNumber", PHONE).param("futureOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(service).findAll(1, PHONE, true);
    }

    @Test
    void should_return400Problem_when_courtNumberFilterNotANumber() throws Exception {
        mockMvc.perform(get(BASE).param("courtNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"))
                .andExpect(jsonPath("$.detail").value("Parameter 'courtNumber' must be of type Integer"));
    }

    @Test
    void should_return400Problem_when_phoneFilterMalformed() throws Exception {
        when(service.findAll(null, "abc", false)).thenThrow(new ValidationException("phoneNumber 'abc' is not valid"));

        mockMvc.perform(get(BASE).param("phoneNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("phoneNumber 'abc' is not valid"));
    }

    @Test
    void should_returnOne_when_getByIdExists() throws Exception {
        when(service.findById(1L)).thenReturn(RESERVATION);

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customer.name").value("Jane"));
    }

    @Test
    void should_return404Problem_when_getByIdMissing() throws Exception {
        when(service.findById(9L)).thenThrow(NotFoundException.of("Reservation", 9L));

        mockMvc.perform(get(BASE + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("Reservation with id 9 not found"));
    }

    @Test
    void should_return201WithLocation_when_postValid() throws Exception {
        when(service.create(any())).thenReturn(RESERVATION);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(BASE + "/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.price").value(180.00))
                .andExpect(jsonPath("$.customer.phoneNumber").value(PHONE));

        verify(service).create(new CreateReservationRequest(1, START, END, GameTypeDto.SINGLES,
                "+421 900 000 001", "Jane"));
    }

    @Test
    void should_return400WithErrors_when_postMissingFields() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.courtNumber").exists())
                .andExpect(jsonPath("$.errors.startTime").exists())
                .andExpect(jsonPath("$.errors.endTime").exists())
                .andExpect(jsonPath("$.errors.gameType").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").exists())
                .andExpect(jsonPath("$.errors.customerName").exists());
    }

    @Test
    void should_return400WithErrors_when_postFieldsInvalid() throws Exception {
        String body = "{\"courtNumber\":0,\"startTime\":\"2026-06-02T10:00:00Z\",\"endTime\":\"2026-06-02T11:30:00Z\","
                + "\"gameType\":\"SINGLES\",\"phoneNumber\":\"abc\",\"customerName\":\"" + "x".repeat(101) + "\"}";

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courtNumber").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").value(startsWith("must be a phone number")))
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.startTime").doesNotExist());
    }

    @Test
    void should_return400Malformed_when_gameTypeUnknown() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE.replace("SINGLES", "TRIPLES")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void should_return400Malformed_when_instantUnparseable() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE.replace("2026-06-02T10:00:00Z", "tomorrow")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void should_return400Problem_when_postOverlaps() throws Exception {
        when(service.create(any()))
                .thenThrow(new ValidationException("Reservation overlaps with an existing reservation on court 1"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Reservation overlaps with an existing reservation on court 1"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void should_return404Problem_when_postUnknownCourt() throws Exception {
        when(service.create(any())).thenThrow(new NotFoundException("Court with number 1 not found"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Court with number 1 not found"));
    }

    @Test
    void should_return409Problem_when_courtLockCannotBeAcquired() throws Exception {
        when(service.create(any())).thenThrow(
                new ConflictException("Court with number 1 is locked by a concurrent reservation, please retry"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail")
                        .value("Court with number 1 is locked by a concurrent reservation, please retry"));
    }

    @Test
    void should_return200_when_putValid() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(RESERVATION);

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(service).update(1L, new UpdateReservationRequest(2, START, END, GameTypeDto.DOUBLES));
    }

    @Test
    void should_return400WithErrors_when_putInvalidBody() throws Exception {
        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtNumber\":-1,\"gameType\":\"SINGLES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courtNumber").exists())
                .andExpect(jsonPath("$.errors.startTime").exists())
                .andExpect(jsonPath("$.errors.endTime").exists())
                .andExpect(jsonPath("$.errors.gameType").doesNotExist());
    }

    @Test
    void should_return404_when_putMissing() throws Exception {
        when(service.update(eq(9L), any())).thenThrow(NotFoundException.of("Reservation", 9L));

        mockMvc.perform(put(BASE + "/9").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return400_when_putIntervalInvalid() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new ValidationException("startTime must be before endTime"));

        mockMvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(VALID_UPDATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("startTime must be before endTime"));
    }

    @Test
    void should_return204_when_deleteSucceeds() throws Exception {
        mockMvc.perform(delete(BASE + "/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(1L);
    }

    @Test
    void should_return404_when_deleteMissing() throws Exception {
        doThrow(NotFoundException.of("Reservation", 9L)).when(service).delete(9L);

        mockMvc.perform(delete(BASE + "/9"))
                .andExpect(status().isNotFound());
    }
}
