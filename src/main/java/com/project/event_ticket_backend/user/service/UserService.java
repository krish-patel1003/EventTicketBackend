package com.project.event_ticket_backend.user.service;

import com.project.event_ticket_backend.user.dto.UserProfileResponseDto;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getUserByEmail(final String email){
        return userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.GONE, "The user account has been deleted or deactivated"));
    }
}
