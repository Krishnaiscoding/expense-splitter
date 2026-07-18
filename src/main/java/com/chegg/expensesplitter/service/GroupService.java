package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.CreateGroupRequest;
import com.chegg.expensesplitter.exception.GroupNotFoundException;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.repository.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
