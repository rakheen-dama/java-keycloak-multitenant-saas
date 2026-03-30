package io.github.rakheendama.starter.comment;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @GetMapping("/api/projects/{projectId}/comments")
  public ResponseEntity<List<CommentResponse>> listComments(@PathVariable UUID projectId) {
    var comments = commentService.listComments(projectId);
    return ResponseEntity.ok(comments.stream().map(CommentResponse::from).toList());
  }

  @PostMapping("/api/projects/{projectId}/comments")
  public ResponseEntity<CommentResponse> addComment(
      @PathVariable UUID projectId, @Valid @RequestBody AddCommentRequest request) {
    var comment = commentService.addMemberComment(projectId, request.content());
    return ResponseEntity.created(
            URI.create("/api/projects/" + projectId + "/comments/" + comment.getId()))
        .body(CommentResponse.from(comment));
  }

  @DeleteMapping("/api/comments/{id}")
  public ResponseEntity<Void> deleteComment(@PathVariable UUID id) {
    commentService.deleteMemberComment(id);
    return ResponseEntity.noContent().build();
  }

  record AddCommentRequest(
      @NotBlank(message = "content is required") @Size(max = 10000) String content) {}

  record CommentResponse(
      UUID id,
      UUID projectId,
      String content,
      String authorType,
      UUID authorId,
      String authorName,
      Instant createdAt,
      Instant updatedAt) {
    static CommentResponse from(Comment c) {
      return new CommentResponse(
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
