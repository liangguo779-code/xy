package com.campus.ai.config;

import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static dev.langchain4j.http.client.sse.ServerSentEventListenerUtils.ignoringExceptions;

/**
 * Identical to langchain4j's {@code DefaultServerSentEventParser} except the
 * {@link InputStreamReader} is constructed with {@link StandardCharsets#UTF_8}.
 *
 * <p>Why this exists: on JDK 17 + Windows (zh_CN locale) the JVM default charset
 * is GBK, and the upstream parser's {@code new InputStreamReader(in)} decodes
 * the LLM's UTF-8 SSE stream as GBK, turning tokens like "你好" into "浣犲ソ".
 * The non-streaming path is unaffected because it goes through
 * {@code BodyHandlers.ofString()} which honours the HTTP {@code Content-Type}
 * charset; the streaming path reads the raw {@link InputStream} and so needs
 * an explicit charset. JDK 18+ defaults to UTF-8 and this class becomes a no-op
 * safety net.
 */
public class Utf8ServerSentEventParser implements ServerSentEventParser {

    @Override
    public void parse(InputStream httpResponseBody, ServerSentEventListener listener) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(httpResponseBody, StandardCharsets.UTF_8))) {

            String event = null;
            StringBuilder data = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                        ignoringExceptions(() -> listener.onEvent(sse));
                        event = null;
                        data.setLength(0);
                    }
                    continue;
                }

                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    String content = line.substring("data:".length());
                    if (!data.isEmpty()) {
                        data.append("\n");
                    }
                    data.append(content.trim());
                }
            }

            if (!data.isEmpty()) {
                ServerSentEvent sse = new ServerSentEvent(event, data.toString());
                ignoringExceptions(() -> listener.onEvent(sse));
            }
        } catch (IOException e) {
            ignoringExceptions(() -> listener.onError(e));
        }
    }
}
