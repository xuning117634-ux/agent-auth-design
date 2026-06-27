package com.huawei.it.roma.liveeda.policycenter.api.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorCode;
import com.huawei.it.roma.liveeda.policycenter.api.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Order(2)
@Slf4j
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private static final String PRECHECK_PATH = "/internal/tool-authorization-prechecks";
    private static final String BATCH_AUTHORIZATION_PATH = "/internal/conversation-authorizations/batch";

    private final ObjectMapper objectMapper;
    private final long precheckMaxBytes;
    private final long batchAuthorizationMaxBytes;

    public RequestBodySizeLimitFilter(
            ObjectMapper objectMapper,
            @Value("${policy-center.request-limit.tool-precheck-max-bytes:262144}") long precheckMaxBytes,
            @Value("${policy-center.request-limit.batch-authorization-max-bytes:262144}") long batchAuthorizationMaxBytes) {
        this.objectMapper = objectMapper;
        this.precheckMaxBytes = precheckMaxBytes;
        this.batchAuthorizationMaxBytes = batchAuthorizationMaxBytes;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (shouldReject(request)) {
            reject(request, response);
            return;
        }
        if (shouldLimit(request)) {
            byte[] body = readBodyWithinLimit(request, response);
            if (body == null) {
                return;
            }
            filterChain.doFilter(new CachedBodyRequest(request, body), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldReject(HttpServletRequest request) {
        return limitFor(request) != null
                && request.getContentLengthLong() > limitFor(request);
    }

    private boolean shouldLimit(HttpServletRequest request) {
        return limitFor(request) != null;
    }

    private Long limitFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        return switch (request.getRequestURI()) {
            case PRECHECK_PATH -> precheckMaxBytes;
            case BATCH_AUTHORIZATION_PATH -> batchAuthorizationMaxBytes;
            default -> null;
        };
    }

    private byte[] readBodyWithinLimit(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long maxBytes = limitFor(request);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = request.getInputStream().read(buffer)) != -1) {
            if (body.size() + read > maxBytes) {
                reject(request, response, maxBytes);
                return null;
            }
            body.write(buffer, 0, read);
        }
        return body.toByteArray();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        reject(request, response, limitFor(request));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long maxBytes) throws IOException {
        String traceId = traceId(request);
        log.warn("PAYLOAD_TOO_LARGE traceId={} path={} contentLength={} maxBytes={}",
                traceId,
                request.getRequestURI(),
                request.getContentLengthLong(),
                maxBytes);
        response.setStatus(ErrorCode.PAYLOAD_TOO_LARGE.status().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                ErrorCode.PAYLOAD_TOO_LARGE.name(),
                "request body is too large",
                traceId));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        if (traceId instanceof String value && !value.isBlank()) {
            return value;
        }
        String headerTraceId = request.getHeader(TraceIdFilter.TRACE_ID_HEADER);
        if (headerTraceId != null && !headerTraceId.isBlank()) {
            return headerTraceId;
        }
        return UUID.randomUUID().toString();
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("async read is not supported");
        }
    }
}
