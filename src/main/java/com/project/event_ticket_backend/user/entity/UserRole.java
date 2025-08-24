package com.project.event_ticket_backend.user.entity;

//import jakarta.persistence.*;
//import lombok.*;
//
//import java.time.Instant;
//
//@Entity
//@Table(name = "user_roles")
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@EqualsAndHashCode(onlyExplicitlyIncluded = true)
//public class UserRole {
//
//    @EmbeddedId
//    private UserRoleId id = new UserRoleId();
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @MapsId("userId")
//    private User user;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @MapsId("roleId")
//    private Role role;
//
//    @Column(name = "assigned_at", nullable = false, updatable = false)
//    private Instant assignedAt = Instant.now();
//}
