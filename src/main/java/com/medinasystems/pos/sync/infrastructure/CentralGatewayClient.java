package com.medinasystems.pos.sync.infrastructure;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.medinasystems.pos.shared.BusinessConflictException;
import com.medinasystems.pos.shared.BusinessNotFoundException;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.CentralGateway;
import com.medinasystems.pos.sync.CentralUnavailableException;
import com.medinasystems.pos.sync.domain.StoreIdentity;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Store side only: online calls to the central node, with the store token and short timeouts. The token is read from the
 * local identity and never logged or put in a URL. The central node's problem answers keep their code and status (see
 * {@link CentralGateway}).
 */
@Component
@Profile("store")
public class CentralGatewayClient implements CentralGateway {

    private static final String TOKEN_HEADER = "X-Store-Token";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;
    private static final int SERVER_ERROR = 500;

    private final RestClient restClient;
    private final StoreIdentityRepository identities;
    private final JsonMapper jsonMapper;

    public CentralGatewayClient(NodeProperties node, StoreIdentityRepository identities, JsonMapper jsonMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = RestClient.builder().baseUrl(node.centralUrl()).requestFactory(factory).build();
        this.identities = identities;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public <T> T get(String path, Class<T> type) {
        String token = storeToken();
        try {
            return restClient.get().uri(path).header(TOKEN_HEADER, token).retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new CentralUnavailableException();
        }
    }

    @Override
    public <T> T post(String path, Object body, Class<T> type) {
        String token = storeToken();
        try {
            RestClient.RequestBodySpec request = restClient.post().uri(path).header(TOKEN_HEADER, token);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            return request.retrieve().body(type);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (RestClientException ex) {
            throw new CentralUnavailableException();
        }
    }

    private String storeToken() {
        return identities.findFirstByOrderByIdAsc().map(StoreIdentity::getStoreToken)
                .orElseThrow(CentralUnavailableException::new);
    }

    /** Maps a refused call to the exception of the same code and status; the body is read as a problem document. */
    private RuntimeException translate(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == UNAUTHORIZED || status == FORBIDDEN || status >= SERVER_ERROR) {
            return new CentralUnavailableException();
        }
        JsonNode problem = problemOf(ex.getResponseBodyAsString());
        String code = textOf(problem, "code");
        String detail = textOf(problem, "detail");
        if (status == NOT_FOUND) {
            return new BusinessNotFoundException(code == null ? "NOT_FOUND" : code, detail);
        }
        if (status == CONFLICT) {
            return new BusinessConflictException(code == null ? "CONFLICT" : code, detail);
        }
        return new BusinessRuleException(code == null ? "CENTRAL_REJECTED" : code, detail);
    }

    /** The problem document, or null when the body is empty or is not JSON. */
    private JsonNode problemOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readTree(body);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String textOf(JsonNode problem, String name) {
        return problem == null ? null : problem.path(name).textValue();
    }
}
