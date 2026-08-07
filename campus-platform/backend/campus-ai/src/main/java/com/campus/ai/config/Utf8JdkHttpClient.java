package com.campus.ai.config;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

/**
 * Forces UTF-8 decoding for the LLM's SSE stream, regardless of the JVM's
 * default charset. See {@link Utf8ServerSentEventParser} for the full rationale.
 *
 * <p>Why a wrapper instead of a parser injection: {@code OpenAiStreamingChatModel}
 * only exposes {@code httpClientBuilder(...)}; the parser is instantiated
 * inside the model. Overriding the {@code execute(request, parser, listener)}
 * method on the client lets us discard whatever parser the caller passes and
 * substitute our own UTF-8 one, transparently.
 */
public class Utf8JdkHttpClient extends JdkHttpClient {

    public Utf8JdkHttpClient(JdkHttpClientBuilder builder) {
        super(builder);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        super.execute(request, new Utf8ServerSentEventParser(), listener);
    }
}
