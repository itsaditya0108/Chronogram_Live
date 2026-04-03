package live.chronogram.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate bean.
 * Used for making synchronous HTTP requests to external or other microservices.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a RestTemplate bean with default configuration.
     * @return A new RestTemplate instance.
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // Since we need the interceptor to read the response body without breaking the stream,
        // we use BufferingClientHttpRequestFactory.
        restTemplate.setRequestFactory(new org.springframework.http.client.BufferingClientHttpRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory()));
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        return restTemplate;
    }
}
