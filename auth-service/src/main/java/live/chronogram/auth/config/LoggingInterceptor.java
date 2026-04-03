package live.chronogram.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        long startTime = System.currentTimeMillis();
        logger.info("Outbound Request: [{}] URI: {} | Body: {}", request.getMethod(), request.getURI(), new String(body, StandardCharsets.UTF_8));
        
        ClientHttpResponse response = execution.execute(request, body);
        
        long endTime = System.currentTimeMillis();
        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        
        logger.info("Inbound Response: [{}] URI: {} | Status: {} | Time: {}ms | Body: {}", 
                request.getMethod(), request.getURI(), response.getStatusCode(), (endTime - startTime), responseBody);
                
        return response;
    }
}
