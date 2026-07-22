package com.chegg.expensesplitter.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class UpdateMembersRequest {

    @NotEmpty(message = "members list is required and cannot be empty")
    private List<String> members;

    public UpdateMembersRequest() {
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }
}