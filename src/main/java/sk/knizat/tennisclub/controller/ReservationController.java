package sk.knizat.tennisclub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.reservation.UpdateReservationRequest;
import sk.knizat.tennisclub.service.ReservationService;

import java.net.URI;
import java.util.List;

/** REST endpoints of reservations ({@code /api/reservations}). */
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Court reservations")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @Operation(summary = "List reservations, optionally filtered by court number and/or phone number")
    public List<ReservationResponse> findAll(
            @RequestParam(required = false) Integer courtNumber,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(defaultValue = "false") boolean futureOnly) {
        return reservationService.findAll(courtNumber, phoneNumber, futureOnly);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation by id")
    @ApiResponse(responseCode = "404", description = "Reservation not found or deleted")
    public ReservationResponse findById(@PathVariable Long id) {
        return reservationService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a reservation; returns the computed price",
            description = "Creates the customer when the phone number is unknown. Price = whole minutes x "
                    + "pricePerMinute of the court surface, x1.5 for doubles.")
    @ApiResponse(responseCode = "201", description = "Reservation created, price included")
    @ApiResponse(responseCode = "400", description = "Invalid payload or interval (not whole minutes, start "
            + "after end, in the past, outside the allowed duration) or overlap with another reservation on the "
            + "same court")
    @ApiResponse(responseCode = "404", description = "Court number does not exist")
    @ApiResponse(responseCode = "409", description = "Court row lock timed out or the customer was created "
            + "concurrently; retry the request")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a reservation (court, interval, game type); price is recalculated")
    @ApiResponse(responseCode = "200", description = "Reservation updated")
    @ApiResponse(responseCode = "400", description = "Invalid interval or overlap with another reservation")
    @ApiResponse(responseCode = "404", description = "Reservation or court not found")
    @ApiResponse(responseCode = "409", description = "Court row lock timed out; retry the request")
    public ReservationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateReservationRequest request) {
        return reservationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a reservation")
    public void delete(@PathVariable Long id) {
        reservationService.delete(id);
    }
}
