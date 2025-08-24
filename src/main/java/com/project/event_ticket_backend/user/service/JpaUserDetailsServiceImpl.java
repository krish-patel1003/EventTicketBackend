package com.project.event_ticket_backend.user.service;

import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.exception.EmailVerificationException;
import com.project.event_ticket_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JpaUserDetailsServiceImpl implements UserDetailsService {

    @Value("${email-verification.required}")
    private boolean emailVerificationRequired;

    private final UserRepository userRepository;

    @Override
    public JpaUserDetailsImpl loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailWithRoles(email)
                .map(this::checkEmailVerification)
                .map(JpaUserDetailsImpl::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

    }

    private User checkEmailVerification(User user){
        if(emailVerificationRequired && !user.isEmailVerified()){
            throw new EmailVerificationException("Your email is not verified");
        }
        return user;
    }
}
