package com.bank.app.service;

import com.bank.app.model.Customer;
import com.bank.app.model.Transaction;
import com.bank.app.util.FileStore;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Set;

/**
 * All banking rules live here: registration, login, deposit, withdraw,
 * transfer and history. Methods are synchronized so two requests can never
 * change the same file at the same time.
 */
public class BankService {

    /** Error that the API turns into {"success":false,"error":"..."} with an HTTP status. */
    public static class BankException extends RuntimeException {
        private final int status;
        public BankException(int status, String message) {
            super(message);
            this.status = status;
        }
        public int getStatus() { return status; }
    }

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";
    private static final int FIRST_ACCOUNT_NUMBER = 100001;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FileStore store;
    private final Set<String> adminTokens = new CopyOnWriteArraySet<>();

    public BankService(FileStore store) {
        this.store = store;
    }

    // ---------------------------------------------------------------- customers

    /** Creates a new account and records the initial deposit (if any). */
    public synchronized Customer register(String name, String email, String password,
                                          String accountType, String initialDeposit) throws IOException {
        name = clean(name);
        email = clean(email).toLowerCase();
        if (name.isEmpty()) throw new BankException(400, "Name is required");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new BankException(400, "Enter a valid email address");
        if (password == null || password.length() < 6) throw new BankException(400, "Password must be at least 6 characters");
        String type = accountType == null ? "" : accountType.trim().toUpperCase();
        if (!type.equals("SAVINGS") && !type.equals("CURRENT")) throw new BankException(400, "Account type must be SAVINGS or CURRENT");
        BigDecimal deposit = initialDeposit == null || initialDeposit.isBlank()
                ? BigDecimal.ZERO : parseAmount(initialDeposit, true);

        List<Customer> customers = store.loadCustomers();
        int next = FIRST_ACCOUNT_NUMBER;
        for (Customer c : customers) {
            if (c.getEmail().equals(email)) throw new BankException(400, "An account with this email already exists");
            next = Math.max(next, Integer.parseInt(c.getAccountNumber()) + 1);
        }

        Customer customer = new Customer(String.valueOf(next), name, email, hash(password), type, deposit);
        customers.add(customer);
        store.saveCustomers(customers);
        if (deposit.signum() > 0) {
            record(customer.getAccountNumber(), "DEPOSIT", deposit, deposit, "Initial deposit");
        }
        return customer;
    }

    /** Checks account number + password; returns the customer or throws 401. */
    public synchronized Customer login(String accountNumber, String password) throws IOException {
        Customer c = find(store.loadCustomers(), accountNumber, false);
        if (c == null || password == null || !c.getPasswordHash().equals(hash(password))) {
            throw new BankException(401, "Invalid account number or password");
        }
        return c;
    }

    public synchronized Customer getAccount(String accountNumber) throws IOException {
        return find(store.loadCustomers(), accountNumber, true);
    }

    // ------------------------------------------------------------- money moves

    public synchronized Customer deposit(String accountNumber, String amountText) throws IOException {
        BigDecimal amount = parseAmount(amountText, false);
        List<Customer> customers = store.loadCustomers();
        Customer c = find(customers, accountNumber, true);
        c.setBalance(c.getBalance().add(amount));
        store.saveCustomers(customers);
        record(c.getAccountNumber(), "DEPOSIT", amount, c.getBalance(), "Cash deposit");
        return c;
    }

    public synchronized Customer withdraw(String accountNumber, String amountText) throws IOException {
        BigDecimal amount = parseAmount(amountText, false);
        List<Customer> customers = store.loadCustomers();
        Customer c = find(customers, accountNumber, true);
        if (c.getBalance().compareTo(amount) < 0) throw new BankException(400, "Insufficient balance");
        c.setBalance(c.getBalance().subtract(amount));
        store.saveCustomers(customers);
        record(c.getAccountNumber(), "WITHDRAW", amount, c.getBalance(), "Cash withdrawal");
        return c;
    }

    /** Moves money from one account to another; returns the sender's updated account. */
    public synchronized Customer transfer(String fromAccount, String toAccount, String amountText) throws IOException {
        BigDecimal amount = parseAmount(amountText, false);
        if (fromAccount == null || toAccount == null || fromAccount.trim().equals(toAccount.trim())) {
            throw new BankException(400, "Cannot transfer to the same account");
        }
        List<Customer> customers = store.loadCustomers();
        Customer from = find(customers, fromAccount, true);
        Customer to = find(customers, toAccount, true);
        if (from.getBalance().compareTo(amount) < 0) throw new BankException(400, "Insufficient balance");
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        store.saveCustomers(customers);
        record(from.getAccountNumber(), "TRANSFER_OUT", amount, from.getBalance(), "To account " + to.getAccountNumber());
        record(to.getAccountNumber(), "TRANSFER_IN", amount, to.getBalance(), "From account " + from.getAccountNumber());
        return from;
    }

    // ----------------------------------------------------------------- history

    /** Transactions for one account, newest first. */
    public synchronized List<Transaction> getTransactions(String accountNumber) throws IOException {
        find(store.loadCustomers(), accountNumber, true);   // 404 if the account does not exist
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : store.loadTransactions()) {
            if (t.getAccountNumber().equals(accountNumber.trim())) result.add(0, t);
        }
        return result;
    }

    // ------------------------------------------------------------------- admin

    /** Returns a session token if the hardcoded admin credentials match. */
    public String adminLogin(String username, String password) {
        if (ADMIN_USER.equals(username) && ADMIN_PASS.equals(password)) {
            String token = UUID.randomUUID().toString();
            adminTokens.add(token);
            return token;
        }
        throw new BankException(401, "Invalid admin credentials");
    }

    public void requireAdmin(String token) {
        if (token == null || !adminTokens.contains(token)) throw new BankException(401, "Admin login required");
    }

    public synchronized List<Customer> getAllAccounts() throws IOException {
        return store.loadCustomers();
    }

    public synchronized List<Transaction> getAllTransactions() throws IOException {
        List<Transaction> all = store.loadTransactions();
        java.util.Collections.reverse(all);
        return all;
    }

    // ----------------------------------------------------------------- helpers

    /** Finds an account by number. If mustExist, throws 404 when missing; else returns null. */
    private Customer find(List<Customer> customers, String accountNumber, boolean mustExist) {
        String wanted = accountNumber == null ? "" : accountNumber.trim();
        for (Customer c : customers) {
            if (c.getAccountNumber().equals(wanted)) return c;
        }
        if (mustExist) throw new BankException(404, "Account not found: " + wanted);
        return null;
    }

    /** Parses a money amount; must be > 0 (or >= 0 when allowZero) with at most 2 decimals. */
    private BigDecimal parseAmount(String text, boolean allowZero) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            throw new BankException(400, "Amount must be a number");
        }
        if (amount.signum() < 0 || (amount.signum() == 0 && !allowZero)) {
            throw new BankException(400, "Amount must be greater than zero");
        }
        if (amount.scale() > 2) throw new BankException(400, "Amount can have at most 2 decimal places");
        if (amount.compareTo(new BigDecimal("1000000000")) > 0) throw new BankException(400, "Amount is too large");
        return amount.setScale(2);
    }

    private void record(String account, String type, BigDecimal amount, BigDecimal balanceAfter, String details) throws IOException {
        String id = "T" + (store.loadTransactions().size() + 1);
        String now = LocalDateTime.now().format(TIME_FORMAT);
        store.appendTransaction(new Transaction(id, account, type, amount, balanceAfter, now, details));
    }

    /** Removes the '|' separator and line breaks so user text cannot corrupt the .txt files. */
    private String clean(String text) {
        return text == null ? "" : text.replace("|", " ").replaceAll("[\\r\\n]+", " ").trim();
    }

    /** SHA-256 hash of a password as a hex string. */
    private String hash(String password) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
