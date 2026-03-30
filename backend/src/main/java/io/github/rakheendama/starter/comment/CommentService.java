package io.github.rakheendama.starter.comment;

import io.github.rakheendama.starter.exception.ForbiddenException;
import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.member.Member;
import io.github.rakheendama.starter.member.MemberRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

  private final CommentRepository commentRepository;
  private final ProjectRepository projectRepository;
  private final MemberRepository memberRepository;

  public CommentService(
      CommentRepository commentRepository,
      ProjectRepository projectRepository,
      MemberRepository memberRepository) {
    this.commentRepository = commentRepository;
    this.projectRepository = projectRepository;
    this.memberRepository = memberRepository;
  }

  @Transactional(readOnly = true)
  public List<Comment> listComments(UUID projectId) {
    projectRepository
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    return commentRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
  }

  @Transactional
  public Comment addMemberComment(UUID projectId, String content) {
    projectRepository
        .findById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    UUID memberId = RequestScopes.requireMemberId();
    String memberName =
        memberRepository.findById(memberId).map(Member::getDisplayName).orElse(null);
    var comment = new Comment(projectId, content, memberId, memberName);
    return commentRepository.save(comment);
  }

  @Transactional
  public void deleteMemberComment(UUID commentId) {
    var comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
    UUID callerId = RequestScopes.requireMemberId();
    if (!"MEMBER".equals(comment.getAuthorType()) || !callerId.equals(comment.getAuthorId())) {
      throw new ForbiddenException(
          "Cannot delete comment", "You can only delete your own comments");
    }
    commentRepository.delete(comment);
  }
}
