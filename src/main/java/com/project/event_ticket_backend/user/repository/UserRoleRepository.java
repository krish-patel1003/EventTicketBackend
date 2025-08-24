//package com.project.event_ticket_backend.user.repository;
//
//import com.project.event_ticket_backend.user.entity.UserRole;
//import com.project.event_ticket_backend.user.entity.UserRoleId;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.stereotype.Repository;
//
//import java.util.List;
//import java.util.UUID;
//
//@Repository
//public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
//
//    List<UserRole> findByUserId(UUID userId);
//
//    List<UserRole> findByRoleId(UUID roleId);
//
//    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
//
//    void deleteByUserId(UUID userId);
//}
