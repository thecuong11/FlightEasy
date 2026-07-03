package com.flighteasy.service;

import com.flighteasy.entity.RefreshToken;
import com.flighteasy.entity.User;
import com.flighteasy.exception.custom.InvalidTokenException;
import com.flighteasy.exception.custom.TokenExpiredException;
import com.flighteasy.exception.custom.TokenReuseException;
import com.flighteasy.repository.RefreshTokenRepository;
import com.flighteasy.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtService jwtService;
    private static final int MAX_TOKENS_PER_USER = 5;

    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceInfo, String ip) {
        long count = refreshTokenRepository.countByUserAndIsUsedFalse(user);
        if (count >= MAX_TOKENS_PER_USER) {
            refreshTokenRepository.deleteOldestByUser(user);
        }

        String rawToken = TokenGenerator.generateToken();
        String hash = TokenGenerator.hashToken(rawToken);

        RefreshToken token = RefreshToken.builder()
                .tokenHash(hash)
                .user(user)
                .deviceInfo(deviceInfo)
                .ipAddress(ip)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(token);
        token.setRawTokenForResponse(rawToken);

        return token;
    }

    @Transactional
    public RefreshToken rotateToken(String rawToken, String accessToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));

        if (existing.isUsed()) {
            refreshTokenRepository.revokeAllByUser(existing.getUser());

            if (accessToken != null && !accessToken.isBlank()) {
                long remainingMillis = jwtService.getRemainingMillis(accessToken);
                tokenBlacklistService.blacklist(accessToken, remainingMillis);
            }
            throw new TokenReuseException("Phát hiện token bị tái sử dụng. Tất cả phiên bản bị đăng xuất.");
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenExpiredException("Refresh token đã hết hạn");
        }

        existing.setUsed(true);
        refreshTokenRepository.save(existing);

        return createRefreshToken(
                existing.getUser(),
                existing.getDeviceInfo(),
                existing.getIpAddress()
        );
    }
}
