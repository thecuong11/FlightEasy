package com.fighteasy.controller;

import com.fighteasy.dto.*;
import com.fighteasy.entity.User;
import com.fighteasy.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req, response));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(req, request, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refresh_token") String token, HttpServletResponse response) {
       return ResponseEntity.ok(authService.refresh(token, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(name = "refresh_token") String token, HttpServletResponse response) {
        authService.logout(token, response);
        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(@AuthenticationPrincipal User user, HttpServletResponse response) {
        authService.logoutAll(user, response);
        return ResponseEntity.ok(Map.of("message", "Đã đăng xuất khỏi tất cả thiết bị"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req.email());
        return ResponseEntity.ok(Map.of("message", "Nếu email tồn tại, bạn sẽ nhận được hướng dẫn."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
        System.out.println("controller-reset");
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công"));
    }
}
