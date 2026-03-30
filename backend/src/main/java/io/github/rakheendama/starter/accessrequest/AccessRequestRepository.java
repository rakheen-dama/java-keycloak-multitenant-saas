package io.github.rakheendama.starter.accessrequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

  Optional<AccessRequest> findByEmailAndStatus(String email, String status);

  boolean existsByEmailAndStatusIn(String email, List<String> statuses);

  List<AccessRequest> findAllByOrderByCreatedAtDesc();

  List<AccessRequest> findByStatus(String status);
}
