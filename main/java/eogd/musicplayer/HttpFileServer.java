package eogd.musicplayer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class HttpFileServer {

    private final JavaPlugin plugin;
    private HttpServer server;
    private final String servePathContext;
    private final File baseServeDirectory;

    public HttpFileServer(JavaPlugin plugin, String configuredServePath, File baseServeDirectory) {
        this.plugin = plugin;
        String tempPath = configuredServePath;
        if (!tempPath.startsWith("/")) {
            tempPath = "/" + tempPath;
        }
        if (tempPath.endsWith("/") && tempPath.length() > 1) {
            tempPath = tempPath.substring(0, tempPath.length() - 1);
        }
        this.servePathContext = tempPath;
        this.baseServeDirectory = baseServeDirectory;
        if (!this.baseServeDirectory.exists()) {
            this.baseServeDirectory.mkdirs();
        }
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext(servePathContext, new FileHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("内置 HTTP 文件服务器已在端口 " + port + " 启动，服务上下文: " + servePathContext);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "启动内置 HTTP 文件服务器失败，端口: " + port, e);
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("内置 HTTP 文件服务器已停止。");
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String getFileUrl(String publicAddressFromConfig, int httpPort, String fileNameInDirectory) {
        String addressToUse = publicAddressFromConfig;
        if (addressToUse == null || addressToUse.isEmpty()) {
            plugin.getLogger().warning("HTTP 服务器的 publicAddress 未在 config.yml 中配置。将尝试使用 127.0.0.1，这可能只对本机客户端有效。");
            addressToUse = "127.0.0.1";
        }

        if (addressToUse.contains(":")) {
            plugin.getLogger().warning("配置的 publicAddress ('" + publicAddressFromConfig + "') 包含端口号。建议只配置纯域名/IP。将尝试移除端口部分。");
            try {
                if (addressToUse.lastIndexOf(':') > addressToUse.lastIndexOf(']')) {
                    addressToUse = addressToUse.substring(0, addressToUse.lastIndexOf(':'));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("无法从配置的 publicAddress 中安全移除端口: " + publicAddressFromConfig + ". URL 可能仍然无效。");
            }
        }
        return "http://" + addressToUse + ":" + httpPort + servePathContext + "/" + fileNameInDirectory;
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();

            if (!requestPath.startsWith(servePathContext + "/")) {
                plugin.getLogger().warning("HTTP请求路径 '" + requestPath + "' 与服务上下文 '" + servePathContext + "/' 不匹配。");
                sendErrorResponse(exchange, 404, "404 (Not Found - Invalid Context)");
                return;
            }

            String fileName = requestPath.substring(servePathContext.length() + 1);

            if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                plugin.getLogger().warning("HTTP请求包含无效的文件名字符: " + fileName);
                sendErrorResponse(exchange, 400, "Bad Request: Invalid filename.");
                return;
            }

            File fileToServe = new File(baseServeDirectory, fileName);
            plugin.getLogger().fine("HTTP请求: " + requestPath + " -> 尝试服务文件: " + fileToServe.getAbsolutePath());

            if (fileToServe.exists() && fileToServe.isFile() && fileToServe.getParentFile().equals(baseServeDirectory)) {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, fileToServe.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(fileToServe.toPath(), os);
                }
                plugin.getLogger().info("成功服务文件: " + fileName + " (" + fileToServe.length() + " bytes) 给 " + exchange.getRemoteAddress());
            } else {
                plugin.getLogger().warning("HTTP服务：请求的文件未找到、为目录或在服务目录之外: " + fileToServe.getAbsolutePath());
                sendErrorResponse(exchange, 404, "404 (Not Found)");
            }
        }

        private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            byte[] responseBytes = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}