package com.finemp.connector.transport;

import static org.assertj.core.api.Assertions.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class ConnectorPayloadLimitFilterTest {
    private final ConnectorPayloadLimitFilter filter = new ConnectorPayloadLimitFilter(1024, 128, 1);
    private final String completion = "/api/connector/v1/work/" + UUID.randomUUID() + "/complete";
    @Test void shouldAcceptTheExactBudgetAndProvideOnlyTheBoundedRepeatableBytesToTheParser() throws Exception {
        byte[] body = new byte[1024]; Arrays.fill(body, (byte)'x'); var request = request(completion, body);
        var accepted = new AtomicBoolean();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var wrapped = (HttpServletRequest) req;
            assertThat(wrapped.getContentLengthLong()).isEqualTo(1024);
            assertThat(wrapped.getInputStream().readAllBytes()).containsExactly(body);
            assertThat(wrapped.getInputStream().readAllBytes()).containsExactly(body); accepted.set(true);
        });
        assertThat(accepted).isTrue();
    }
    @Test void shouldRejectDeclaredOversizeBeforeReadingAndAlsoMeasureActualBytesWithoutLength() throws Exception {
        var touched = new AtomicBoolean();
        var declared = new MockHttpServletRequest("POST", completion) {
            @Override public long getContentLengthLong() { return 1025; }
            @Override public ServletInputStream getInputStream() { touched.set(true); return super.getInputStream(); }
        };
        var response = new MockHttpServletResponse(); filter.doFilter(declared, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(413); assertThat(touched).isFalse();
        var unknown = new MockHttpServletRequest("POST", completion) { @Override public long getContentLengthLong() { return -1; } };
        unknown.setContent(new byte[1025]); response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(unknown, response, chain);
        assertThat(response.getStatus()).isEqualTo(413); assertThat(chain.getRequest()).isNull();
    }
    @Test void shouldApplyTheSmallBudgetOutsideTheCompletionRouteAndRejectCompression() throws Exception {
        var request = request("/api/connector/v1/work/claim", new byte[129]); var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(413);
        request = request(completion, new byte[1]); request.addHeader("Content-Encoding", "gzip"); response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain()); assertThat(response.getStatus()).isEqualTo(415);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        request = request(completion, new byte[1]); request.addHeader("Content-Encoding", "identity"); request.addHeader("Content-Encoding", "gzip");
        response = new MockHttpServletResponse(); filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(415);
    }
    @Test void shouldRejectWithoutQueueingWhenBusyAndReleaseCapacityAfterDownstreamFailure() throws Exception {
        var rejected = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request(completion, new byte[0]), new MockHttpServletResponse(), (req, res) -> {
            filter.doFilter(request(completion, new byte[0]), rejected, new MockFilterChain());
            throw new ServletException("Synthetic downstream failure");
        })).isInstanceOf(ServletException.class);
        assertThat(rejected.getStatus()).isEqualTo(429); assertThat(rejected.getHeader("Retry-After")).isEqualTo("1");
        var chain = new MockFilterChain(); filter.doFilter(request(completion, new byte[0]), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
    @Test void shouldSanitizeReadFailureAndReleaseItsPermit() throws Exception {
        var request = new MockHttpServletRequest("POST", completion) {
            @Override public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    @Override public int read() throws IOException { throw new IOException("synthetic private connection detail"); }
                    @Override public boolean isFinished() { return false; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { }
                };
            }
        };
        var response = new MockHttpServletResponse(); filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(400); assertThat(response.getContentAsString()).doesNotContain("private", "connection");
        var chain = new MockFilterChain(); filter.doFilter(request(completion, new byte[0]), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
    private MockHttpServletRequest request(String route, byte[] body) { var request = new MockHttpServletRequest("POST", route); request.setContent(body); return request; }
}
