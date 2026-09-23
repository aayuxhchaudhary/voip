package com.test.voip;

import com.test.voip.controller.SipCallController;
import com.test.voip.service.SipCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.test.voip.entity.CallDetail;
import com.test.voip.service.CallDetailService;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SipCallControllerTest {

    private MockMvc mockMvc;
    private SipCallService sipCallService;
    private CallDetailService callDetailService;

    @BeforeEach
    void setUp() {
        sipCallService = Mockito.mock(SipCallService.class);
        callDetailService = Mockito.mock(CallDetailService.class);
        SipCallController controller = new SipCallController(sipCallService, callDetailService);
        ReflectionTestUtils.setField(controller, "serverHost", "127.0.0.1");
        ReflectionTestUtils.setField(controller, "serverPort", 5060);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testPutCall_MethodNotAllowed() throws Exception {
        String json = """
                {
                    "caller": "13864181000",
                    "callee": "userB"
                }
                """;

        mockMvc.perform(put("/api/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void testPostCall_Success() throws Exception {
        Mockito.when(sipCallService.makeSingleCall(eq("13864181000"), eq("userB"), eq("127.0.0.1"), eq(5060)))
                .thenReturn("test-call-id-12345");

        String json = """
                {
                    "caller": "13864181000",
                    "callee": "userB"
                }
                """;

        mockMvc.perform(post("/api/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.callId").value("test-call-id-12345"))
                .andExpect(jsonPath("$.caller").value("13864181000"))
                .andExpect(jsonPath("$.callee").value("userB"));
    }

    @Test
    void testCall_MissingCaller_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "callee": "userB"
                }
                """;

        mockMvc.perform(post("/api/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing required parameter: caller"));
    }

    @Test
    void testCall_MissingCallee_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "caller": "13864181000"
                }
                """;

        mockMvc.perform(post("/api/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing required parameter: callee"));
    }

    @Test
    void testCall_ServiceThrowsException_ReturnsInternalServerError() throws Exception {
        Mockito.when(sipCallService.makeSingleCall(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Simulated socket error"));

        String json = """
                {
                    "caller": "13864181000",
                    "callee": "userB"
                }
                """;

        mockMvc.perform(post("/api/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("SIP call failed"));
    }

    @Test
    void testHangupCall_Success() throws Exception {
        Mockito.when(sipCallService.hangupCall("call-123")).thenReturn(true);

        mockMvc.perform(post("/api/call/hangup")
                        .param("callId", "call-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value("call-123"))
                .andExpect(jsonPath("$.status").value("CALL_TERMINATED"))
                .andExpect(jsonPath("$.message").value("Call terminated, audio recording saved"));
    }

    @Test
    void testHangupCall_NotFound() throws Exception {
        Mockito.when(sipCallService.hangupCall("call-unknown")).thenReturn(false);

        mockMvc.perform(post("/api/call/hangup")
                        .param("callId", "call-unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value("call-unknown"))
                .andExpect(jsonPath("$.status").value("CALL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No active call found"));
    }

    @Test
    void testGetAllCallDetails() throws Exception {
        CallDetail detail = new CallDetail();
        detail.setCallId("call-abc");
        detail.setSrcNumber("101");
        detail.setDstNumber("102");
        detail.setStatus("ANSWERED");
        Mockito.when(callDetailService.getAllCallDetails()).thenReturn(List.of(detail));

        mockMvc.perform(get("/api/call/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].callId").value("call-abc"))
                .andExpect(jsonPath("$[0].srcNumber").value("101"))
                .andExpect(jsonPath("$[0].dstNumber").value("102"))
                .andExpect(jsonPath("$[0].status").value("ANSWERED"));
    }

    @Test
    void testGetCallDetailByCallId() throws Exception {
        CallDetail detail = new CallDetail();
        detail.setCallId("call-abc");
        detail.setSrcNumber("101");
        detail.setDstNumber("102");
        detail.setStatus("ANSWERED");
        Mockito.when(callDetailService.getCallDetailByCallId("call-abc")).thenReturn(detail);

        mockMvc.perform(get("/api/call/details/call-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value("call-abc"))
                .andExpect(jsonPath("$.srcNumber").value("101"))
                .andExpect(jsonPath("$.dstNumber").value("102"))
                .andExpect(jsonPath("$.status").value("ANSWERED"));
    }
}
