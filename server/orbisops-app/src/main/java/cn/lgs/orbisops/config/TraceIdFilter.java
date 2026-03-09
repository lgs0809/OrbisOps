package cn.lgs.orbisops.config;

import cn.lgs.orbisops.types.common.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String traceId = TraceContext.resolveTraceId(
                request.getHeader(TraceContext.HEADER_TRACE_ID),
                request.getHeader(TraceContext.LEGACY_HEADER_TRACE_ID));
        try {
            TraceContext.putTraceId(traceId);
            response.setHeader(TraceContext.HEADER_TRACE_ID, traceId);
            response.setHeader("Access-Control-Expose-Headers", TraceContext.HEADER_TRACE_ID);
            filterChain.doFilter(request, response);
        } finally {
            log.info("HTTP request completed method:{} uri:{} status:{} durationMs:{}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), System.currentTimeMillis() - start);
            TraceContext.clearTraceId();
        }
    }

}
