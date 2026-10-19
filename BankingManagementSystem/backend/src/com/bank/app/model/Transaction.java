package com.bank.app.model;

import java.math.BigDecimal;

/** One recorded money movement on an account. */
public class Transaction {
    private final String id;
    private final String accountNumber;
    private final String type;           // DEPOSIT, WITHDRAW, TRANSFER_IN, TRANSFER_OUT
    private final BigDecimal amount;
    private final BigDecimal balanceAfter;
    private final String timestamp;
    private final String details;

    public Transaction(String id, String accountNumber, String type, BigDecimal amount,
                       BigDecimal balanceAfter, String timestamp, String details) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp = timestamp;
        this.details = details;
    }

    public String getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getTimestamp() { return timestamp; }
    public String getDetails() { return details; }

    /** One line of transactions.txt. */
    public String toLine() {
        return String.join("|", id, accountNumber, type, amount.toPlainString(),
                balanceAfter.toPlainString(), timestamp, details);
    }

    public static Transaction fromLine(String line) {
        String[] p = line.split("\\|", -1);
        return new Transaction(p[0], p[1], p[2], new BigDecimal(p[3]),
                new BigDecimal(p[4]), p[5], p[6]);
    }
}
