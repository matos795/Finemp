package com.finemp.finance.transfer;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class BankTransferPayloadLimitFilterTest {
    final BankTransferPayloadLimitFilter filter=new BankTransferPayloadLimitFilter(1024,1);
    @Test void authorizedMarkerMustApplyTheBudgetToEncodedRoutesAndMisleadingLengths()throws Exception {
        for(var length:new long[]{2048,-1,1}) {
            var request=request();request.setRequestURI("/api/companies/1-1-1-1-1/%62ank-transfers");request.setContent(new byte[1025]);
            var wrapper=new HttpServletRequestWrapper(request) {@Override public long getContentLengthLong() {return length;}};
            var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(wrapper,response,chain);
            assertThat(response.getStatus()).isEqualTo(413);assertThat(response.getContentAsString()).contains("BANK_TRANSFER_PAYLOAD_TOO_LARGE");assertThat(chain.getRequest()).isNull();
        }
    }
    @Test void wrongEncodingAndCapacityExhaustionMustBeBoundedAndCapacityMustRecover()throws Exception {
        var compressed=request();compressed.addHeader("Content-Encoding","gzip");var rejected=new MockHttpServletResponse();filter.doFilter(compressed,rejected,new MockFilterChain());assertThat(rejected.getStatus()).isEqualTo(415);
        filter.doFilter(request(),new MockHttpServletResponse(),(req,res)->{
            var response=new MockHttpServletResponse();filter.doFilter(request(),response,new MockFilterChain());assertThat(response.getStatus()).isEqualTo(429);assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        });
        assertThatThrownBy(()->filter.doFilter(request(),new MockHttpServletResponse(),(req,res)->{throw new ServletException("Synthetic");})).isInstanceOf(ServletException.class);
        var chain=new MockFilterChain();filter.doFilter(request(),new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isNotNull();
    }
    @Test void aForgedHeaderWithoutTheServerAttributeMustNotSelectTheFilter()throws Exception {
        var request=request();request.removeAttribute(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE);request.addHeader(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE,"true");request.setContentType("text/plain");
        var chain=new MockFilterChain();filter.doFilter(request,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isSameAs(request);
    }
    MockHttpServletRequest request() {var request=new MockHttpServletRequest("POST","/unused");request.setAttribute(BankTransferCommandAuthorizationManager.BODY_ATTRIBUTE,Boolean.TRUE);request.setContentType("application/json");return request;}
}
