package com.prathanbomb.diffsense.providers

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client factory with optimized connection pooling.
 * All AI providers should use this singleton to avoid creating new clients per request.
 */
object HttpClientFactory {

    /**
     * Shared OkHttpClient with connection pooling for better performance.
     * - ConnectionPool: 5 idle connections, 5 minute keep-alive
     * - Connect timeout: 30 seconds
     * - Read timeout: 120 seconds (2 minutes for slow LLM APIs)
     * - Write timeout: 30 seconds
     */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
