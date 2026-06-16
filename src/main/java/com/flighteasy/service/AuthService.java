package com.flighteasy.service;

import com.flighteasy.dto.AuthResponse;
import com.flighteasy.dto.LoginRequest;
import com.flighteasy.dto.RegisterRequest;
import com.flighteasy.dto.ResetPasswordRequest;
import com.flighteasy.entity.PasswordResetToken;
import com.flighteasy.entity.RefreshToken;
import com.flighteasy.entity.User;
import com.flighteasy.exception.custom.*;
import com.flighteasy.repository.PasswordResetTokenRepository;
import com.flighteasy.repository.RefreshTokenRepository;
import com.flighteasy.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserAttemptService userAttemptService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletResponse response) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException("Email " + req.email() + " đã được đăng ký");
        }

        User user = User.builder()
                .fullName(req.fullName())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .build();
        userRepository.save(user);

        return buildAuthResponse(user, null, null, response);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không đúng"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountLockedException("Tài khoản bị khóa đến " + user.getLockedUntil());
        }

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            userAttemptService.updateFailedAttempts(user);
            throw new InvalidCredentialsException("Email hoặc mật khẩu không đúng");
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        String deviceInfo = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        return buildAuthResponse(user, deviceInfo, ip, response);
    }

    @Transactional
    public AuthResponse refresh(String rawToken, String accessToken , HttpServletResponse response) {
        RefreshToken newRefreshToken = refreshTokenService.rotateToken(rawToken, accessToken);
        String newAccessToken = jwtService.generateAccessToken(newRefreshToken.getUser());
        setRefreshTokenCookie(response, newRefreshToken.getToken());

        return new AuthResponse(accessToken, "Bearer", null);
    }

    @Transactional
    public void logout(String rawRefreshToken, String accessToken, HttpServletResponse response) {

        if (accessToken != null && !accessToken.isBlank()) {
            try {
                long reminingMillis = jwtService.getRemainingMillis(accessToken);
                tokenBlacklistService.blacklist(accessToken, reminingMillis);
            } catch (Exception ex) {
                log.warn("Could not blacklist token: {}", ex.getMessage());
            }
        }

        refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(t -> {
            t.setUsed(true);
            refreshTokenRepository.save(t);
        });
        clearRefreshTokenCookie(response);
    }

    @Transactional
    public void logoutAll(User user, HttpServletResponse response) {
        refreshTokenRepository.revokeAllByUser(user);
        clearRefreshTokenCookie(response);
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(UUID.randomUUID().toString())
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .isUsed(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);
            //emailService.sendResetEmail(user.getEmail(), resetToken.getToken());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(req.token())
                .orElseThrow(() -> new InvalidTokenException("Token không hợp lệ"));

        if (resetToken.isUsed()) {
            throw new InvalidTokenException("Token đã được sử dụng");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Token đã hết hạn");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.revokeAllByUser(user);
    }

    private AuthResponse buildAuthResponse(User user, String deviceInfo, String ip, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo, ip);
        setRefreshTokenCookie(response, refreshToken.getToken());

        return new AuthResponse(accessToken, "Bearer", new AuthResponse.UserInfo(user.getId(), user.getEmail(), user.getFullName(), user.getRole()));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }


}
