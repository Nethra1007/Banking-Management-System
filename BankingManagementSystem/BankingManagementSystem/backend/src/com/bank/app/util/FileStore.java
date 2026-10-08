package com.bank.app.util;

import com.bank.app.model.Customer;
import com.bank.app.model.Transaction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the pipe-delimited .txt files under backend/data/.
 * accounts.txt and transactions.txt are created on first use.
 */
public class FileStore {
    private final Path accountsFile;
    private final Path transactionsFile;

    public FileStore(String dataDir) throws IOException {
        Path dir = Paths.get(dataDir);
        Files.createDirectories(dir);
        accountsFile = dir.resolve("accounts.txt");
        transactionsFile = dir.resolve("transactions.txt");
        if (!Files.exists(accountsFile)) Files.createFile(accountsFile);
        if (!Files.exists(transactionsFile)) Files.createFile(transactionsFile);
    }

    public List<Customer> loadCustomers() throws IOException {
        List<Customer> list = new ArrayList<>();
        for (String line : Files.readAllLines(accountsFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) list.add(Customer.fromLine(line));
        }
        return list;
    }

    /** Rewrites the whole accounts file (fine for a small learning project). */
    public void saveCustomers(List<Customer> customers) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Customer c : customers) lines.add(c.toLine());
        Files.write(accountsFile, lines, StandardCharsets.UTF_8);
    }

    public List<Transaction> loadTransactions() throws IOException {
        List<Transaction> list = new ArrayList<>();
        for (String line : Files.readAllLines(transactionsFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) list.add(Transaction.fromLine(line));
        }
        return list;
    }

    public void appendTransaction(Transaction t) throws IOException {
        Files.write(transactionsFile, List.of(t.toLine()), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }
}
