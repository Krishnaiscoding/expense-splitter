package com.chegg.expensesplitter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;

public class AddExpenseRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "paidBy is required")
    private String paidBy;

    @NotEmpty(message = "splitAmong is required and cannot be empty")
    private List<String> splitAmong;

    public AddExpenseRequest() {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(String paidBy) {
        this.paidBy = paidBy;
    }

    public List<String> getSplitAmong() {
        return splitAmong;
    }

    public void setSplitAmong(List<String> splitAmong) {
        this.splitAmong = splitAmong;
    }
}
