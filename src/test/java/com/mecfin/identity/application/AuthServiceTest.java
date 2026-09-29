package com.mecfin.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.mecfin.identity.domain.SecurityEventType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SecurityEventService securityEvents;

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private AuthService service() {
        return new AuthService(userRepository, passwordEncoder, eventPublisher, new PasswordPolicy(), securityEvents,
                Clock.fixed(NOW, ZoneOffset.UTC), 3, Duration.ofMinutes(15));
    }

    @Test
    void registerHashesPasswordAndSavesLowercasedEmail() {
        AuthService authService = service();
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
        AuthService authService = service();
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
        AuthService authService = service();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("s3cret1234")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> authService.register("user@example.com", "s3cret1234"))
                .isInstanceOf(DuplicateEmailException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void registerRejectsCommonPasswordBeforeTouchingTheDatabase() {
        assertThatThrownBy(() -> service().register("user@example.com", "senha12345"))
                .isInstanceOf(WeakPasswordException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void thirdWrongPasswordLocksTheAccountAndLockedAccountSkipsThePasswordCheck() {
        User user = new User("user@example.com", "hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass-1", "hash")).thenReturn(false);
        ClientInfo client = new ClientInfo("127.0.0.1", "test");
        AuthService authService = service();

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> authService.verifyPassword("user@example.com", "wrong-pass-1", client))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThat(user.isLocked(NOW)).isTrue();
        verify(securityEvents).record(any(), eq(SecurityEventType.ACCOUNT_LOCKED), eq(client));
        // bloqueada: nem a senha certa passa, e a senha nem é conferida
        assertThatThrownBy(() -> authService.verifyPassword("user@example.com", "correct-pass", client))
                .isInstanceOf(AccountLockedException.class);
        verify(passwordEncoder, never()).matches(eq("correct-pass"), any());
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(16)))).isFalse();
    }

    @Test
    void unknownEmailStillPaysThePasswordHashCost() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verifyPassword("ghost@example.com", "whatever-123", new ClientInfo(null, null)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder).matches(eq("whatever-123"), any());
    }
}
