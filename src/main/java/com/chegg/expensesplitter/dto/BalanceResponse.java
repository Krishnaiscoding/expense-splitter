package com.chegg.expensesplitter.dto;

import java.util.List;

public class BalanceResponse {

    private List<MemberBalance> balances;

    public BalanceResponse() {
    }

    public BalanceResponse(List<MemberBalance> balances) {
        this.balances = balances;
    }

    public List<MemberBalance> getBalances() {
        return balances;
    }

    public void setBalances(List<MemberBalance> balances) {
        this.balances = balances;
    }
}
