package io.github.rakheendama.starter.exception;

import io.github.rakheendama.starter.portal.PortalAuthException;
import io.github.rakheendama.starter.portal.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    log.warn("Not found: path={}, detail={}", request.getRequestURI(), ex.getBody().getDetail());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getBody());
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(
      ForbiddenException ex, HttpServletRequest request) {
    log.warn("Forbidden: path={}, detail={}", request.getRequestURI(), ex.getBody().getDetail());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getBody());
  }

  @ExceptionHandler(InvalidStateException.class)
  public ResponseEntity<ProblemDetail> handleInvalidState(
      InvalidStateException ex, HttpServletRequest request) {
    log.warn(
        "Invalid state: path={}, detail={}", request.getRequestURI(), ex.getBody().getDetail());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getBody());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied: path={}, method={}", request.getRequestURI(), request.getMethod());
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle("Access denied");
    problem.setDetail("Insufficient permissions for this operation");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
  }

  @ExceptionHandler(PortalAuthException.class)
  public ResponseEntity<ProblemDetail> handlePortalAuth(
      PortalAuthException ex, HttpServletRequest request) {
    log.warn("Portal auth failed: path={}, detail={}", request.getRequestURI(), ex.getMessage());
    var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problem.setTitle("Portal authentication failed");
    problem.setDetail(ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyRequests(
      TooManyRequestsException ex, HttpServletRequest request) {
    log.warn("Rate limit exceeded: path={}, detail={}", request.getRequestURI(), ex.getMessage());
    var problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
    problem.setTitle("Too many requests");
    problem.setDetail(ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
  }
}
