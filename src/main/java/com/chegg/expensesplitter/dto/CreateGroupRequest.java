package com.chegg.expensesplitter.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class CreateGroupRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotEmpty(message = "members list is required and cannot be empty")
    private List<String> members;

    public CreateGroupRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }
}
