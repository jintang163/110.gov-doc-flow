package com.gov.gw.seal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SealRepo extends JpaRepository<Seal, Long> {
    List<Seal> findByEnabledTrue();
}

