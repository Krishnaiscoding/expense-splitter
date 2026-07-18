package com.chegg.expensesplitter.service;

import com.chegg.expensesplitter.dto.MemberBalance;
import com.chegg.expensesplitter.dto.SettlementTransaction;
import com.chegg.expensesplitter.model.Expense;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes net balances and simplified settlement transactions for a group.
 * Balances are always derived on-the-fly from the current set of expenses -
 * nothing is persisted here.
 */
@Service
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final GroupService groupService;

    public BalanceService(ExpenseRepository expenseRepository, GroupService groupService) {
        this.expenseRepository = expenseRepository;
        this.groupService = groupService;
    }

    /**
     * Net balance per member = (total amount they paid) - (total amount they owe
     * across all expenses they were part of). Positive = they are owed money.
     * Negative = they owe money.
     */
    public List<MemberBalance> computeBalances(Long groupId) {
        Group group = groupService.getGroupOrThrow(groupId);
        List<Expense> expenses = expenseRepository.findByGroupId(groupId);

        // LinkedHashMap keeps output ordered the same way the group's member list was defined.
        Map<String, BigDecimal> netBalances = new LinkedHashMap<>();
        for (String member : group.getMembers()) {
            netBalances.put(member, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        for (Expense expense : expenses) {
            // Credit the payer with the full amount.
            netBalances.merge(expense.getPaidBy(), expense.getAmount(), BigDecimal::add);

            // Debit each participant their fair share.
            Map<String, BigDecimal> shares = splitEqually(expense.getAmount(), expense.getSplitAmong());
            for (Map.Entry<String, BigDecimal> shareEntry : shares.entrySet()) {
                netBalances.merge(shareEntry.getKey(), shareEntry.getValue().negate(), BigDecimal::add);
            }
        }

        List<MemberBalance> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : netBalances.entrySet()) {
            result.add(new MemberBalance(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    /**
     * Splits `amount` equally among `members`, returning each member's exact
     * share such that the shares sum to exactly `amount` (no rounding drift).
     *
     * Approach: compute a floor share per member (2 decimal places), then
     * distribute the remaining leftover cents (amount - floorShare * n) one
     * cent at a time to members in list order. This guarantees the shares
     * always sum precisely to the original amount, using BigDecimal only.
     */
    public Map<String, BigDecimal> splitEqually(BigDecimal amount, List<String> members) {
        int n = members.size();
        Map<String, BigDecimal> shares = new LinkedHashMap<>();

        if (n == 0) {
            return shares;
        }

        BigDecimal floorShare = amount.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal totalFloor = floorShare.multiply(BigDecimal.valueOf(n));
        BigDecimal remainder = amount.subtract(totalFloor); // e.g. 0.02 left over
        BigDecimal cent = new BigDecimal("0.01");
        int remainderCents = remainder.divide(cent, 0, RoundingMode.HALF_UP).intValue();

        int index = 0;
        for (String member : members) {
            BigDecimal share = floorShare;
            if (index < remainderCents) {
                share = share.add(cent);
            }
            // If the same member name appears twice in splitAmong, combine their shares.
            shares.merge(member, share, BigDecimal::add);
            index++;
        }

        return shares;
    }

    /**
     * Settlement algorithm: greedy largest-debtor-pays-largest-creditor.
     *
     * 1. Split members into creditors (netBalance > 0) and debtors (netBalance < 0).
     * 2. Repeatedly take the debtor who owes the most and the creditor who is owed
     *    the most, and settle the smaller of the two amounts between them.
     * 3. Whichever side reaches zero first drops out; the other carries the
     *    remainder forward to the next largest counterpart.
     * 4. Repeat until every balance is zero.
     *
     * This greedy approach does not guarantee the mathematically minimal number
     * of transactions in every possible case, but it performs well in practice
     * and is the approach suggested by the assignment brief.
     */
    public List<SettlementTransaction> computeSettlements(Long groupId) {
        List<MemberBalance> balances = computeBalances(groupId);

        List<BalanceEntry> creditors = new ArrayList<>();
        List<BalanceEntry> debtors = new ArrayList<>();
        BigDecimal cent = new BigDecimal("0.01");

        for (MemberBalance balance : balances) {
            BigDecimal amount = balance.getNetBalance();
            if (amount.compareTo(cent) >= 0) {
                creditors.add(new BalanceEntry(balance.getMember(), amount));
            } else if (amount.compareTo(cent.negate()) <= 0) {
                debtors.add(new BalanceEntry(balance.getMember(), amount.abs()));
            }
        }

        creditors.sort((a, b) -> b.amount.compareTo(a.amount));
        debtors.sort((a, b) -> b.amount.compareTo(a.amount));

        List<SettlementTransaction> settlements = new ArrayList<>();

        int i = 0;
        int j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            BalanceEntry debtor = debtors.get(i);
            BalanceEntry creditor = creditors.get(j);

            BigDecimal settledAmount = debtor.amount.min(creditor.amount);

            if (settledAmount.compareTo(BigDecimal.ZERO) > 0) {
                settlements.add(new SettlementTransaction(
                        debtor.member,
                        creditor.member,
                        settledAmount.setScale(2, RoundingMode.HALF_UP)
                ));
            }

            debtor.amount = debtor.amount.subtract(settledAmount);
            creditor.amount = creditor.amount.subtract(settledAmount);

            if (debtor.amount.compareTo(cent) < 0) {
                i++;
            }
            if (creditor.amount.compareTo(cent) < 0) {
                j++;
            }
        }

        return settlements;
    }

    private static class BalanceEntry {
        private final String member;
        private BigDecimal amount;

        private BalanceEntry(String member, BigDecimal amount) {
            this.member = member;
            this.amount = amount;
        }
    }
}
