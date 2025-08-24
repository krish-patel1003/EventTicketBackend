//package com.project.event_ticket_backend.user.entity;
//
//import jakarta.persistence.*;
//import lombok.*;
//
//import java.io.Serializable;
//import java.util.Objects;
//import java.util.UUID;
//
//@Embeddable
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@EqualsAndHashCode
//public class UserRoleId implements Serializable {
//
//    @Column(name = "user_id", nullable = false)
//    private UUID userId;
//
//    @Column(name = "role_id", nullable = false)
//    private UUID roleId;
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (!(o instanceof UserRoleId that)) return false;
//        return Objects.equals(userId, that.userId) &&
//                Objects.equals(roleId, that.roleId);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hash(userId, roleId);
//    }
//}
//
