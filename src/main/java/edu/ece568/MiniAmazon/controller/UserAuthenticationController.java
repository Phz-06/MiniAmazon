package edu.ece568.MiniAmazon.controller;

import edu.ece568.MiniAmazon.dto.request.LoginRequest;
import edu.ece568.MiniAmazon.dto.request.SignupRequest;
import edu.ece568.MiniAmazon.dto.response.JwtResponse;
import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.model.UserRole;
import edu.ece568.MiniAmazon.repository.UserRepository;
import edu.ece568.MiniAmazon.security.JwtTokenUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class UserAuthenticationController {

    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtils jwtTokenUtils;
    private final PasswordEncoder passwordEncoder;

    public UserAuthenticationController(UserRepository userRepository, AuthenticationManager authenticationManager, JwtTokenUtils jwtTokenUtils, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.jwtTokenUtils = jwtTokenUtils;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid  @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtTokenUtils.generateToken(request.getUsername());
        log.info("User {} logged in successfully", request.getUsername());
        return ResponseEntity.ok(new JwtResponse(jwt));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        log.info("Received signup request for user: {}", request.getUsername());
        if (userRepository.existsUserByUsername(request.getUsername())) {
            return ResponseEntity.badRequest().body("Error: Username already exists");
        } else if (userRepository.existsUserByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email already exists");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setRole(UserRole.USER);
        user.setAccountEnabled(true);
        userRepository.save(user);
        log.info("User {} signed up successfully", request.getUsername());
        return ResponseEntity.ok(new JwtResponse(jwtTokenUtils.generateToken(request.getUsername())));
    }
}
