package com.finemp.finance.entry;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class FinancialEntryPayloadLimitFilterTest {
    private final FinancialEntryPayloadLimitFilter filter=new FinancialEntryPayloadLimitFilter(1024,1);
    @Test void declaredChunkedAndMisleadingLengthsMustAllRespectTheActualByteBudget()throws Exception {
        for(long declared:new long[]{2048,-1,1}) {
            var raw=request();raw.setContent(new byte[1025]);
            var wrapped=new HttpServletRequestWrapper(raw) {@Override public long getContentLengthLong() {return declared;}};
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(wrapped,response,chain);
            assertThat(response.getStatus()).isEqualTo(413);assertThat(chain.getRequest()).isNull();
        }
    }
    @Test void abbreviatedOrInvalidPathIdentifiersMustNotBypassTheBodyBudget()throws Exception {
        // Binding aliases must receive the same resource gate as canonical UUID paths.
        for(var route:new String[]{"/api/companies/1-1-1-1-1/financial-entries",
                "/api/companies/00000000-0000-0000-0000-000000000001/financial-entries/1-1-1-1-1",
                "/api/companies/00000000-0000-0000-0000-000000000001/financial-entries/invalid"}) {
            var raw=request();raw.setRequestURI(route);if(route.contains("/financial-entries/"))raw.setMethod("PUT");raw.setContent(new byte[1025]);
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(raw,response,chain);
            assertThat(response.getStatus()).isEqualTo(413);assertThat(chain.getRequest()).isNull();
        }
    }
    @Test void boundedBodyMustBeReadableAndCapacityReleasedAfterSuccessOrDownstreamFailure()throws Exception {
        var raw=request();raw.setContent("{}".getBytes(StandardCharsets.UTF_8));var response=new MockHttpServletResponse();
        filter.doFilter(raw,response,(req,res)->assertThat(req.getInputStream().readAllBytes()).containsExactly((byte)'{',(byte)'}'));
        assertThatThrownBy(()->filter.doFilter(request(),new MockHttpServletResponse(),(req,res)->{throw new ServletException("Synthetic failure");})).isInstanceOf(ServletException.class);
        var after=new MockFilterChain();filter.doFilter(request(),new MockHttpServletResponse(),after);assertThat(after.getRequest()).isNotNull();
    }
    @Test void compressedOrNonJsonBodiesMustFailBeforeAnyReading()throws Exception {
        for(var encoding:new String[]{"gzip","br"}) {var raw=request();raw.addHeader("Content-Encoding",encoding);reject415(raw);}
        var raw=request();raw.setContentType("text/plain");reject415(raw);
    }
    @Test void excessConcurrentBufferingMustReturnRetryableBusyAndReleaseCapacity()throws Exception {
        filter.doFilter(request(),new MockHttpServletResponse(),(req,res)->{
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request(),response,chain);
            assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getHeader("Retry-After")).isEqualTo("1");assertThat(chain.getRequest()).isNull();
        });
        var chain=new MockFilterChain();filter.doFilter(request(),new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isNotNull();
    }
    @Test void readsConnectorAndUnrelatedRoutesMustNotBeBufferedOrRejectedByThisFilter()throws Exception {
        for(var route:new String[]{"/api/connector/v1/claim","/api/companies/a/other","/api/companies/00000000-0000-0000-0000-000000000001/financial-entries/other/extra"}) {
            var raw=request();raw.setRequestURI(route);raw.setContentType("text/plain");var chain=new MockFilterChain();filter.doFilter(raw,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isSameAs(raw);
        }
        var raw=request();raw.setMethod("GET");raw.setContentType("text/plain");var chain=new MockFilterChain();filter.doFilter(raw,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isSameAs(raw);
    }
    private void reject415(MockHttpServletRequest request)throws Exception {
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);
        assertThat(response.getStatus()).isEqualTo(415);assertThat(chain.getRequest()).isNull();
    }
    private MockHttpServletRequest request() {var request=new MockHttpServletRequest("POST","/api/companies/00000000-0000-0000-0000-000000000001/financial-entries");request.setContentType("application/json");return request;}
}
