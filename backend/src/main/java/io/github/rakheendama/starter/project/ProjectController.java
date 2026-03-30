package io.github.rakheendama.starter.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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

  public ProjectController(ProjectService projectService) {
    this.projectService = projectService;
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
    return ResponseEntity.ok(projects.stream().map(ProjectResponse::from).toList());
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
    return ResponseEntity.ok(ProjectResponse.from(projectService.getProject(id)));
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> createProject(
      @Valid @RequestBody CreateProjectRequest request) {
    var project =
        projectService.createProject(
            request.title(), request.description(), request.customerId());
    return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
        .body(ProjectResponse.from(project));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest request) {
    return ResponseEntity.ok(
        ProjectResponse.from(
            projectService.updateProject(id, request.title(), request.description())));
  }

  @PatchMapping("/{id}/status")
  public ResponseEntity<ProjectResponse> changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
    return ResponseEntity.ok(
        ProjectResponse.from(projectService.changeProjectStatus(id, request.status())));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ProjectResponse> archiveProject(@PathVariable UUID id) {
    return ResponseEntity.ok(ProjectResponse.from(projectService.archiveProject(id)));
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
      UUID createdBy,
      Instant createdAt,
      Instant updatedAt) {
    static ProjectResponse from(Project p) {
      return new ProjectResponse(
          p.getId(),
          p.getTitle(),
          p.getDescription(),
          p.getStatus(),
          p.getCustomerId(),
          p.getCreatedBy(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }
}
