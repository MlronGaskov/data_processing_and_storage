package miron.gaskov.server.generation;

import miron.gaskov.common.KeyMaterial;
import miron.gaskov.common.Pem;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class RsaGenerationService {
    private final ConcurrentHashMap<String, CompletableFuture<KeyMaterial>> map = new ConcurrentHashMap<>();

    private final ExecutorService genExecutor;
    private final CertificateService certs;
    private final int keyBits;

    public RsaGenerationService(ExecutorService genExecutor, CertificateService certs, int keyBits) {
        this.genExecutor = Objects.requireNonNull(genExecutor);
        this.certs = Objects.requireNonNull(certs);
        this.keyBits = keyBits;
    }

    public CompletableFuture<KeyMaterial> get(String name) {
        return map.computeIfAbsent(name, n -> {
            var future = new CompletableFuture<KeyMaterial>();

            genExecutor.submit(() -> {
                try {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                    kpg.initialize(keyBits, new SecureRandom());
                    KeyPair kp = kpg.generateKeyPair();

                    var cert = certs.issueCertificate(n, kp.getPublic());
                    byte[] keyPem = Pem.encode("PRIVATE KEY", kp.getPrivate().getEncoded());
                    byte[] crtPem = Pem.encode("CERTIFICATE", cert.getEncoded());

                    future.complete(new KeyMaterial(keyPem, crtPem));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });

            future.whenComplete((keyMaterial, ex) -> {
                if (ex != null) map.remove(n);
            });

            return future;
        });
    }

    public void stop() {
        genExecutor.shutdown();
        try {
            if (!genExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
                genExecutor.shutdownNow();
        } catch (InterruptedException e) {
            genExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
