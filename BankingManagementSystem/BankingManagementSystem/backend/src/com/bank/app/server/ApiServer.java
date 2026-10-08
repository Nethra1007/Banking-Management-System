package com.bank.app.server;

import com.bank.app.model.Customer;
import com.bank.app.model.Transaction;
import com.bank.app.service.BankService;
import com.bank.app.service.BankService.BankException;
import com.bank.app.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The REST API. Each route reads JSON/query params, calls BankService and
 * writes JSON back. Any error becomes {"success": false, "error": "..."}.
 */
public class ApiServer {

    /** One API route: receives the request data and returns the JSON fields to send back. */
    private interface Route {
        Map<String, Object> handle(Map<String, String> params, HttpExchange exchange) throws IOException;
    }

    private final BankService bank;
    private final HttpServer server;

    public ApiServer(int port, BankService bank) throws IOException {
        this.bank = bank;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        addRoutes();
    }

    public void start() {
        server.start();
    }

    private void addRoutes() {
        route("/api/health", "GET", (p, ex) -> ok("status", "UP"));

        route("/api/register", "POST", (p, ex) -> {
            Customer c = bank.register(p.get("name"), p.get("email"), p.get("password"),
                    p.get("accountType"), p.get("initialDeposit"));
            return ok("account", accountJson(c));
        });

        route("/api/login", "POST", (p, ex) ->
                ok("account", accountJson(bank.login(p.get("accountNumber"), p.get("password")))));

        route("/api/account", "GET", (p, ex) ->
                ok("account", accountJson(bank.getAccount(p.get("accountNumber")))));

        route("/api/deposit", "POST", (p, ex) ->
                ok("account", accountJson(bank.deposit(p.get("accountNumber"), p.get("amount")))));

        route("/api/withdraw", "POST", (p, ex) ->
                ok("account", accountJson(bank.withdraw(p.get("accountNumber"), p.get("amount")))));

        route("/api/transfer", "POST", (p, ex) ->
                ok("account", accountJson(bank.transfer(p.get("fromAccount"), p.get("toAccount"), p.get("amount")))));

        route("/api/transactions", "GET", (p, ex) ->
                ok("transactions", txList(bank.getTransactions(p.get("accountNumber")))));

        route("/api/admin/login", "POST", (p, ex) ->
                ok("token", bank.adminLogin(p.get("username"), p.get("password"))));

        route("/api/admin/accounts", "GET", (p, ex) -> {
            bank.requireAdmin(ex.getRequestHeaders().getFirst("X-Admin-Token"));
            List<Object> list = new ArrayList<>();
            for (Customer c : bank.getAllAccounts()) list.add(accountJson(c));
            return ok("accounts", list);
        });

        route("/api/admin/transactions", "GET", (p, ex) -> {
            bank.requireAdmin(ex.getRequestHeaders().getFirst("X-Admin-Token"));
            return ok("transactions", txList(bank.getAllTransactions()));
        });
    }

    /** Registers a path that accepts one HTTP method and wraps it with error handling. */
    private void route(String path, String method, Route route) {
        server.createContext(path, exchange -> {
            Map<String, Object> response;
            int status = 200;
            try {
                if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                    throw new BankException(405, "Method not allowed. Use " + method);
                }
                Map<String, String> params = method.equals("GET")
                        ? parseQuery(exchange.getRequestURI().getRawQuery())
                        : JsonUtil.parseObject(readBody(exchange));
                response = route.handle(params, exchange);
            } catch (BankException e) {
                status = e.getStatus();
                response = error(e.getMessage());
            } catch (IllegalArgumentException e) {
                status = 400;
                response = error(e.getMessage());
            } catch (Exception e) {              // never let the server crash
                e.printStackTrace();
                status = 500;
                response = error("Internal server error");
            }
            send(exchange, status, JsonUtil.toJson(response));
        });
    }

    // ----------------------------------------------------------------- helpers

    private static Map<String, Object> ok(String key, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put(key, value);
        return m;
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("error", message);
        return m;
    }

    /** Account details for the client. The password hash is never included. */
    private static Map<String, Object> accountJson(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accountNumber", c.getAccountNumber());
        m.put("name", c.getName());
        m.put("email", c.getEmail());
        m.put("accountType", c.getAccountType());
        m.put("balance", c.getBalance());
        return m;
    }

    private static List<Object> txList(List<Transaction> transactions) {
        List<Object> list = new ArrayList<>();
        for (Transaction t : transactions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("accountNumber", t.getAccountNumber());
            m.put("type", t.getType());
            m.put("amount", t.getAmount());
            m.put("balanceAfter", t.getBalanceAfter());
            m.put("timestamp", t.getTimestamp());
            m.put("details", t.getDetails());
            list.add(m);
        }
        return list;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return params;
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
