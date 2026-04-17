package com.jarida.jadxfrida.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ProcessUtils {
    private ProcessUtils() {
    }

    public static ProcessResult run(List<String> command, long timeoutMillis) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();

        // Drain stdout/stderr concurrently with waitFor to avoid pipe-buffer deadlock
        // when the child writes more than the OS pipe capacity.
        StreamDrainer outDrainer = new StreamDrainer(process.getInputStream(), "proc-stdout");
        StreamDrainer errDrainer = new StreamDrainer(process.getErrorStream(), "proc-stderr");
        outDrainer.start();
        errDrainer.start();

        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                outDrainer.join(500);
                errDrainer.join(500);
                return new ProcessResult(-1, outDrainer.result(), "Timed out after " + timeoutMillis + " ms");
            }
            outDrainer.join();
            errDrainer.join();
            return new ProcessResult(process.exitValue(), outDrainer.result(), errDrainer.result());
        } finally {
            try { process.getOutputStream().close(); } catch (IOException ignored) { }
        }
    }

    private static final class StreamDrainer extends Thread {
        private final InputStream stream;
        private final StringBuilder buffer = new StringBuilder();
        private volatile IOException error;

        StreamDrainer(InputStream stream, String name) {
            super(name);
            this.stream = stream;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (buffer) {
                        buffer.append(line).append('\n');
                    }
                }
            } catch (IOException e) {
                error = e;
            }
        }

        String result() {
            synchronized (buffer) {
                return buffer.toString();
            }
        }
    }
}
