package com.wylie.registration.web;

import com.wylie.registration.security.AuthUser;
import com.wylie.registration.service.AuthService;
import com.wylie.registration.web.dto.LoginRequest;
import com.wylie.registration.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthUser user = AuthUser.current();
        return Map.of("id", user.id(), "role", user.role().name(), "name", user.name(),
                "username", user.username());
    }
}