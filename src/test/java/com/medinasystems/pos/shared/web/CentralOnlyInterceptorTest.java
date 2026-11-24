package com.medinasystems.pos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.NodeRole;
import com.medinasystems.pos.shared.ReadOnlyNodeException;

/**
 * The store profile cannot log in (it has no bootstrapped admin), so the rule is tested on the
 * interceptor itself with plain handler methods.
 */
class CentralOnlyInterceptorTest {

    private static final NodeProperties STORE = new NodeProperties(NodeRole.STORE, 1, "http://central:8080");
    private static final NodeProperties CENTRAL = new NodeProperties(NodeRole.CENTRAL, 0, null);

    @Test
    void storeRejectsMethodAnnotatedAsCentralOnly() throws Exception {
        CentralOnlyInterceptor interceptor = new CentralOnlyInterceptor(STORE);
        HandlerMethod handler = handler(Sample.class, "write");

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(), handler))
                .isInstanceOf(ReadOnlyNodeException.class);
    }

    @Test
    void storeRejectsClassAnnotatedAsCentralOnly() throws Exception {
        CentralOnlyInterceptor interceptor = new CentralOnlyInterceptor(STORE);
        HandlerMethod handler = handler(CentralSample.class, "write");

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(), handler))
                .isInstanceOf(ReadOnlyNodeException.class);
    }

    @Test
    void storeAllowsUnannotatedMethods() throws Exception {
        CentralOnlyInterceptor interceptor = new CentralOnlyInterceptor(STORE);

        assertThat(interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(Sample.class, "read"))).isTrue();
    }

    @Test
    void centralAcceptsCentralOnlyMethods() throws Exception {
        CentralOnlyInterceptor interceptor = new CentralOnlyInterceptor(CENTRAL);

        assertThat(interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(Sample.class, "write"))).isTrue();
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/v1/countries");
    }

    private static HandlerMethod handler(Class<?> type, String method) throws Exception {
        Object bean = type.getDeclaredConstructor().newInstance();
        return new HandlerMethod(bean, type.getMethod(method));
    }

    static class Sample {

        @CentralOnly
        public void write() {
        }

        public void read() {
        }
    }

    @CentralOnly
    static class CentralSample {

        public void write() {
        }
    }
}
