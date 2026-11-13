package com.basarsy.skyline.crew.repository;

import com.basarsy.skyline.crew.entity.CrewMember;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CrewMemberRepository extends JpaRepository<CrewMember, UUID>, JpaSpecificationExecutor<CrewMember> {

    boolean existsByEmployeeNumberIgnoreCase(String employeeNumber);

    boolean existsByUserId(UUID userId);
}
