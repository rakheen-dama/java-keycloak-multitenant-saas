package io.github.rakheendama.starter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ResourceNotFoundException extends ErrorResponseException {

  public ResourceNotFoundException(String resourceType, Object id) {
    super(
        HttpStatus.NOT_FOUND,
        createProblem(
            resourceType + " not found",
            "No " + resourceType.toLowerCase() + " found with id " + id),
        null);
  }

  public static ResourceNotFoundException withDetail(String title, String detail) {
    return new ResourceNotFoundException(title, detail, true);
  }

  private ResourceNotFoundException(String title, String detail, boolean custom) {
    super(HttpStatus.NOT_FOUND, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
