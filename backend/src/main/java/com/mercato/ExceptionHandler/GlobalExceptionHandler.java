package com.mercato.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Generic exception handlers
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGeneric(Exception ex) {
        log.error("Unhandled request failure", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse("Something went wrong. Please try again later.", true));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse("You do not have permission to perform this action.", true));
    }

    @ExceptionHandler({BindException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse> handleInvalidParameter(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiResponse("Invalid request parameters. Check the values and try again.", true));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse("Invalid username or password", true));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse(ex.getMessage(), true));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidation(MethodArgumentNotValidException ex) {
        String fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));

        String message = fieldErrors.isEmpty()
                ? ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "))
                : fieldErrors;

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse(message, true));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        String message = ex.getCause() instanceof com.fasterxml.jackson.core.JsonParseException
                ? "Malformed JSON request body"
                : "Missing or unreadable request body";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse(message, true));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String message = String.format("'%s' is not supported for '%s'", ex.getMethod(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiResponse(message, true));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse> handleNoHandlerFound(NoHandlerFoundException ex, HttpServletRequest request) {
        String message = String.format("No endpoint found: '%s %s'", ex.getHttpMethod(), request.getRequestURI());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse(message, true));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse("Data integrity violation", true));
        }

        String message;

        if (cause.contains("violates not-null constraint")) {
            message = extractBetween(cause, "null value in column \"", "\" of relation")
                    .map(col -> "'" + toReadable(col) + "' is required")
                    .orElse("A required field is missing");

        } else if (cause.contains("violates unique constraint") || cause.contains("duplicate key")) {
            message = extractBetween(cause, "Key (", ")=")
                    .map(col -> "'" + toReadable(col) + "' already exists")
                    .orElse("A duplicate value was provided");

        } else if (cause.contains("violates check constraint")) {
            message = extractBetween(cause, "violates check constraint \"", "\"")
                    .map(col -> "Value violates constraint: " + toReadable(col))
                    .orElse("A value constraint was violated");

        } else if (cause.contains("violates foreign key constraint")) {
            message = extractBetween(cause, "Key (", ")=")
                    .map(col -> "Referenced '" + toReadable(col) + "' does not exist")
                    .orElse("A referenced record does not exist");

        } else {
            message = "Data integrity violation";
        }
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ApiResponse(message, true));
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiResponse> handleInvalidFileException(InvalidFileException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse(ex.getMessage(), true));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiResponse> handleFileUploadException(FileUploadException ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse(ex.getMessage(), true));
    }

    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<InsufficientInventoryResponse> handleInsufficientInventoryException(InsufficientInventoryException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new InsufficientInventoryResponse(
                                ex.getMessage(),
                                true,
                                ex.getProductId(),
                                ex.getAvailableQty()
                        )
                );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse> handleBusinessException(BusinessException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(new ApiResponse(ex.getMessage(), true));
    }

    private Optional<String> extractBetween(String source, String from, String to) {
        int start = source.indexOf(from);
        if (start == -1) return Optional.empty();
        start += from.length();
        int end = source.indexOf(to, start);
        if (end == -1) return Optional.empty();
        return Optional.of(source.substring(start, end).trim());
    }

    private String toReadable(String dbName) {
        return dbName.replace("_fk", "").replace("_", " ");
    }
}

