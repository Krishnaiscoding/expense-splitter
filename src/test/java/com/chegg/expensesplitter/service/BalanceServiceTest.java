package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.AddExpenseRequest;
import com.chegg.expensesplitter.dto.CreateGroupRequest;
import com.chegg.expensesplitter.dto.MemberBalance;
import com.chegg.expensesplitter.dto.SettlementTransaction;
import com.chegg.expensesplitter.exception.ValidationException;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.model.Group;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class BalanceServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private BalanceService balanceService;

    private Group createGroup(String name, String... members) {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName(name);
        request.setMembers(List.of(members));
        return groupService.createGroup(request);
    }

    private void addExpense(Long groupId, String title, String amount, String paidBy, List<String> splitAmong) {
        AddExpenseRequest request = new AddExpenseRequest();
        request.setTitle(title);
        request.setAmount(new BigDecimal(amount));
        request.setPaidBy(paidBy);
        request.setSplitAmong(splitAmong);
        expenseService.addExpense(groupId, request);
    }

    private BigDecimal balanceOf(List<MemberBalance> balances, String member) {
        return balances.stream()
                .filter(b -> b.getMember().equals(member))
                .findFirst()
                .orElseThrow()
                .getNetBalance();
    }

    @Test
    void creatingAGroupPersistsNameAndMembers() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        assertThat(group.getId()).isNotNull();
        assertThat(group.getName()).isEqualTo("Goa Trip");
        assertThat(group.getMembers()).containsExactly("Alice", "Bob", "Carol");
    }

    @Test
    void addingAnExpenseMakesItAppearInTheGroupsExpenseList() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        addExpense(group.getId(), "Hotel", "3000.00", "Alice", List.of("Alice", "Bob", "Carol"));

        List<Expense> expenses = expenseService.listExpenses(group.getId());
        assertThat(expenses).hasSize(1);
        assertThat(expenses.get(0).getTitle()).isEqualTo("Hotel");
        assertThat(expenses.get(0).getAmount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void balanceCalculationIsCorrectForTheAssignmentExample() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        addExpense(group.getId(), "Hotel", "3000.00", "Alice", List.of("Alice", "Bob", "Carol"));

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());

        assertThat(balanceOf(balances, "Alice")).isEqualByComparingTo("2000.00");
        assertThat(balanceOf(balances, "Bob")).isEqualByComparingTo("-1000.00");
        assertThat(balanceOf(balances, "Carol")).isEqualByComparingTo("-1000.00");
    }

    @Test
    void balanceCalculationIsCorrectAcrossMultipleExpenses() {
        Group group = createGroup("Team Lunch", "Alice", "Bob", "Carol", "Dave");

        addExpense(group.getId(), "Lunch", "100.00", "Alice", List.of("Alice", "Bob", "Carol", "Dave"));
        addExpense(group.getId(), "Cab", "40.00", "Bob", List.of("Alice", "Bob"));
        addExpense(group.getId(), "Snacks", "30.00", "Carol", List.of("Carol", "Dave"));

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());

        // Alice: paid 100, owes 25 (lunch) + 20 (cab) = 45 -> net +55
        assertThat(balanceOf(balances, "Alice")).isEqualByComparingTo("55.00");
        // Bob: paid 40, owes 25 (lunch) + 20 (cab) = 45 -> net -5
        assertThat(balanceOf(balances, "Bob")).isEqualByComparingTo("-5.00");
        // Carol: paid 30, owes 25 (lunch) + 15 (snacks) = 40 -> net -10
        assertThat(balanceOf(balances, "Carol")).isEqualByComparingTo("-10.00");
        // Dave: paid 0, owes 25 (lunch) + 15 (snacks) = 40 -> net -40
        assertThat(balanceOf(balances, "Dave")).isEqualByComparingTo("-40.00");

        // Net balances across the group must always sum to zero.
        BigDecimal sum = balances.stream().map(MemberBalance::getNetBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0.00");
    }

    @Test
    void unequalRoundingCentsAreDistributedWithoutLosingOrGainingMoney() {
        Group group = createGroup("Odd Split", "Alice", "Bob", "Carol");

        // 100 / 3 = 33.33333... -> shares must still sum to exactly 100.00
        addExpense(group.getId(), "Coffee", "100.00", "Alice", List.of("Alice", "Bob", "Carol"));

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());
        BigDecimal sum = balances.stream().map(MemberBalance::getNetBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("0.00");
    }

    @Test
    void settlementsMinimizeTransactionCountForSimpleThreeWayCase() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        addExpense(group.getId(), "Hotel", "3000.00", "Alice", List.of("Alice", "Bob", "Carol"));

        List<SettlementTransaction> settlements = balanceService.computeSettlements(group.getId());

        assertThat(settlements).hasSize(2);
        assertThat(settlements).allSatisfy(t -> assertThat(t.getTo()).isEqualTo("Alice"));

        BigDecimal totalPaidToAlice = settlements.stream()
                .map(SettlementTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalPaidToAlice).isEqualByComparingTo("2000.00");
    }

    @Test
    void settlementsProduceFewerTransactionsThanNaivePairwiseSettlement() {
        Group group = createGroup("Four Way", "Alice", "Bob", "Carol", "Dave");

        // Alice pays for everyone: everyone else owes 100, Alice is owed 300.
        addExpense(group.getId(), "Everything", "400.00", "Alice",
                List.of("Alice", "Bob", "Carol", "Dave"));

        List<SettlementTransaction> settlements = balanceService.computeSettlements(group.getId());

        // With a single creditor, 3 transactions is optimal (n-1 debtors -> creditor).
        assertThat(settlements).hasSize(3);
        BigDecimal total = settlements.stream()
                .map(SettlementTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("300.00");
    }

    @Test
    void oneMemberPayingAllExpensesResultsInEveryoneElseOwingThem() {
        Group group = createGroup("One Payer", "Alice", "Bob", "Carol");

        addExpense(group.getId(), "Groceries", "60.00", "Alice", List.of("Alice", "Bob", "Carol"));
        addExpense(group.getId(), "Gas", "30.00", "Alice", List.of("Alice", "Bob", "Carol"));

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());

        assertThat(balanceOf(balances, "Alice")).isEqualByComparingTo("60.00");
        assertThat(balanceOf(balances, "Bob")).isEqualByComparingTo("-30.00");
        assertThat(balanceOf(balances, "Carol")).isEqualByComparingTo("-30.00");
    }

    @Test
    void deletingTheOnlyExpenseInAGroupResetsAllBalancesToZero() {
        Group group = createGroup("Solo Expense", "Alice", "Bob");

        addExpense(group.getId(), "Dinner", "50.00", "Alice", List.of("Alice", "Bob"));
        Long expenseId = expenseService.listExpenses(group.getId()).get(0).getId();

        expenseService.deleteExpense(group.getId(), expenseId);

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());
        assertThat(balanceOf(balances, "Alice")).isEqualByComparingTo("0.00");
        assertThat(balanceOf(balances, "Bob")).isEqualByComparingTo("0.00");
        assertThat(expenseService.listExpenses(group.getId())).isEmpty();
    }

    @Test
    void deletingAnExpenseUpdatesBalancesCorrectly() {
        Group group = createGroup("Deletion Test", "Alice", "Bob", "Carol");

        addExpense(group.getId(), "Hotel", "3000.00", "Alice", List.of("Alice", "Bob", "Carol"));
        addExpense(group.getId(), "Taxi", "300.00", "Bob", List.of("Alice", "Bob", "Carol"));

        Long taxiExpenseId = expenseService.listExpenses(group.getId()).stream()
                .filter(e -> e.getTitle().equals("Taxi"))
                .findFirst()
                .orElseThrow()
                .getId();

        expenseService.deleteExpense(group.getId(), taxiExpenseId);

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());
        // Only the Hotel expense should remain.
        assertThat(balanceOf(balances, "Alice")).isEqualByComparingTo("2000.00");
        assertThat(balanceOf(balances, "Bob")).isEqualByComparingTo("-1000.00");
        assertThat(balanceOf(balances, "Carol")).isEqualByComparingTo("-1000.00");
    }

    @Test
    void memberListedInSplitAmongWhoNeverPaysAnythingOnlyEverOwesMoney() {
        Group group = createGroup("Free Rider", "Alice", "Bob", "Carol");

        addExpense(group.getId(), "Dinner", "90.00", "Alice", List.of("Alice", "Bob", "Carol"));
        addExpense(group.getId(), "Drinks", "60.00", "Bob", List.of("Alice", "Bob", "Carol"));

        List<MemberBalance> balances = balanceService.computeBalances(group.getId());

        // Carol never pays, only owes: 30 + 20 = 50
        assertThat(balanceOf(balances, "Carol")).isEqualByComparingTo("-50.00");
    }

    @Test
    void addingExpenseWithPaidByNotInGroupThrowsValidationException() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        assertThrows(ValidationException.class, () ->
                addExpense(group.getId(), "Hotel", "3000.00", "Eve", List.of("Alice", "Bob", "Carol")));
    }

    @Test
    void addingExpenseWithEmptySplitAmongThrowsValidationException() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        assertThrows(ValidationException.class, () ->
                addExpense(group.getId(), "Hotel", "3000.00", "Alice", List.of()));
    }

    @Test
    void addingExpenseWithSplitAmongContainingNonMemberThrowsValidationException() {
        Group group = createGroup("Goa Trip", "Alice", "Bob", "Carol");

        assertThrows(ValidationException.class, () ->
                addExpense(group.getId(), "Hotel", "3000.00", "Alice", List.of("Alice", "Eve")));
    }

    @Test
    void splitEquallyDistributesAmountExactlyWithNoRoundingLoss() {
        Map<String, BigDecimal> shares = balanceService.splitEqually(
                new BigDecimal("100.00"), List.of("Alice", "Bob", "Carol"));

        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");
        // 100 / 3 = 33.33 each, with the leftover cent going to the first member.
        assertThat(shares.get("Alice")).isEqualByComparingTo("33.34");
        assertThat(shares.get("Bob")).isEqualByComparingTo("33.33");
        assertThat(shares.get("Carol")).isEqualByComparingTo("33.33");
    }
}
