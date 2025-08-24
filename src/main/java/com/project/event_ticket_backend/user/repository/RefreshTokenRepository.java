package com.project.event_ticket_backend.user.repository;

import com.project.event_ticket_backend.user.entity.RefreshToken;
import com.project.event_ticket_backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByIdAndExpiresAtAfter(UUID id, Instant date);
    List<RefreshToken> findByUser(User user);
    Optional<RefreshToken> findByIdAndUser(UUID id, User user);
    void deleteByUser(User user);
}
