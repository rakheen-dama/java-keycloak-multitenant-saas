package io.github.rakheendama.starter.customer;

import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

  private final CustomerRepository repository;

  public CustomerService(CustomerRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomers() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Customer> listByStatus(String status) {
    return repository.findByStatus(status);
  }

  @Transactional(readOnly = true)
  public Customer getCustomer(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
  }

  @Transactional
  public Customer createCustomer(String name, String email, String company) {
    RequestScopes.requireOwner();
    UUID createdBy = RequestScopes.requireMemberId();
    var customer = new Customer(name, email, company, createdBy);
    return repository.save(customer);
  }

  @Transactional
  public Customer updateCustomer(UUID id, String name, String email, String company) {
    RequestScopes.requireOwner();
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    customer.updateDetails(name, email, company);
    return repository.save(customer);
  }

  @Transactional
  public Customer archiveCustomer(UUID id) {
    RequestScopes.requireOwner();
    var customer =
        repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    customer.archive();
    return repository.save(customer);
  }
}
