package sk.knizat.tennisclub.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.service.CourtService;

import java.net.URI;
import java.util.List;

/** REST endpoints of court management ({@code /api/courts}). */
@RestController
@RequestMapping("/api/courts")
@RequiredArgsConstructor
@Tag(name = "Courts", description = "Tennis court management")
public class CourtController {

    private final CourtService courtService;

    @GetMapping
    @Operation(summary = "List courts")
    public List<CourtResponse> findAll() {
        return courtService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a court by id")
    public CourtResponse findById(@PathVariable Long id) {
        return courtService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a court")
    public ResponseEntity<CourtResponse> create(@Valid @RequestBody CourtRequest request) {
        CourtResponse created = courtService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a court")
    public CourtResponse update(@PathVariable Long id, @Valid @RequestBody CourtRequest request) {
        return courtService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a court")
    public void delete(@PathVariable Long id) {
        courtService.delete(id);
    }
}
