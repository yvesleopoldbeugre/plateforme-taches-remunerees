package com.platform.notifications.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Raw SMS transport via Djosend (same gateway/account as the mon-point project). Phone numbers
 * are already stored in E.164 (see RegisterRequest), so no normalization is needed here. A send
 * failure is logged, never rethrown — SMS delivery must not block the auth flow that triggered it.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String senderId;

    public SmsService(@Value("${sms.base-url:https://api.djosend.com/api/v1/sms/messages}") String baseUrl,
                       @Value("${sms.api-key:}") String apiKey,
                       @Value("${sms.sender-id:}") String senderId) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.senderId = senderId;
    }

    public void send(String to, String body) {
        try {
            restClient.post()
                    .header("X-API-Key", apiKey)
                    .body(new SmsRequest(to, body, senderId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Échec d'envoi de SMS à {}: {}", to, e.getMessage());
        }
    }

    private record SmsRequest(String to, String body, String senderName) {
    }
}
