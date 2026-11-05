package com.platform.notifications.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Raw SMS transport via Djosend (same gateway/account as the mon-point project), using
 * {@code POST /api/v1/messages} with {@code senderNameId} — the current, non-deprecated way to
 * pick a sender (the legacy {@code /api/v1/sms/messages} + {@code senderName} string route
 * ignores the sender name and falls back to Djosend's own default). {@code senderNameId} is the
 * UUID of a sender name already created and approved in the Djosend merchant portal (see
 * {@code GET /api/v1/sender-names}) — "MONPOINTCI" here, shared with the mon-point project.
 *
 * Phone numbers are already stored in E.164 (see RegisterRequest), so no normalization is needed.
 * A send failure is logged, never rethrown — SMS delivery must not block the auth flow that
 * triggered it.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String senderNameId;

    public SmsService(@Value("${sms.base-url:https://api.djosend.com/api/v1/messages}") String baseUrl,
                       @Value("${sms.api-key:}") String apiKey,
                       @Value("${sms.sender-name-id:}") String senderNameId) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.senderNameId = senderNameId;
    }

    public void send(String to, String body) {
        try {
            restClient.post()
                    .header("X-API-Key", apiKey)
                    .body(new SmsRequest("sms", to, body, senderNameId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Échec d'envoi de SMS à {}: {}", to, e.getMessage());
        }
    }

    private record SmsRequest(String channel, String to, String body, String senderNameId) {
    }
}
