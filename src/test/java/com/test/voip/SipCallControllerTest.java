package com.test.voip;

import com.test.voip.controller.SipCallController;
import com.test.voip.service.SipCallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class SipCallControllerTest {

    private MockMvc mockMvc;
    private SipCallService sipCallService;

    @BeforeEach
    void setUp() {
        sipCallService = Mockito.mock(SipCallService.class);
        SipCallController controller = new SipCallController(sipCallService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testStartCall_SuccessWithJsonBody() throws Exception {
        Mockito.when(sipCallService.makeSingleCall(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn("mock-call-id-12345");

        String json = """
                {
                    "ani": "13864181000",
                    "calleeUser": "userB",
                    "targetIp": "127.0.0.1",
                    "targetPort": 5060
                }
                """;

        mockMvc.perform(post("/api/call/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.callId").value("mock-call-id-12345"))
                .andExpect(jsonPath("$.ani").value("13864181000"))
                .andExpect(jsonPath("$.callee").value("userB"));
    }

    @Test
    void testStartCall_MissingAni_ReturnsBadRequest() throws Exception {
        String json = """
                {
                    "calleeUser": "userB",
                    "targetIp": "127.0.0.1"
                }
                """;

        mockMvc.perform(post("/api/call/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing required parameter: 'ani' (Caller ID)"));
    }
}
