package io.github.rakheendama.starter.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
  List<Project> findByCustomerId(UUID customerId);

  List<Project> findByStatus(String status);

  List<Project> findByCustomerIdAndStatus(UUID customerId, String status);
}
