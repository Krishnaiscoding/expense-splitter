package com.chegg.expensesplitter.controller;

import com.chegg.expensesplitter.dto.AddExpenseRequest;
import com.chegg.expensesplitter.dto.ExpenseResponse;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse addExpense(@PathVariable Long groupId, @Valid @RequestBody AddExpenseRequest request) {
        Expense expense = expenseService.addExpense(groupId, request);
        return ExpenseResponse.from(expense);
    }

    @GetMapping
    public List<ExpenseResponse> listExpenses(@PathVariable Long groupId) {
        return expenseService.listExpenses(groupId).stream()
                .map(ExpenseResponse::from)
                .collect(Collectors.toList());
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long groupId, @PathVariable Long expenseId) {
        expenseService.deleteExpense(groupId, expenseId);
    }
}
