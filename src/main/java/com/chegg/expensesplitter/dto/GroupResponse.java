package com.chegg.expensesplitter.dto;

import com.chegg.expensesplitter.model.Group;

import java.time.Instant;
import java.util.List;

public class GroupResponse {

    private Long id;
    private String name;
    private List<String> members;
    private Instant createdAt;

    public GroupResponse() {
    }

    public GroupResponse(Long id, String name, List<String> members, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.members = members;
        this.createdAt = createdAt;
    }

    public static GroupResponse from(Group group) {
        return new GroupResponse(group.getId(), group.getName(), group.getMembers(), group.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
