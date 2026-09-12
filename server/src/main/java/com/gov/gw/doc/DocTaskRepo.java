package com.gov.gw.doc;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DocTaskRepo extends JpaRepository<DocTask, Long> {
    List<DocTask> findByDocIdOrderById(Long docId);
    List<DocTask> findByDocIdAndAttemptAndStatus(Long docId, Integer attempt, String status);
    List<DocTask> findByAssigneeIdAndStatusOrderByIdDesc(Long assigneeId, String status);
    List<DocTask> findByAssigneeIdAndStatusInOrderByIdDesc(Long assigneeId, List<String> statuses);
    List<DocTask> findByStatus(String status);
    Optional<DocTask> findByCamundaTaskId(String camundaTaskId);
    boolean existsByDocIdAndAssigneeId(Long docId, Long assigneeId);
}
