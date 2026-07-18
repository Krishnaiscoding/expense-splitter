package com.chegg.expensesplitter.dto;

import java.util.List;

public class SettlementResponse {

    private List<SettlementTransaction> settlements;

    public SettlementResponse() {
    }

    public SettlementResponse(List<SettlementTransaction> settlements) {
        this.settlements = settlements;
    }

    public List<SettlementTransaction> getSettlements() {
        return settlements;
    }

    public void setSettlements(List<SettlementTransaction> settlements) {
        this.settlements = settlements;
    }
}
