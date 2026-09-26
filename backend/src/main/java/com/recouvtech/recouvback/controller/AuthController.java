package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.configuration.JwtUtils;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.security.LoginRateLimiter;
import com.recouvtech.recouvback.service.LoginAttemptService;
import com.recouvtech.recouvback.service.RefreshTokenService;
import com.recouvtech.recouvback.service.RefreshTokenService.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.LockedException;
import com.recouvtech.recouvback.entity.Utilisateur;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurRepository utilisateurRepo;
    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;
    private final LoginRateLimiter rateLimiter;
    private final LoginAttemptService loginAttempts;
    private final MeterRegistry meterRegistry;
    private final RefreshTokenService refreshTokens;

    /** Cookie Secure : true en production (HTTPS). Seul un deploiement de test en HTTP pur le desactive. */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    static final String REFRESH_COOKIE = "smartcdc_refresh";
    /** Le cookie n'est envoye qu'aux endpoints d'authentification : pas aux appels metier. */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        // Avant toute verification du mot de passe : une tentative refusee par le
        // limiteur ne doit ni tester le mot de passe ni incrementer les echecs.
        if (rateLimiter.isBlocked(request.getRemoteAddr(), req.username())) {
            meterRegistry.counter("smartcdc.login.throttled").increment();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds()))
                    .body("Trop de tentatives. Réessayez dans un instant.");
        }
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (LockedException e) {
            // Verrouille apres trop d'echecs. Meme statut que le limiteur, sans dire
            // si le compte existe.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(rateLimiter.retryAfterSeconds()))
                    .body("Trop de tentatives. Réessayez dans un instant.");
        } catch (AuthenticationException e) {
            rateLimiter.recordFailure(request.getRemoteAddr(), req.username());
            loginAttempts.recordFailure(req.username());
            // Message volontairement generique : ne pas distinguer "compte inconnu"
            // de "mot de passe invalide" (enumeration de comptes).
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants incorrects");
        }

        Utilisateur user = utilisateurRepo.findByEmail(req.username())
                .orElseThrow(() -> new BadCredentialsException("Identifiants incorrects"));

        loginAttempts.recordSuccess(req.username());
        rateLimiter.recordSuccess(req.username());
        RefreshTokenService.Issued refresh = refreshTokens.issue(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(refresh.token(), refresh.ttl()).toString())
                .body(jwtResponse(user));
    }

    /**
     * Renouvelle la session : echange le cookie de renouvellement contre un nouveau jeton d'acces (et
     * un nouveau cookie : le precedent est revoque).
     *
     * Protection CSRF : ce endpoint est authentifie par un cookie, donc appelable par un autre site
     * a l'insu de l'utilisateur. SameSite=Strict l'empeche deja ; on exige de plus un en-tete
     * personnalise, qu'un formulaire ou une image d'un autre site ne peut pas ajouter.
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        if (requestedWith == null || requestedWith.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("En-tête X-Requested-With requis");
        }
        try {
            RefreshTokenService.Rotation rotation = refreshTokens.rotate(cookie);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, refreshCookie(rotation.refresh().token(), rotation.refresh().ttl()).toString())
                    .body(jwtResponse(rotation.user()));
        } catch (InvalidRefreshTokenException e) {
            // Cookie perime ou revoque : on l'efface pour que le navigateur cesse de l'envoyer.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                    .body("Session expirée");
        }
    }

    /** Deconnexion : revoque la session cote serveur et efface le cookie. Idempotente. */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookie,
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith) {
        if (requestedWith == null || requestedWith.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        refreshTokens.revoke(cookie);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie().toString())
                .build();
    }

    private JwtResponse jwtResponse(Utilisateur user) {
        return new JwtResponse(
                jwtUtils.generateToken(user.getEmail()),
                jwtUtils.accessTtlSeconds(),
                user.getIdAgentRecouv(),
                user.getNom(),
                user.getEmail(),
                user.getRole().getNom().name().toLowerCase());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)           // illisible depuis JavaScript : un XSS ne peut pas le voler
                .secure(cookieSecure)
                .sameSite("Strict")       // jamais envoye lors d'une navigation depuis un autre site
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearedCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Strict")
                .path(REFRESH_COOKIE_PATH).maxAge(0).build();
    }

    record LoginRequest(String username, String password) {}
    record JwtResponse(String token, long expiresIn, Long id, String name, String email, String role) {}
}
