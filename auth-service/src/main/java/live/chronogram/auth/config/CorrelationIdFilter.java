package live.chronogram.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that sets a unique Correlation ID (txId) for every request.
 * This ID is stored in the SLF4J MDC (Mapped Diagnostic Context) to ensure
 * it appears in all logs generated during the request lifecycle.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_TX_ID_KEY = "txId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extract Correlation ID from incoming request or generate a new one
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        try {
            // 2. Put the ID into MDC (Logback pattern: txId=%X{txId})
            MDC.put(MDC_TX_ID_KEY, correlationId);

            // 3. Add the ID to the response header for client traceability
            response.addHeader(CORRELATION_ID_HEADER, correlationId);

            // 4. Continue the filter chain
            filterChain.doFilter(request, response);
        } finally {
            // 5. CRITICAL: Clear MDC at the end of the request to prevent memory leaks/pollution
            MDC.remove(MDC_TX_ID_KEY);
        }
    }
}
