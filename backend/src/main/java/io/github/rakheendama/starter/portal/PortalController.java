package io.github.rakheendama.starter.portal;

import io.github.rakheendama.starter.comment.Comment;
import io.github.rakheendama.starter.comment.CommentService;
import io.github.rakheendama.starter.customer.Customer;
import io.github.rakheendama.starter.customer.CustomerRepository;
import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.project.Project;
import io.github.rakheendama.starter.project.ProjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class PortalController {

  private final ProjectRepository projectRepository;
  private final CommentService commentService;
  private final CustomerRepository customerRepository;

  public PortalController(
      ProjectRepository projectRepository,
      CommentService commentService,
      CustomerRepository customerRepository) {
    this.projectRepository = projectRepository;
    this.commentService = commentService;
    this.customerRepository = customerRepository;
  }

  @GetMapping("/projects")
  public ResponseEntity<List<PortalProjectResponse>> listProjects() {
    UUID customerId = RequestScopes.CUSTOMER_ID.get();
    var projects = projectRepository.findByCustomerId(customerId);
    return ResponseEntity.ok(projects.stream().map(PortalProjectResponse::from).toList());
  }

  @GetMapping("/projects/{id}")
  public ResponseEntity<PortalProjectResponse> getProject(@PathVariable UUID id) {
    UUID customerId = RequestScopes.CUSTOMER_ID.get();
    var project = findOwnedProject(id, customerId);
    return ResponseEntity.ok(PortalProjectResponse.from(project));
  }

  @GetMapping("/projects/{id}/comments")
  public ResponseEntity<List<PortalCommentResponse>> listComments(@PathVariable UUID id) {
    UUID customerId = RequestScopes.CUSTOMER_ID.get();
    findOwnedProject(id, customerId);
    var comments = commentService.listComments(id);
    return ResponseEntity.ok(comments.stream().map(PortalCommentResponse::from).toList());
  }

  @PostMapping("/projects/{id}/comments")
  public ResponseEntity<PortalCommentResponse> addComment(
      @PathVariable UUID id, @Valid @RequestBody AddCommentRequest request) {
    UUID customerId = RequestScopes.CUSTOMER_ID.get();
    findOwnedProject(id, customerId);
    String customerName =
        customerRepository.findById(customerId).map(Customer::getName).orElse(null);
    var comment =
        commentService.addCustomerComment(id, request.content(), customerId, customerName);
    return ResponseEntity.created(URI.create("/api/portal/projects/" + id + "/comments"))
        .body(PortalCommentResponse.from(comment));
  }

  /**
   * Finds a project by ID and verifies it belongs to the given customer. Returns 404 (not 403) if
   * the project exists but belongs to a different customer — no information leak.
   */
  private Project findOwnedProject(UUID projectId, UUID customerId) {
    var project =
        projectRepository
            .findById(projectId)
            .filter(p -> customerId.equals(p.getCustomerId()))
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    return project;
  }

  record AddCommentRequest(
      @NotBlank(message = "content is required") @Size(max = 10000) String content) {}

  record PortalProjectResponse(
      UUID id,
      String title,
      String description,
      String status,
      UUID customerId,
      Instant createdAt,
      Instant updatedAt) {
    static PortalProjectResponse from(Project p) {
      return new PortalProjectResponse(
          p.getId(),
          p.getTitle(),
          p.getDescription(),
          p.getStatus(),
          p.getCustomerId(),
          p.getCreatedAt(),
          p.getUpdatedAt());
    }
  }

  record PortalCommentResponse(
      UUID id,
      UUID projectId,
      String content,
      String authorType,
      UUID authorId,
      String authorName,
      Instant createdAt,
      Instant updatedAt) {
    static PortalCommentResponse from(Comment c) {
      return new PortalCommentResponse(
          c.getId(),
          c.getProjectId(),
          c.getContent(),
          c.getAuthorType(),
          c.getAuthorId(),
          c.getAuthorName(),
          c.getCreatedAt(),
          c.getUpdatedAt());
    }
  }
}
