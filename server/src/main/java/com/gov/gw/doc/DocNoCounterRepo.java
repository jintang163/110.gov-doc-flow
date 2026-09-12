package com.gov.gw.doc;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocNoCounterRepo extends JpaRepository<DocNoCounter, Long> {
    Optional<DocNoCounter> findByTemplateIdAndYearNo(Long templateId, Integer yearNo);
}
