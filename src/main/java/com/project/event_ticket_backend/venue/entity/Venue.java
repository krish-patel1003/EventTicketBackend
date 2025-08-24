package com.project.event_ticket_backend.venue.entity;

import com.project.event_ticket_backend.config.BaseEntity;
import com.project.event_ticket_backend.user.entity.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "venues")
@Getter
@Setter
@EqualsAndHashCode(callSuper = true, exclude = "seats")
public class Venue extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String location;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VenueSeat> seats = new ArrayList<>();
}
