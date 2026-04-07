package com.fighteasy.service;

import com.fighteasy.entity.User;
import com.fighteasy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserAttemptService {

    private final UserRepository userRepository;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateFailedAttempts(User user) {
        int attempt = user.getFailedAttempts();
        user.setFailedAttempts(attempt + 1);
        if (attempt >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        userRepository.save(user);
    }
}