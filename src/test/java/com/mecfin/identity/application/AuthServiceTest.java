package com.mecfin.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mecfin.identity.domain.User;
import com.mecfin.identity.domain.UserRegisteredEvent;
import com.mecfin.identity.infra.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void registerHashesPasswordAndSavesLowercasedEmail() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, eventPublisher);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("s3cret1234")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = authService.register("User@Example.com", "s3cret1234");

        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        verify(userRepository).saveAndFlush(any(User.class));
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void registerWithExistingEmailThrowsAndNeverSaves() {
        AuthService authService = new AuthService(userRepository, passwordEncoder, eventPublisher);
        when(userRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(new User("user@example.com", "hash")));

        assertThatThrownBy(() -> authService.register("user@example.com", "s3cret1234"))
                .isInstanceOf(DuplicateEmailException.class);

        verify(userRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registerWithRaceConditionOnUniqueConstraintThrowsDuplicateEmail() {
        // Simulates two concurrent registrations for the same email: both pass the
        // findByEmail() check (neither sees the other's uncommitted row), so the DB's
        // unique constraint is what actually catches the duplicate, on flush.
        AuthService authService = new AuthService(userRepository, passwordEncoder, eventPublisher);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("s3cret1234")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> authService.register("user@example.com", "s3cret1234"))
                .isInstanceOf(DuplicateEmailException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
