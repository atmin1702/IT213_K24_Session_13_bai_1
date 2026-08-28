package com.rikkeiexpress.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.stdio.StdioClientTransport;

import java.util.List;
import java.time.Duration;
import jakarta.annotation.PreDestroy;

@Configuration
public class McpClientConfig {

    private McpSyncClient postgresMcpClient;
    private McpSyncClient filesystemMcpClient;

    @Bean
    public McpSyncClient postgresMcpClient() {
        ServerParameters params = ServerParameters.builder("cmd.exe")
                .args(List.of("/c", "npx", "-y", "@modelcontextprotocol/server-postgres", "postgresql://user:pass@localhost:5432/rikkei_logistics_db"))
                .build();
        
        StdioClientTransport transport = new StdioClientTransport(params);
        this.postgresMcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        return this.postgresMcpClient;
    }

    @Bean
    public McpSyncClient filesystemMcpClient() {
        // Chuẩn hóa đường dẫn cho Windows: Tránh lỗi escape sequence \
        String logisticsPath = "C:/data/logistics/";
        ServerParameters params = ServerParameters.builder("cmd.exe")
                .args(List.of("/c", "npx", "-y", "@modelcontextprotocol/server-filesystem", logisticsPath))
                .build();
        
        StdioClientTransport transport = new StdioClientTransport(params);
        this.filesystemMcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
        return this.filesystemMcpClient;
    }

    @PreDestroy
    public void cleanup() {
        if (postgresMcpClient != null) {
            postgresMcpClient.close();
        }
        if (filesystemMcpClient != null) {
            filesystemMcpClient.close();
        }
    }
}
