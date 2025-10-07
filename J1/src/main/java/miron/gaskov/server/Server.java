package miron.gaskov.server;

import miron.gaskov.common.KeyMaterial;
import miron.gaskov.server.generation.RsaGenerationService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Server {
    private final int port;
    private final RsaGenerationService generation;

    private final Selector selector;
    private final ServerSocketChannel server;
    private final Inbox inbox;

    public Server(int port, RsaGenerationService generation) throws IOException {
        this.port = port;
        this.generation = Objects.requireNonNull(generation);
        this.selector = Selector.open();
        this.server = ServerSocketChannel.open();
        this.server.configureBlocking(false);
        this.server.bind(new InetSocketAddress("0.0.0.0", port));
        this.server.register(selector, SelectionKey.OP_ACCEPT);
        this.inbox = new Inbox(selector);
    }

    public void run() throws IOException {
        System.out.println("KeyGen server started on :" + port);
        while (true) {
            inbox.drain();
            selector.select();
            inbox.drain();

            var it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (!key.isValid()) continue;
                try {
                    if (key.isAcceptable()) onAccept();
                    if (key.isValid() && key.isReadable()) onRead(key);
                    if (key.isValid() && key.isWritable()) onWrite(key);
                } catch (IOException e) {
                    close(key);
                }
            }
        }
    }

    private void onAccept() throws IOException {
        SocketChannel ch = server.accept();
        if (ch == null) return;
        ch.configureBlocking(false);
        SelectionKey key = ch.register(selector, SelectionKey.OP_READ);
        key.attach(new Session());
    }

    private void onRead(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        Session s = (Session) key.attachment();
        int n = ch.read(s.in);
        if (n == -1) {
            close(key);
            return;
        }
        if (n == 0) return;

        s.in.flip();
        while (s.in.hasRemaining() && !s.nameDone) {
            byte b = s.in.get();
            if (b == 0) {
                s.nameDone = true;
                final String name = s.name.toString();
                s.name.setLength(0);
                if (name.isEmpty()) {
                    replyErrorAndClose(key, s, "empty name");
                    break;
                }
                handleNameAsync(key, s, name);
            } else {
                if (s.name.length() < Session.MAX_NAME) s.name.append((char) (b & 0xFF));
            }
        }
        s.in.compact();
    }

    private void handleNameAsync(SelectionKey key, Session s, String name) {
        System.out.println("Request for '" + name + "'");
        generation.get(name).whenComplete((km, ex) -> inbox.post(() -> {
            if (!key.isValid()) return;
            if (ex != null) {
                replyErrorAndClose(key, (Session) key.attachment(), ex.getMessage());
            } else {
                enqueueFrames(key, (Session) key.attachment(), km);
            }
        }));
    }

    private void enqueueFrames(SelectionKey key, Session s, KeyMaterial km) {
        s.out.add(lenPrefixed(km.privateKeyPem()));
        s.out.add(lenPrefixed(km.certificatePem()));
        s.closeAfterFlush = true;
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    private void replyErrorAndClose(SelectionKey key, Session s, String msg) {
        System.err.println("Error: " + msg);
        s.out.add(lenPrefixed(new byte[0]));
        s.out.add(lenPrefixed(new byte[0]));
        s.closeAfterFlush = true;
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    private void onWrite(SelectionKey key) throws IOException {
        SocketChannel ch = (SocketChannel) key.channel();
        Session s = (Session) key.attachment();
        while (!s.out.isEmpty()) {
            ByteBuffer buf = s.out.peek();
            ch.write(buf);
            if (buf.hasRemaining()) break;
            s.out.poll();
        }
        if (s.out.isEmpty()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            if (s.closeAfterFlush) close(key);
        } else {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    private static ByteBuffer lenPrefixed(byte[] payload) {
        ByteBuffer b = ByteBuffer.allocate(4 + payload.length);
        b.putInt(payload.length).put(payload).flip();
        return b;
    }

    private void close(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
        key.cancel();
    }

    private static final class Session {
        static final int MAX_NAME = 1024;
        final ByteBuffer in = ByteBuffer.allocateDirect(8 * 1024);
        final Deque<ByteBuffer> out = new ArrayDeque<>();
        final StringBuilder name = new StringBuilder(64);
        boolean nameDone = false;
        boolean closeAfterFlush = false;
    }

    private static final class Inbox {
        private final Selector selector;
        private final ConcurrentLinkedQueue<Runnable> q = new ConcurrentLinkedQueue<>();

        Inbox(Selector selector) {
            this.selector = selector;
        }

        void post(Runnable r) {
            q.add(r);
            selector.wakeup();
        }

        void drain() {
            for (Runnable r; (r = q.poll()) != null; )
                try {
                    r.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
        }
    }
}
