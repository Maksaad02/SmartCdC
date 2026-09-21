package com.recouvtech.recouvback.controller;

import com.recouvtech.recouvback.configuration.JwtUtils;
import com.recouvtech.recouvback.dao.RoleRepository;
import com.recouvtech.recouvback.dao.UtilisateurRepository;
import com.recouvtech.recouvback.dto.UtilisateurDTO.RegisterRequestDTO;
import com.recouvtech.recouvback.entity.Role;
import com.recouvtech.recouvback.entity.Utilisateur;
import com.recouvtech.recouvback.entity.enums.RoleAgent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurRepository utilisateurRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtils jwtUtils;

    /**
     * Creation de compte, reservee aux ADMIN.
     *
     * On construit l'entite a partir d'un DTO restreint : lier @RequestBody
     * directement sur Utilisateur laissait le client fournir idAgentRecouv
     * (save() faisait alors un merge et ecrasait le compte cible) ainsi que role.
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO dto) {
        if (utilisateurRepo.existsByEmail(dto.getEmail())) {
            return ResponseEntity.badRequest().body("Email déjà utilisé");
        }

        Role role = roleRepo.findByNom(RoleAgent.AGENT)
                .orElseThrow(() -> new IllegalStateException("Rôle AGENT introuvable"));

        Utilisateur u = new Utilisateur();
        u.setNom(dto.getNom());
        u.setEmail(dto.getEmail());
        u.setMotDePasse(passwordEncoder.encode(dto.getMotDePasse()));
        u.setRole(role);

        utilisateurRepo.save(u);
        return ResponseEntity.status(HttpStatus.CREATED).body("Utilisateur créé");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException e) {
            // Message volontairement generique : ne pas distinguer "compte inconnu"
            // de "mot de passe invalide" (enumeration de comptes).
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Identifiants incorrects");
        }

        Utilisateur user = utilisateurRepo.findByEmail(req.username())
                .orElseThrow(() -> new BadCredentialsException("Identifiants incorrects"));

        String token = jwtUtils.generateToken(req.username());

        return ResponseEntity.ok(new JwtResponse(
                token,
                user.getIdAgentRecouv(),
                user.getNom(),
                user.getEmail(),
                user.getRole().getNom().name().toLowerCase()));
    }

    record LoginRequest(String username, String password) {}
    record JwtResponse(String token, Long id, String name, String email, String role) {}
}
