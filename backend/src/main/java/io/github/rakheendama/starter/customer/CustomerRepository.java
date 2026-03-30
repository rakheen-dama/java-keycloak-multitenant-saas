package io.github.rakheendama.starter.customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  List<Customer> findByStatus(String status);

  Optional<Customer> findByEmail(String email);
}
