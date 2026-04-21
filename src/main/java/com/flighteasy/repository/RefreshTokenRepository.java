package com.flighteasy.repository;

import com.flighteasy.entity.RefreshToken;
import com.flighteasy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    long countByUserAndIsUsedFalse(User user);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.isUsed = true WHERE r.user = :user")
    void revokeAllByUser(User user);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken r WHERE r.id = (
            SELECT MIN(r2.id) FROM RefreshToken r2
            WHERE r2.user = :user AND r2.isUsed = false
            )
""")
    void deleteOldestByUser(User user);
}
