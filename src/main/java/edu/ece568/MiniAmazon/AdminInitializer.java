package edu.ece568.MiniAmazon;

import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.model.UserRole;
import edu.ece568.MiniAmazon.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Check if admin exists
        if (userRepository.findUserByRole((UserRole.ADMIN)).isEmpty()) {
            // Create default admin if none exists
            User adminUser = new User();
            adminUser.setUsername("admin");
            adminUser.setPassword(passwordEncoder.encode("admin")); // Use secure password in production
            adminUser.setEmail("lqngg66@gmail.com");
            adminUser.setFirstName("Admin");
            adminUser.setLastName("User");
            adminUser.setRole(UserRole.ADMIN);
            adminUser.setAccountEnabled(true);
            userRepository.save(adminUser);
            log.info("Created admin user");
        }
    }
}