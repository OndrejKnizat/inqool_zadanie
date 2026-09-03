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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import sk.knizat.tennisclub.dto.user.CreateUserRequest;
import sk.knizat.tennisclub.dto.user.UpdateUserRequest;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.service.UserService;

import java.net.URI;
import java.security.Principal;
import java.util.List;

/**
 * REST endpoints of user management ({@code /api/users}). Everything is ADMIN-only except
 * {@code GET /api/users/me}, which returns the caller's own account (the principal name is the phone number,
 * i.e. the JWT subject). Responses never contain the password hash.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User account management")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users (ADMIN)")
    public List<UserResponse> findAll() {
        return userService.findAll();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the account of the current user")
    @ApiResponse(responseCode = "404", description = "The account was deleted after the token was issued")
    public UserResponse me(Principal principal) {
        return userService.findByPhoneNumber(principal.getName());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id (ADMIN)")
    @ApiResponse(responseCode = "404", description = "User not found or deleted")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @Operation(summary = "Create a user account (ADMIN)")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Invalid payload (phone number, password length, role)")
    @ApiResponse(responseCode = "409", description = "Phone number already registered")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update name, role and optionally password of a user (ADMIN)")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "409", description = "Demoting the last administrator")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Soft-delete a user (ADMIN); not yourself, not with unfinished reservations")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "409", description = "Own account, the last administrator, or a user with "
            + "unfinished reservations")
    public void delete(@PathVariable Long id, Principal principal) {
        userService.delete(id, principal.getName());
    }
}
