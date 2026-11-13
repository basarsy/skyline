package com.basarsy.skyline.user.service;

import com.basarsy.skyline.user.dto.CreateUserRequest;
import com.basarsy.skyline.user.dto.UserResponse;
import com.basarsy.skyline.user.entity.UserRole;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** User lookup and account administration. */
public interface UserService {

    /** The account behind an authenticated request. */
    UserResponse getCurrentUser(UUID userId);

    /** Paged search; {@code email} is a case-insensitive "contains" match, both filters optional. */
    Page<UserResponse> searchUsers(String email, UserRole role, Pageable pageable);

    /** Creates a STAFF or ADMIN account. */
    UserResponse createUser(CreateUserRequest request);
}
