package com.gov.gw.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReminderLogRepo extends JpaRepository<ReminderLog, Long> {
    List<ReminderLog> findByStatusOrderByIdDesc(String status);
    List<ReminderLog> findAllByOrderByIdDesc();
    boolean existsByDocIdAndStatus(Long docId, String status);
}
