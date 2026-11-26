package edu.ece568.MiniAmazon.repository;

import edu.ece568.MiniAmazon.model.User;
import edu.ece568.MiniAmazon.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsUserByUsername(String username);
    boolean existsUserByEmail(String email);
    Optional<User> findUserByUsername(String username);

    Collection<Object> findUserByRole(UserRole role);
}
