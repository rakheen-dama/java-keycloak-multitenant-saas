package io.github.rakheendama.starter.project;

import io.github.rakheendama.starter.customer.Customer;
import io.github.rakheendama.starter.customer.CustomerRepository;
import io.github.rakheendama.starter.member.Member;
import io.github.rakheendama.starter.member.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

  private final ProjectService projectService;
  private final CustomerRepository customerRepository;
  private final MemberRepository memberRepository;

  public ProjectController(
      ProjectService projectService,
      CustomerRepository customerRepository,
      MemberRepository memberRepository) {
    this.projectService = projectService;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
  }

  @GetMapping
  public ResponseEntity<List<ProjectResponse>> listProjects(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) String status) {
    List<Project> projects;
    if (customerId != null && status != null) {
      projects = projectService.listByCustomerIdAndStatus(customerId, status);
    } else if (customerId != null) {
      projects = projectService.listByCustomerId(customerId);
    } else if (status != null) {
      projects = projectService.listByStatus(status);
    } else {
      projects = projectService.listProjects();
    }
    return ResponseEntity.ok(toResponses(projects));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
    return ResponseEntity.ok(toResponses(List.of(projectService.getProject(id))).getFirst());
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    var project =
        projectService.createProject(request.title(), request.description(), request.customerId());
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(toResponses(List.of(project)).getFirst());
  }

  @PutMapping("/{id}")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
    var project = projectService.updateProject(id, request.title(), request.description());
    return ResponseEntity.ok(toResponses(List.of(project)).getFirst());
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ProjectResponse> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
    var project = projectService.changeProjectStatus(id, request.status());
    return ResponseEntity.ok(toResponses(List.of(project)).getFirst());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ProjectResponse> archiveProject(@PathVariable UUID id) {
    var project = projectService.archiveProject(id);
    return ResponseEntity.ok(toResponses(List.of(project)).getFirst());
  }

  private List<ProjectResponse> toResponses(List<Project> projects) {
    List<Customer> customers = resolveCustomers(projects);
    Map<UUID, String> customerNames =
        customers.stream().collect(Collectors.toMap(Customer::getId, Customer::getName));
    Map<UUID, String> customerEmails =
        customers.stream().collect(Collectors.toMap(Customer::getId, Customer::getEmail));
    Map<UUID, String> memberNames = resolveMemberNames(projects);
    return projects.stream()
        .map(p -> ProjectResponse.from(p, customerNames, customerEmails, memberNames))
        .toList();
  }

  private List<Customer> resolveCustomers(List<Project> projects) {
    var customerIds =
        projects.stream()
            .map(Project::getCustomerId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (customerIds.isEmpty()) return List.of();
    return customerRepository.findAllById(customerIds);
  }

  private Map<UUID, String> resolveMemberNames(List<Project> projects) {
    var memberIds =
        projects.stream()
            .map(Project::getCreatedBy)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    if (memberIds.isEmpty()) return Map.of();
    return memberRepository.findAllById(memberIds).stream()
        .collect(Collectors.toMap(Member::getId, Member::getDisplayName));
  }

  record CreateProjectRequest(
      @NotBlank(message = "title is required") @Size(max = 255) String title,
      @Size(max = 2000) String description,
      @NotNull(message = "customerId is required") UUID customerId) {}

  record UpdateProjectRequest(
      @NotBlank(message = "title is required") @Size(max = 255) String title,
      @Size(max = 2000) String description) {}

  record ChangeStatusRequest(@NotBlank String status) {}

  record ProjectResponse(
      UUID id,
      String title,
      String description,
      String status,
      UUID customerId,
      String customerName,
      String customerEmail,
      UUID createdBy,
      String createdByName,
      Instant createdAt,
      Instant updatedAt) {
    static ProjectResponse from(
        Project p,
        Map<UUID, String> customerNames,
        Map<UUID, String> customerEmails,
        Map<UUID, String> memberNames) {
      return new ProjectResponse(
          p.getId(),
          p.getTitle(),
          p.getDescription(),
          p.getStatus(),
          p.getCustomerId(),
          customerNames.getOrDefault(p.getCustomerId(), "Unknown"),
          customerEmails.getOrDefault(p.getCustomerId(), ""),
          p.getCreatedBy(),
          memberNames.getOrDefault(p.getCreatedBy(), "Unknown"),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }
}
