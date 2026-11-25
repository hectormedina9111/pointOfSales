package com.medinasystems.pos.sync.web;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.medinasystems.pos.sync.StoreCaller;
import com.medinasystems.pos.sync.domain.StoreNode;
import com.medinasystems.pos.sync.domain.StoreTokens;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;

/**
 * Authenticates store calls on /api/v1/sync/** with the {@code X-Store-Token} header. The token is hashed and
 * looked up among the active stores; the matching store node id goes into the request attribute
 * {@link #STORE_NODE_ATTRIBUTE}. Activation and status are not filtered: see {@link #shouldNotFilter}.
 * Not a Spring bean: it is registered with its URL pattern in {@link SyncWebConfig}.
 */
final class StoreTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String STORE_NODE_ATTRIBUTE = StoreCaller.NODE_ATTRIBUTE;
    static final String TOKEN_HEADER = "X-Store-Token";
    private static final String SYNC_PREFIX = "/api/v1/sync/";
    private static final String ACTIVATE = "/api/v1/sync/activate";
    private static final String STATUS = "/api/v1/sync/status";

    private final StoreNodeRepository nodes;
    private final StoreProblemWriter problems;

    StoreTokenAuthenticationFilter(StoreNodeRepository nodes, StoreProblemWriter problems) {
        this.nodes = nodes;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith(SYNC_PREFIX) || ACTIVATE.equals(path) || STATUS.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<StoreNode> store = findStore(request.getHeader(TOKEN_HEADER));
        if (store.isEmpty()) {
            problems.write(request, response, HttpStatus.UNAUTHORIZED, "STORE_UNAUTHORIZED",
                    "title.UNAUTHENTICATED", "error.STORE_UNAUTHORIZED");
            return;
        }
        request.setAttribute(STORE_NODE_ATTRIBUTE, store.get().getId());
        chain.doFilter(request, response);
    }

    private Optional<StoreNode> findStore(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return nodes.findByCredentialHash(StoreTokens.sha256Hex(token)).filter(StoreNode::canAuthenticate);
    }
}
