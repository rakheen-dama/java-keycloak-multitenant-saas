package io.github.rakheendama.starter.comment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  List<Comment> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

  List<Comment> findByAuthorTypeAndAuthorId(String authorType, UUID authorId);
}
