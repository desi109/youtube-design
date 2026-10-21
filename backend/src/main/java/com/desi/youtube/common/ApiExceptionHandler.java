package com.desi.youtube.common;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException ex) {
    return error(ex.status, ex.code, ex.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
  })
  ResponseEntity<?> badRequest(Exception ex) {
    return error(400, "INVALID_REQUEST", "Please check the request fields and try again.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception ex) {
    org.slf4j.LoggerFactory.getLogger(getClass()).error(
      "Request failed: {}",
      ex.getClass().getSimpleName()
    );
    return error(500, "SERVER_ERROR", "The request could not be completed. Please try again.");
  }

  private ResponseEntity<?> error(int status, String code, String message) {
    return ResponseEntity.status(status).body(
      Map.of("code", code, "message", message, "requestId", UUID.randomUUID().toString())
    );
  }
}
