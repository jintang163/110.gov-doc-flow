package com.gov.gw.org;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrgRepo extends JpaRepository<OrgEntity, Long> {
    List<OrgEntity> findByParentIdOrderBySort(Long parentId);
}
