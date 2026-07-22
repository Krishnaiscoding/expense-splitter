package com.chegg.expensesplitter.dto;

import jakarta.validation.constraints.NotBlank;

public class AddMemberRequest {

    @NotBlank(message = "name is required")
    private String name;

    public AddMemberRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}