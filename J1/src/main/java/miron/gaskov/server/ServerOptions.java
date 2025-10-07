package miron.gaskov.server;

record ServerOptions(
        int port, int threads, int keyBits, String signingKeyPath, String issuerDN, String sigAlg, int validDays
) {
    static ServerOptions parse(String[] args) {
        int port = 9999;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        int keyBits = 8192;
        String key = "signer.key";
        String issuer = "CN=Test Issuer";
        String sigAlg = "SHA512withRSA";
        int validDays = 3650;

        for (int i = 0; i < args.length; i += 2) {
            String v = (i + 1 < args.length) ? args[i + 1] : "";
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(v);
                case "--threads" -> threads = Integer.parseInt(v);
                case "--key-bits" -> keyBits = Integer.parseInt(v);
                case "--signing-key" -> key = v;
                case "--issuer" -> issuer = v;
                case "--sig-alg" -> sigAlg = v;
                case "--valid-days" -> validDays = Integer.parseInt(v);
            }
        }
        return new ServerOptions(port, threads, keyBits, key, issuer, sigAlg, validDays);
    }
}