package com.chegg.expensesplitter.controller;

import com.chegg.expensesplitter.dto.BalanceResponse;
import com.chegg.expensesplitter.dto.CreateGroupRequest;
import com.chegg.expensesplitter.dto.GroupResponse;
import com.chegg.expensesplitter.dto.SettlementResponse;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.service.BalanceService;
import com.chegg.expensesplitter.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final BalanceService balanceService;

    public GroupController(GroupService groupService, BalanceService balanceService) {
        this.groupService = groupService;
        this.balanceService = balanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Group group = groupService.createGroup(request);
        return GroupResponse.from(group);
    }

    @GetMapping
    public List<GroupResponse> listGroups() {
        return groupService.listGroups().stream()
                .map(GroupResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{groupId}")
    public GroupResponse getGroup(@PathVariable Long groupId) {
        Group group = groupService.getGroupOrThrow(groupId);
        return GroupResponse.from(group);
    }

    @GetMapping("/{groupId}/balances")
    public BalanceResponse getBalances(@PathVariable Long groupId) {
        return new BalanceResponse(balanceService.computeBalances(groupId));
    }

    @GetMapping("/{groupId}/settlements")
    public SettlementResponse getSettlements(@PathVariable Long groupId) {
        return new SettlementResponse(balanceService.computeSettlements(groupId));
    }
}
