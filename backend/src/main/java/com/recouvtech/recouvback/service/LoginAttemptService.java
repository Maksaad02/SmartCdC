package com.recouvtech.recouvback.service;

import com.recouvtech.recouvback.dao.UtilisateurRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Verrouille un compte apres des echecs de connexion consecutifs. Complete le
 * limiteur par minute (LoginRateLimiter) : celui-ci freine le debit, ce service
 * arrete une attaque lente qui resterait sous la limite.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 10;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UtilisateurRepository utilisateurRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void recordFailure(String email) {
        // Alerte utile : un pic d'echecs de connexion signale une attaque par force brute.
        meterRegistry.counter("smartcdc.login.failures").increment();
        utilisateurRepository.findByEmail(email).ifPresent(u -> {
            int echecs = u.getFailedAttempts() + 1;
            if (echecs >= MAX_FAILURES) {
                u.setLockedUntil(LocalDateTime.now().plus(LOCK_DURATION));
                meterRegistry.counter("smartcdc.login.accounts.locked").increment();
                echecs = 0;
            }
            u.setFailedAttempts(echecs);
            utilisateurRepository.save(u);
        });
    }

    @Transactional
    public void recordSuccess(String email) {
        utilisateurRepository.findByEmail(email).ifPresent(u -> {
            if (u.getFailedAttempts() != 0 || u.getLockedUntil() != null) {
                u.setFailedAttempts(0);
                u.setLockedUntil(null);
                utilisateurRepository.save(u);
            }
        });
    }
}
