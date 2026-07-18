package com.chegg.expensesplitter.repository;

import com.chegg.expensesplitter.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByGroupId(Long groupId);

    List<Expense> findByGroupIdOrderByCreatedAtAsc(Long groupId);
}
