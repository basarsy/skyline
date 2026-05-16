package com.basarsy.skyline.crew.repository;

import com.basarsy.skyline.crew.entity.CrewMember;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrewMemberRepository extends JpaRepository<CrewMember, UUID> {
}
