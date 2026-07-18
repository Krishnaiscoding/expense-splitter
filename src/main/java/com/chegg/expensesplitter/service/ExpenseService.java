package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.AddExpenseRequest;
import com.chegg.expensesplitter.exception.ExpenseNotFoundException;
import com.chegg.expensesplitter.exception.ValidationException;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;

    public ExpenseService(ExpenseRepository expenseRepository, GroupService groupService) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
    }

    public Expense addExpense(Long groupId, AddExpenseRequest request) {
        Group group = groupService.getGroupOrThrow(groupId);

        List<String> groupMembers = group.getMembers();

        if (!groupMembers.contains(request.getPaidBy())) {
            throw new ValidationException("paidBy '" + request.getPaidBy() + "' is not a member of this group");
        }

        if (request.getSplitAmong() == null || request.getSplitAmong().isEmpty()) {
            throw new ValidationException("splitAmong must be non-empty");
        }

        for (String member : request.getSplitAmong()) {
            if (!groupMembers.contains(member)) {
                throw new ValidationException("splitAmong contains a non-member: '" + member + "'");
            }
        }

        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        Expense expense = new Expense(group, request.getTitle(), amount, request.getPaidBy(), request.getSplitAmong());
        return expenseRepository.save(expense);
    }

    public List<Expense> listExpenses(Long groupId) {
        // Ensure the group exists (throws 404 otherwise)
        groupService.getGroupOrThrow(groupId);
        return expenseRepository.findByGroupIdOrderByCreatedAtAsc(groupId);
    }

    public void deleteExpense(Long groupId, Long expenseId) {
        groupService.getGroupOrThrow(groupId);

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        if (!expense.getGroup().getId().equals(groupId)) {
            throw new ExpenseNotFoundException(expenseId);
        }

        expenseRepository.delete(expense);
    }
}
