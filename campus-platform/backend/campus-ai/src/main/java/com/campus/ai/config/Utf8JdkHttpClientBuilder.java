package com.campus.ai.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;

/**
 * Returns a {@link Utf8JdkHttpClient} from {@link #build()} so the streaming
 * chat model uses UTF-8 SSE parsing without exposing parser configuration.
 */
public class Utf8JdkHttpClientBuilder extends JdkHttpClientBuilder {

    @Override
    public JdkHttpClient build() {
        return new Utf8JdkHttpClient(this);
    }
}
