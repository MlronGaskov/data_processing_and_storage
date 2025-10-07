package miron.gaskov.client;

record ClientOptions(String host, int port, String name, String outPrefix, int delaySec, boolean abort) {
    static ClientOptions parse(String[] args) {
        String host = "127.0.0.1";
        int port = 9999;
        String name = "miron";
        String out = "out";
        int delay = 0;
        boolean abort = false;

        for (int i = 0; i < args.length; i += 2) {
            String v = (i + 1 < args.length) ? args[i + 1] : "";
            switch (args[i]) {
                case "--host" -> host = v;
                case "--port" -> port = Integer.parseInt(v);
                case "--name" -> name = v;
                case "--out" -> out = v;
                case "--delay" -> delay = Integer.parseInt(v);
                case "--abort" -> abort = Boolean.parseBoolean(v);
            }
        }
        return new ClientOptions(host, port, name, out, delay, abort);
    }
}