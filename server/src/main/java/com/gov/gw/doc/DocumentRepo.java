package com.gov.gw.doc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepo extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {
    java.util.List<Document> findByCreatedByOrderByUpdatedAtDesc(Long createdBy);
}
