package com.jd.genie.service.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RequestTraceFilterTest {
    @Test
    void returnsGeneratedRequestIdOnEveryResponse() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter(mock(AgentFailureAlertService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertNotNull(response.getHeader(RequestTraceFilter.REQUEST_ID_HEADER));
        assertEquals(36, response.getHeader(RequestTraceFilter.REQUEST_ID_HEADER).length());
    }

    @Test
    void preservesValidCallerRequestId() throws Exception {
        RequestTraceFilter filter = new RequestTraceFilter(mock(AgentFailureAlertService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agent/sessions");
        request.addHeader(RequestTraceFilter.REQUEST_ID_HEADER, "e2e-test-request-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        assertEquals("e2e-test-request-0001", response.getHeader(RequestTraceFilter.REQUEST_ID_HEADER));
    }
}