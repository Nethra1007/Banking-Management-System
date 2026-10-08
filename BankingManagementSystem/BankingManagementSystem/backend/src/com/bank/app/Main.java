package com.bank.app;

import com.bank.app.server.ApiServer;
import com.bank.app.service.BankService;
import com.bank.app.util.FileStore;

/** Starts the Banking API on port 8080 (or the port given as the first argument). */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        FileStore store = new FileStore("data");          // backend/data/ when run from backend/
        new ApiServer(port, new BankService(store)).start();
        System.out.println("Banking API running at http://localhost:" + port + "/api/health");
        System.out.println("Press Ctrl+C to stop.");
    }
}
