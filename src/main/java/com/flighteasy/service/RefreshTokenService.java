package com.flighteasy.service;

import com.flighteasy.entity.RefreshToken;
import com.flighteasy.entity.User;
import com.flighteasy.exception.custom.InvalidTokenException;
import com.flighteasy.exception.custom.TokenExpiredException;
import com.flighteasy.exception.custom.TokenReuseException;
import com.flighteasy.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private static final int MAX_TOKENS_PER_USER = 5;

    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceInfo, String ip) {
        long count = refreshTokenRepository.countByUserAndIsUsedFalse(user);
        if (count >= MAX_TOKENS_PER_USER) {
            refreshTokenRepository.deleteOldestByUser(user);
        }

        RefreshToken token = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .deviceInfo(deviceInfo)
                .ipAddress(ip)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        return refreshTokenRepository.save(token);
    }

    @Transactional
    public RefreshToken rotateToken(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));

        if (existing.isUsed()) {
            refreshTokenRepository.revokeAllByUser(existing.getUser());
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
