package com.gov.gw.seal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SealRecordRepo extends JpaRepository<SealRecord, Long> {
    List<SealRecord> findByDocIdOrderById(Long docId);
}
