package com.project.event_ticket_backend.util;

import com.project.event_ticket_backend.user.entity.Role;
import com.project.event_ticket_backend.user.entity.RoleType;
import com.project.event_ticket_backend.user.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class RoleInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (RoleType roleType : RoleType.values()) {
            boolean exists = roleRepository.existsByName(roleType);
            if (!exists) {
                Role role = new Role();
                role.setName(roleType);
                roleRepository.save(role);
            }
        }
    }
}

