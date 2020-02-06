package wcyoung.spring.mvc.filter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class RequestAndResponseLoggingFilter extends OncePerRequestFilter {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<MediaType> VISIBLE_TYPES = Arrays.asList(
            MediaType.valueOf("text/*"),
            MediaType.APPLICATION_FORM_URLENCODED,
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            MediaType.valueOf("application/*+json"),
            MediaType.valueOf("application/*+xml"),
            MediaType.MULTIPART_FORM_DATA
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isAsyncDispatch(request) || !log.isDebugEnabled()) {
            filterChain.doFilter(request, response);
        } else {
            doFilterWrapped(wrapRequest(request), wrapResponse(response), filterChain);
        }
    }

    protected void doFilterWrapped(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, FilterChain filterChain) throws IOException, ServletException {
        StopWatch stopWatch = new StopWatch();

        try {
            stopWatch.start();

            beforeRequest(request, response);
            filterChain.doFilter(request, response);
        } finally {
            afterRequest(request, response);
            response.copyBodyToResponse();

            stopWatch.stop();
            long executionTime = stopWatch.getTime();
            log.debug("{} <==> execution time=({} ms)", request.getRemoteAddr(), String.format("%,d", executionTime));
        }
    }

    protected void beforeRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        logRequestHeader(request, request.getRemoteAddr());
    }

    protected void afterRequest(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        String remoteAddr = request.getRemoteAddr();
        logRequestBody(request, remoteAddr);
        logResponseHeader(response, remoteAddr);
        logResponseBody(response, remoteAddr);
    }

    private void logRequestHeader(ContentCachingRequestWrapper request, String remoteAddr) {
        String queryString = request.getQueryString();
        if (queryString == null) {
            log.debug("{} ==> [{}] {}", remoteAddr, request.getMethod(), request.getRequestURI());
        } else {
            log.debug("{} ==> [{}] {}?{}", remoteAddr, request.getMethod(), request.getRequestURI(), queryString);
        }

        Collections.list(request.getHeaderNames()).forEach(headerName -> {
            Collections.list(request.getHeaders(headerName)).forEach(headerValue -> {
                log.debug("{} ==> {}: {}", remoteAddr, headerName, headerValue);
            });
        });
    }

    private void logRequestBody(ContentCachingRequestWrapper request, String remoteAddr) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            logContent(content, request.getContentType(), request.getCharacterEncoding(), remoteAddr + " ==>");
        }
    }

    private void logResponseHeader(ContentCachingResponseWrapper response, String remoteAddr) {
        int status = response.getStatusCode();
        log.debug("{} <== [{} {}]", remoteAddr, status, HttpStatus.valueOf(status).getReasonPhrase());

        response.getHeaderNames().forEach(headerName -> {
            response.getHeaders(headerName).forEach(headerValue -> {
                log.debug("{} <== {}: {}", headerName, headerValue);
            });
        });
    }

    private void logResponseBody(ContentCachingResponseWrapper response, String remoteAddr) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            logContent(content, response.getContentType(), response.getCharacterEncoding(), remoteAddr + " <==");
        }
    }

    private void logContent(byte[] content, String contentType, String contentEncoding, String logPrefix) {
        MediaType mediaType = MediaType.valueOf(contentType);
        boolean visible = VISIBLE_TYPES.stream().anyMatch(visibleType -> visibleType.includes(mediaType));
        if (visible) {
            try {
                String contentString = new String(content, contentEncoding);
                log.debug("{} {}", logPrefix, contentString);
            } catch (UnsupportedEncodingException e) {
                log.debug("{} [{}]", logPrefix, content.length);
            }
        } else {
            log.debug("{} [{}]", logPrefix, content.length);
        }
    }

    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper) {
            return (ContentCachingRequestWrapper) request;
        } else {
            return new ContentCachingRequestWrapper(request);
        }
    }

    private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper) {
            return (ContentCachingResponseWrapper) response;
        } else {
            return new ContentCachingResponseWrapper(response);
        }
    }

}
