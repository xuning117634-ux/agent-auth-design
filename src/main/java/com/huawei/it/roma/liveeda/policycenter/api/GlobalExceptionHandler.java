package com.huawei.it.roma.liveeda.policycenter.api;

import com.huawei.it.roma.liveeda.policycenter.api.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        String traceId = traceId(request);
        if (exception.getCause() != null) {
            log.warn("API_EXCEPTION traceId={} path={} errorCode={} message={} context={}",
                    traceId,
                    request.getRequestURI(),
                    exception.code().name(),
                    exception.getMessage(),
                    exception.context(),
                    exception);
        } else {
            log.warn("API_EXCEPTION traceId={} path={} errorCode={} message={}",
                    traceId,
                    request.getRequestURI(),
                    exception.code().name(),
                    exception.getMessage());
        }
        return ResponseEntity.status(exception.code().status())
                .body(new ErrorResponse(exception.code().name(), exception.getMessage(), traceId));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        String message = invalidRequestMessage(exception);
        log.warn("INVALID_REQUEST traceId={} path={} errorCode={} message={}",
                traceId,
                request.getRequestURI(),
                ErrorCode.INVALID_REQUEST.name(),
                message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ErrorCode.INVALID_REQUEST.name(), message, traceId));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleInternalError(Exception exception, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("INTERNAL_ERROR traceId={} path={} errorCode={}",
                traceId,
                request.getRequestURI(),
                ErrorCode.INTERNAL_ERROR.name(),
                exception);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(ErrorCode.INTERNAL_ERROR.name(), "internal error", traceId));
    }

    private String invalidRequestMessage(Exception exception) {
        if (exception instanceof HttpMessageNotReadableException) {
            return "request body is invalid";
        }
        return "request is invalid";
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        if (traceId instanceof String value && !value.isBlank()) {
            return value;
        }

        String headerTraceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        if (headerTraceId == null || headerTraceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return headerTraceId;
    }
}
