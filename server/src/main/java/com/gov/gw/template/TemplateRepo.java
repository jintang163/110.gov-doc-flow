package com.gov.gw.template;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TemplateRepo extends JpaRepository<DocTemplate, Long> {
    List<DocTemplate> findByEnabledTrue();
}
