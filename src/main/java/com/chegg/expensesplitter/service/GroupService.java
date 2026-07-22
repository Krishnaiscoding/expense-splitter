package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.CreateGroupRequest;
import com.chegg.expensesplitter.exception.GroupNotFoundException;
import com.chegg.expensesplitter.exception.ValidationException;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.repository.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public Group createGroup(CreateGroupRequest request) {
        Group group = new Group(request.getName(), request.getMembers());
        return groupRepository.save(group);
    }

    public List<Group> listGroups() {
        return groupRepository.findAll();
    }

    public Group getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }

    public Group addMember(Long groupId, String name) {
        Group group = getGroupOrThrow(groupId);
        String trimmed = normalize(name);

        if (trimmed.isEmpty()) {
            throw new ValidationException("member name must not be blank");
        }
        if (group.getMembers().contains(trimmed)) {
            throw new ValidationException("'" + trimmed + "' is already a member of this group");
        }

        group.getMembers().add(trimmed);
        return groupRepository.save(group);
    }

    public Group updateMembers(Long groupId, List<String> members) {
        Group group = getGroupOrThrow(groupId);

        if (members == null || members.isEmpty()) {
            throw new ValidationException("members list must not be empty");
        }

        List<String> normalized = members.stream()
                .map(this::normalize)
                .collect(Collectors.toList());

        if (normalized.stream().anyMatch(String::isEmpty)) {
            throw new ValidationException("member names must not be blank");
        }

        Set<String> deduped = new LinkedHashSet<>(normalized);
        if (deduped.size() != normalized.size()) {
            throw new ValidationException("duplicate member names are not allowed");
        }

        group.setMembers(new ArrayList<>(deduped));
        return groupRepository.save(group);
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim();
    }
}
