package com.basarsy.skyline.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.user.dto.CreateUserRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceImplTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserServiceImpl service = new UserServiceImpl(repository, encoder);

    @Test
    void createsAStaffAccountWithANormalisedEmailAndHashedPassword() {
        when(encoder.encode("long-enough-password")).thenReturn("hash");
        when(repository.save(any())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserResponse created = service.createUser(
                new CreateUserRequest("  Ops.Agent@Skyline.Test ", "long-enough-password", UserRole.STAFF));

        assertThat(created.email()).isEqualTo("ops.agent@skyline.test");
        assertThat(created.role()).isEqualTo(UserRole.STAFF);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(u -> "hash".equals(u.getPasswordHash()) && u.isEnabled()));
    }

    @Test
    void passengerAccountsCannotBeCreatedHere() {
        assertThatThrownBy(() -> service.createUser(
                        new CreateUserRequest("pax@skyline.test", "long-enough-password", UserRole.PASSENGER)))
                .isInstanceOf(SkylineException.class)
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).save(any());
    }

    @Test
    void duplicateEmailIsA409() {
        when(repository.existsByEmail("ops@skyline.test")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(
                        new CreateUserRequest("OPS@skyline.test", "long-enough-password", UserRole.ADMIN)))
                .isInstanceOf(SkylineException.class)
                .hasMessage("Email is already registered")
                .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void currentUserIsLoadedFromTheDatabase() {
        User user = user(true);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.getCurrentUser(user.getId()))
                .isEqualTo(new UserResponse(user.getId(), "me@skyline.test", UserRole.PASSENGER));
    }

    @Test
    void aDisabledOrDeletedAccountIsNoLongerASession() {
        User disabled = user(false);
        when(repository.findById(disabled.getId())).thenReturn(Optional.of(disabled));
        UUID gone = UUID.randomUUID();
        when(repository.findById(gone)).thenReturn(Optional.empty());

        for (UUID id : new UUID[] {disabled.getId(), gone}) {
            assertThatThrownBy(() -> service.getCurrentUser(id))
                    .isInstanceOf(SkylineException.class)
                    .extracting(e -> ((SkylineException) e).getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private static User user(boolean enabled) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("me@skyline.test");
        user.setRole(UserRole.PASSENGER);
        user.setEnabled(enabled);
        return user;
    }
}
