package com.gov.gw.flow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowRepo extends JpaRepository<FlowConfig, Long> {
}
