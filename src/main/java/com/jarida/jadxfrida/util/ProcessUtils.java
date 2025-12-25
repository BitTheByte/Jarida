package com.jarida.jadxfrida.util;

import java.io.BufferedReader;
import java.io.IOException;
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
        boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new ProcessResult(-1, "", "Timed out after " + timeoutMillis + " ms");
        }
        String stdout = readStream(process.getInputStream());
        String stderr = readStream(process.getErrorStream());
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static String readStream(java.io.InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
