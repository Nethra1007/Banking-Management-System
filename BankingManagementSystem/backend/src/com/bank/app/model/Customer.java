package com.bank.app.model;

import java.math.BigDecimal;

/** A bank customer and their single account. Plain data holder. */
public class Customer {
    private final String accountNumber;
    private final String name;
    private final String email;
    private final String passwordHash;   // SHA-256 hex, never the plain password
    private final String accountType;    // SAVINGS or CURRENT
    private BigDecimal balance;

    public Customer(String accountNumber, String name, String email,
                    String passwordHash, String accountType, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.accountType = accountType;
        this.balance = balance;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getAccountType() { return accountType; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    /** One line of accounts.txt: account|name|email|hash|type|balance */
    public String toLine() {
        return String.join("|", accountNumber, name, email, passwordHash,
                accountType, balance.toPlainString());
    }

    /** Rebuilds a Customer from a line written by toLine(). */
    public static Customer fromLine(String line) {
        String[] p = line.split("\\|", -1);
        return new Customer(p[0], p[1], p[2], p[3], p[4], new BigDecimal(p[5]));
    }
}
