package com.chegg.expensesplitter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Long createGroup(String name, String... members) throws Exception {
        Map<String, Object> body = Map.of("name", name, "members", members);
        String response = mockMvc.perform(post("/api/groups")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();
    }

    @Test
    void createGroupReturns201WithGroupDetails() throws Exception {
        Map<String, Object> body = Map.of("name", "Goa Trip", "members", new String[]{"Alice", "Bob", "Carol"});

        mockMvc.perform(post("/api/groups")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Goa Trip"))
                .andExpect(jsonPath("$.members", org.hamcrest.Matchers.contains("Alice", "Bob", "Carol")));
    }

    @Test
    void createGroupWithMissingNameReturns400() throws Exception {
        Map<String, Object> body = Map.of("members", new String[]{"Alice", "Bob"});

        mockMvc.perform(post("/api/groups")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getGroupThatDoesNotExistReturns404() throws Exception {
        mockMvc.perform(get("/api/groups/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void listGroupsReturnsAllCreatedGroups() throws Exception {
        createGroup("Group A", "Alice", "Bob");
        createGroup("Group B", "Carol", "Dave");

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(2))));
    }

    @Test
    void addExpenseReturns201AndAppearsInExpenseList() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        Map<String, Object> expenseBody = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Alice",
                "splitAmong", new String[]{"Alice", "Bob", "Carol"}
        );

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hotel"))
                .andExpect(jsonPath("$.amount").value(3000.00));

        mockMvc.perform(get("/api/groups/{groupId}/expenses", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Hotel"));
    }

    @Test
    void addExpenseWithPaidByNotInGroupReturns422() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        Map<String, Object> expenseBody = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Eve",
                "splitAmong", new String[]{"Alice", "Bob", "Carol"}
        );

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void addExpenseWithEmptySplitAmongReturns400() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        Map<String, Object> expenseBody = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Alice",
                "splitAmong", new String[]{}
        );

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getBalancesReturnsCorrectNetBalances() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        Map<String, Object> expenseBody = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Alice",
                "splitAmong", new String[]{"Alice", "Bob", "Carol"}
        );

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/groups/{groupId}/balances", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances", org.hamcrest.Matchers.hasSize(3)));
    }

    @Test
    void getSettlementsReturnsSimplifiedTransactions() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        Map<String, Object> expenseBody = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Alice",
                "splitAmong", new String[]{"Alice", "Bob", "Carol"}
        );

        mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/groups/{groupId}/settlements", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void deleteExpenseReturns204AndRemovesItFromList() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        Map<String, Object> expenseBody = Map.of(
                "title", "Hotel",
                "amount", 3000.00,
                "paidBy", "Alice",
                "splitAmong", new String[]{"Alice", "Bob", "Carol"}
        );

        String response = mockMvc.perform(post("/api/groups/{groupId}/expenses", groupId)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(expenseBody)))
                .andReturn().getResponse().getContentAsString();
        Long expenseId = ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();

        mockMvc.perform(delete("/api/groups/{groupId}/expenses/{expenseId}", groupId, expenseId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/{groupId}/expenses", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void deleteExpenseThatDoesNotExistReturns404() throws Exception {
        Long groupId = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        mockMvc.perform(delete("/api/groups/{groupId}/expenses/{expenseId}", groupId, 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
