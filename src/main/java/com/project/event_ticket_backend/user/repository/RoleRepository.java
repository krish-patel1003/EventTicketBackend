package com.project.event_ticket_backend.user.repository;

import com.project.event_ticket_backend.user.entity.Role;
import com.project.event_ticket_backend.user.entity.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(RoleType name);
    @Query("SELECT r FROM Role r WHERE r.name IN :names")
    List<Role> findByNameIn(@Param("names") Set<RoleType> names);
    boolean existsByName(RoleType name);
}
