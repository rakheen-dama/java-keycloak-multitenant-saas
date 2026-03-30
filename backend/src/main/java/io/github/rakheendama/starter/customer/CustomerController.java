package io.github.rakheendama.starter.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  public ResponseEntity<List<CustomerResponse>> listCustomers(
      @RequestParam(required = false) String status) {
    var customers =
        status != null ? customerService.listByStatus(status) : customerService.listCustomers();
    return ResponseEntity.ok(customers.stream().map(CustomerResponse::from).toList());
  }

  @GetMapping("/{id}")
  public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id) {
    return ResponseEntity.ok(CustomerResponse.from(customerService.getCustomer(id)));
  }

  @PostMapping
  public ResponseEntity<CustomerResponse> createCustomer(
      @Valid @RequestBody CreateCustomerRequest request) {
    var customer =
        customerService.createCustomer(request.name(), request.email(), request.company());
    return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
        .body(CustomerResponse.from(customer));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CustomerResponse> updateCustomer(
      @PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
    var customer =
        customerService.updateCustomer(id, request.name(), request.email(), request.company());
    return ResponseEntity.ok(CustomerResponse.from(customer));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<CustomerResponse> archiveCustomer(@PathVariable UUID id) {
    return ResponseEntity.ok(CustomerResponse.from(customerService.archiveCustomer(id)));
  }

  record CreateCustomerRequest(
      @NotBlank(message = "name is required") @Size(max = 255) String name,
      @NotBlank(message = "email is required") @Email @Size(max = 255) String email,
      @Size(max = 255) String company) {}

  record UpdateCustomerRequest(
      @NotBlank(message = "name is required") @Size(max = 255) String name,
      @NotBlank(message = "email is required") @Email @Size(max = 255) String email,
      @Size(max = 255) String company) {}

  record CustomerResponse(
      UUID id,
      String name,
      String email,
      String company,
      String status,
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt) {
    static CustomerResponse from(Customer c) {
      return new CustomerResponse(
          c.getId(),
          c.getName(),
          c.getEmail(),
          c.getCompany(),
          c.getStatus(),
          c.getCreatedBy(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }
}
