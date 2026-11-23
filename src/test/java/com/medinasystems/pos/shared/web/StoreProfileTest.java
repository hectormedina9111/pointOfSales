package com.medinasystems.pos.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.medinasystems.pos.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("store")
@TestPropertySource(properties = {"pos.node.number=1", "pos.node.central-url=http://central:8080"})
class StoreProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsStoreNode() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeRole").value("STORE"))
                .andExpect(jsonPath("$.nodeNumber").value(1));
    }
}
