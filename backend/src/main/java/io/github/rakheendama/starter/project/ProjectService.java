package io.github.rakheendama.starter.project;

import io.github.rakheendama.starter.customer.CustomerRepository;
import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

  private final ProjectRepository repository;
  private final CustomerRepository customerRepository;

  public ProjectService(ProjectRepository repository, CustomerRepository customerRepository) {
    this.repository = repository;
    this.customerRepository = customerRepository;
  }

  @Transactional(readOnly = true)
  public List<Project> listProjects() {
    return repository.findAll();
  }

  @Transactional(readOnly = true)
  public List<Project> listByCustomerId(UUID customerId) {
    return repository.findByCustomerId(customerId);
  }

  @Transactional(readOnly = true)
  public List<Project> listByStatus(String status) {
    return repository.findByStatus(status);
  }

  @Transactional(readOnly = true)
  public List<Project> listByCustomerIdAndStatus(UUID customerId, String status) {
    return repository.findByCustomerIdAndStatus(customerId, status);
  }

  @Transactional(readOnly = true)
  public Project getProject(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Project", id));
  }

  @Transactional
  public Project createProject(String title, String description, UUID customerId) {
    RequestScopes.requireOwner();
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    UUID createdBy = RequestScopes.requireMemberId();
    var project = new Project(title, description, customerId, createdBy);
    return repository.save(project);
  }

  @Transactional
  public Project updateProject(UUID id, String title, String description) {
    RequestScopes.requireOwner();
    var project =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    project.updateDetails(title, description);
    return repository.save(project);
  }

  @Transactional
  public Project changeProjectStatus(UUID id, String newStatus) {
    RequestScopes.requireOwner();
    var project =
        repository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    project.changeStatus(newStatus);
    return repository.save(project);
  }

  @Transactional
  public Project archiveProject(UUID id) {
    return changeProjectStatus(id, "ARCHIVED");
  }
}
