package com.platform.notifications.sms;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Raw SMS transport via Djosend (same gateway/account as the mon-point project), using
 * {@code POST /api/v1/messages} — the current, non-deprecated endpoint (the legacy
 * {@code /api/v1/sms/messages} + {@code senderName} string route is deprecated and ignores the
 * sender name). {@code senderNameId} (UUID of a sender name approved in the Djosend merchant
 * portal, see {@code GET /api/v1/sender-names}) is omitted from the request when blank, which
 * falls back to Djosend's own default sender ("Djosend").
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
        Map<String, String> request = new LinkedHashMap<>();
        request.put("channel", "sms");
        request.put("to", to);
        request.put("body", body);
        if (senderNameId != null && !senderNameId.isBlank()) {
            request.put("senderNameId", senderNameId);
        }
        try {
            restClient.post()
                    .header("X-API-Key", apiKey)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Échec d'envoi de SMS à {}: {}", to, e.getMessage());
        }
    }
}
