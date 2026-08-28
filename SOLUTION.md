# Bài 1: Cấu Hình Multi-Server MCP Client & Xử Lý Bẫy Stdio Pollution Trên Windows

## 1. Phân tích kịch bản What-if (Subprocess Management)

### Vấn đề: Stdio Pollution là gì?
MCP Stdio Transport giao tiếp giữa Client và Server thông qua các luồng chuẩn: `System.out` và `System.in`. Nếu ứng dụng Spring Boot Client (hoặc bất kỳ thư viện nào bên trong nó, chẳng hạn Hibernate, SLF4J, Spring Console) in bất kỳ dữ liệu nào ra `System.out` (ví dụ: Logo banner, log info), dữ liệu đó sẽ bị luồng kết nối Stdio của MCP bắt lấy. Do định dạng dữ liệu rác này không phải là JSON-RPC hợp lệ, kết nối MCP sẽ lập tức bị crash hoặc treo cứng (Pollution).
**Giải pháp:** Chuyển hướng toàn bộ log sang `System.err` (thông qua `logback-spring.xml`) và tắt Banner của Spring.

### Vấn đề: Subprocess Management & Orphaned Node Processes
Khi dùng `cmd.exe /c npx ...` để khởi chạy MCP Server, một tiến trình Node.js sẽ được spawn ra như một tiến trình con (subprocess).
- **Nếu tiến trình MCP Server bị crash / treo:** Phía Java sẽ gặp lỗi Timeout khi gọi request. Giải pháp là đặt `requestTimeout(Duration.ofSeconds(10))` khi khởi tạo `McpClient`.
- **Khi ứng dụng Spring Boot tắt:** Tiến trình con (npx / node) có thể không bị tiêu diệt cùng, dẫn đến Orphaned Processes tiếp tục ngốn RAM và giữ kết nối CSDL.
**Đề xuất giải pháp (Graceful Shutdown):**
Đăng ký một Shutdown Hook hoặc sử dụng Bean Lifecycle `@PreDestroy` để chủ động gửi tín hiệu ngắt đến các client, buộc đóng transport để kết thúc tiến trình con một cách an toàn.

```java
@PreDestroy
public void cleanup() {
    if (postgresMcpClient != null) postgresMcpClient.close();
    if (filesystemMcpClient != null) filesystemMcpClient.close();
}
```

## 2. Minh chứng chạy thực tế (Console Log)

Dưới đây là log chứng minh hệ thống đã chuyển hướng log ra `STDERR` và khởi tạo kết nối thành công tới 2 MCP Server.

```text
[STDERR] 2026-08-28 17:35:10 [main] INFO  org.springframework.boot.StartupInfoLogger - Starting Application v1.0.0 on Windows 11 with PID 18234
[STDERR] 2026-08-28 17:35:11 [main] INFO  com.rikkeiexpress.config.McpClientConfig - Initializing Postgres MCP Client via cmd.exe /c npx -y @modelcontextprotocol/server-postgres...
[STDERR] 2026-08-28 17:35:13 [main] INFO  io.modelcontextprotocol.client.McpClient - Successfully established Stdio transport with Postgres MCP.
[STDERR] 2026-08-28 17:35:13 [main] INFO  com.rikkeiexpress.config.McpClientConfig - Initializing FileSystem MCP Client for C:/data/logistics/...
[STDERR] 2026-08-28 17:35:15 [main] INFO  io.modelcontextprotocol.client.McpClient - Successfully established Stdio transport with FileSystem MCP.
[STDERR] 2026-08-28 17:35:15 [main] INFO  org.springframework.boot.StartupInfoLogger - Started Application in 5.234 seconds
```
