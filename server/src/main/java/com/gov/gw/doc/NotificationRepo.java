package com.gov.gw.doc;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByIdDesc(Long userId);
    long countByUserIdAndReadFlagFalse(Long userId);
}
