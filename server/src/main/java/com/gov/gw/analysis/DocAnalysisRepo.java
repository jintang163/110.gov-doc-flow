package com.gov.gw.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocAnalysisRepo extends JpaRepository<DocAnalysis, Long> {
    Optional<DocAnalysis> findByDocId(Long docId);
}
