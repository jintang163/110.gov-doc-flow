package com.gov.gw.doc;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocTraceRepo extends JpaRepository<DocTrace, Long> {
    List<DocTrace> findByDocIdOrderById(Long docId);
}
