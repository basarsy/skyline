package com.basarsy.skyline.user.service;

import com.basarsy.skyline.common.exception.SkylineException;
import com.basarsy.skyline.user.dto.CreateUserRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.user.entity.User;
import com.basarsy.skyline.user.entity.UserRole;
import com.basarsy.skyline.user.repository.UserRepository;
import com.basarsy.skyline.user.repository.UserSpecifications;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        // A valid token for an account that is gone or disabled is no longer a session.
        return userRepository.findById(userId)
                .filter(User::isEnabled)
                .map(UserServiceImpl::toResponse)
                .orElseThrow(() -> new SkylineException("Authentication required", HttpStatus.UNAUTHORIZED));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String email, UserRole role, Pageable pageable) {
        return userRepository.findAll(UserSpecifications.matching(email, role), pageable)
                .map(UserServiceImpl::toResponse);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() != UserRole.STAFF && request.role() != UserRole.ADMIN) {
            throw new SkylineException("Role must be STAFF or ADMIN", HttpStatus.BAD_REQUEST);
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new SkylineException("Email is already registered", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);
        return toResponse(userRepository.save(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
