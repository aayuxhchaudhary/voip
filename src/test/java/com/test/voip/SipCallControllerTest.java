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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SipCallControllerTest {

    private MockMvc mockMvc;
    private SipCallService sipCallService;

    @BeforeEach
    void setUp() {
        sipCallService = Mockito.mock(SipCallService.class);
        SipCallController controller = new SipCallController(sipCallService);
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
}
