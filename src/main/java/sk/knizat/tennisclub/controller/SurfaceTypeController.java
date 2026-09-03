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
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.service.SurfaceTypeService;

import java.net.URI;
import java.util.List;

/** REST endpoints of the surface type code list ({@code /api/surface-types}). */
@RestController
@RequestMapping("/api/surface-types")
@RequiredArgsConstructor
@Tag(name = "Surface types", description = "Court surface code list")
public class SurfaceTypeController {

    private final SurfaceTypeService surfaceTypeService;

    @GetMapping
    @Operation(summary = "List surface types")
    public List<SurfaceTypeResponse> findAll() {
        return surfaceTypeService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a surface type by id")
    public SurfaceTypeResponse findById(@PathVariable Long id) {
        return surfaceTypeService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a surface type")
    public ResponseEntity<SurfaceTypeResponse> create(@Valid @RequestBody SurfaceTypeRequest request) {
        SurfaceTypeResponse created = surfaceTypeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a surface type")
    public SurfaceTypeResponse update(@PathVariable Long id, @Valid @RequestBody SurfaceTypeRequest request) {
        return surfaceTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a surface type")
    public void delete(@PathVariable Long id) {
        surfaceTypeService.delete(id);
    }
}
