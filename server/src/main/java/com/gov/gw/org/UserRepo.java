package com.gov.gw.org;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    List<UserEntity> findByOrgId(Long orgId);
    List<UserEntity> findByEnabledTrue();
}
