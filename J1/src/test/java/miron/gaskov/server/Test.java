package miron.gaskov.server;


import miron.gaskov.client.ClientMain;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class Test {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("scenarios")
    @Timeout(10)
    void runScenario(String serverConfig, List<String> clientConfigs) throws Exception {
        int port = findFreePort();

        Map<String, String> vars = new HashMap<>();
        vars.put("${PORT}", String.valueOf(port));
        vars.put("${SIGNER}", "signer.key");
        vars.put("${ISSUER}", "CN=Test Issuer");

        String[] serverArgs = splitArgs(applyVars(serverConfig, vars));
        Thread serverThread = new Thread(() -> {
            try {
                ServerMain.main(serverArgs);
            } catch (Throwable t) {
                System.err.println("[SERVER] FAILED: " + t);
                t.printStackTrace();
            }
        }, "server-main-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(1000);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(32, clientConfigs.size()));
        List<Future<?>> futures = new ArrayList<>(clientConfigs.size());

        for (String cfg : clientConfigs) {
            String[] args = splitArgs(applyVars(cfg, vars));
            futures.add(pool.submit(() -> {
                try {
                    ClientMain.main(args);
                } catch (Throwable t) {
                    System.err.println("[CLIENT] ERROR: " + t);
                    t.printStackTrace();
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get(5, TimeUnit.MINUTES);
            } catch (TimeoutException te) {
                System.err.println("[TEST] CLIENT TIMEOUT: " + te);
                f.cancel(true);
            } catch (ExecutionException ee) {
                System.err.println("[TEST] CLIENT EXEC ERROR: " + ee.getCause());
                ee.getCause().printStackTrace();
            }
        }

        pool.shutdownNow();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("[TEST] Scenario finished on port " + port);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> scenarios() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "--signing-key ${SIGNER} --issuer \"${ISSUER}\" --port ${PORT} --threads 4 --key-bits 2048 --valid-days 30",
                        List.of(
                                "--host 127.0.0.1 --port ${PORT} --name a1 --out out/a1 --abort true",
                                "--host 127.0.0.1 --port ${PORT} --name a2 --out out/a2 --abort true",
                                "--host 127.0.0.1 --port ${PORT} --name b1 --out out/b1",
                                "--host 127.0.0.1 --port ${PORT} --name b2 --out out/b2",
                                "--host 127.0.0.1 --port ${PORT} --name c1 --out out/c1 --delay 2",
                                "--host 127.0.0.1 --port ${PORT} --name c2 --out out/c2 --delay 2"
                        )
                ),

                org.junit.jupiter.params.provider.Arguments.of(
                        "--signing-key ${SIGNER} --issuer '${ISSUER}' --port ${PORT} --threads 4 --key-bits 2048 --valid-days 30",
                        buildMixedClients()
                ),

                org.junit.jupiter.params.provider.Arguments.of(
                        "--signing-key ${SIGNER} --issuer 'CN=Long Issuer' --port ${PORT} --threads 4 --key-bits 2048 --valid-days 30",
                        buildSlowAndNormal(Duration.ofSeconds(1))
                )
        );
    }

    private static List<String> buildMixedClients() {
        List<String> list = new ArrayList<>(20);
        int idx = 0;
        for (int i = 0; i < 10; i++) {
            list.add("--host 127.0.0.1 --port ${PORT} --name a" + (++idx) + " --out out/a" + idx + " --abort true");
        }
        for (int i = 10; i < 20; i++) {
            String d = Duration.ZERO.isZero() ? "" : (" --delay " + Duration.ZERO.toSeconds());
            list.add("--host 127.0.0.1 --port ${PORT} --name b" + (++idx) + " --out out/b" + idx + d);
        }
        return list;
    }

    private static List<String> buildSlowAndNormal(Duration slowDelay) {
        List<String> list = new ArrayList<>(6 + 6);
        for (int i = 1; i <= 6; i++) {
            list.add("--host 127.0.0.1 --port ${PORT} --name n" + i + " --out out/n" + i);
        }
        for (int i = 1; i <= 6; i++) {
            list.add("--host 127.0.0.1 --port ${PORT} --name s" + i + " --out out/s" + i + " --delay " + slowDelay.toSeconds());
        }
        return list;
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setReuseAddress(true);
            return ss.getLocalPort();
        }
    }

    private static String applyVars(String s, Map<String, String> vars) {
        String r = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            r = r.replace(e.getKey(), e.getValue());
        }
        return r;
    }

    private static String[] splitArgs(String cmdline) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false, escape = false;

        for (int i = 0; i < cmdline.length(); i++) {
            char c = cmdline.charAt(i);
            if (escape) {
                cur.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out.toArray(String[]::new);
    }
}
