package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.model.UserRole;
import edu.ece568.MiniAmazon.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first admin account from configuration. Nothing is hardcoded:
 * without {@code app.admin.password} no admin is created at all.
 */
@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String username;

    @Value("${app.admin.password:}")
    private String password;

    @Value("${app.admin.email:admin@example.com}")
    private String email;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.findUserByRole(UserRole.ADMIN).isEmpty()) {
            return;
        }
        if (!StringUtils.hasText(password)) {
            log.warn("No admin exists and app.admin.password is not set: skipping admin creation");
            return;
        }
        User adminUser = new User();
        adminUser.setUsername(username);
        adminUser.setPassword(passwordEncoder.encode(password));
        adminUser.setEmail(email);
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setRole(UserRole.ADMIN);
        adminUser.setAccountEnabled(true);
        userRepository.save(adminUser);
        log.info("Created admin user '{}'", username);
    }
}
