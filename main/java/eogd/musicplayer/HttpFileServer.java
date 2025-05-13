package eogd.musicplayer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public class HttpFileServer {

    private final MusicPlayerPlugin plugin;
    private HttpServer server;
    private final String servePathPrefix;
    private final File serveDirectory;

    public HttpFileServer(MusicPlayerPlugin plugin, String servePathPrefixFromConfig, File serveDirectory) {
        this.plugin = plugin;
        this.servePathPrefix = servePathPrefixFromConfig;
        this.serveDirectory = serveDirectory;
    }

    public void start(int port) {
        if (!serveDirectory.exists()) {
            plugin.getLogger().severe("HTTP 服务目录不存在: " + serveDirectory.getAbsolutePath());
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            String contextPath = "/" + servePathPrefix.replaceAll("^/|/$", "");
            server.createContext(contextPath, new FileHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("HTTP 文件服务器已在端口 " + port + " 启动，上下文路径: " + contextPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "启动 HTTP 文件服务器失败", e);
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("HTTP 文件服务器已停止。");
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String getFileUrl(String publicAddress, int port, String fileName) {
        if (publicAddress == null || publicAddress.isEmpty()) {
            publicAddress = Bukkit.getIp().isEmpty() ? "localhost" : Bukkit.getIp();
            plugin.getLogger().warning("HTTP publicAddress 未配置，URL 可能无法从外部访问。使用: " + publicAddress);
        }
        String urlContextPath = "/" + servePathPrefix.replaceAll("^/|/$", "") + "/";
        String cleanFileName = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        return "http://" + publicAddress + ":" + port + urlContextPath + cleanFileName;
    }

    public File getServeDirectory() {
        return serveDirectory;
    }

    public String getServePathPrefix() {
        return servePathPrefix;
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            String expectedContextPath = "/" + servePathPrefix.replaceAll("^/|/$", "");

            if (!requestPath.startsWith(expectedContextPath + "/")) {
                sendError(exchange, 404, "Not Found (Invalid Path Prefix)");
                return;
            }

            String requestedFileName = requestPath.substring((expectedContextPath + "/").length());
            File fileToServe = new File(serveDirectory, requestedFileName);

            if (!fileToServe.exists() || !fileToServe.isFile() || !isWithinServeDirectory(fileToServe)) {
                plugin.getLogger().warning("HTTP请求的文件不存在或无效: " + fileToServe.getAbsolutePath() + " (请求路径: " + requestPath + ")");
                sendError(exchange, 404, "File Not Found");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.sendResponseHeaders(200, fileToServe.length());
            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(fileToServe.toPath(), os);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "发送文件时出错: " + fileToServe.getName(), e);
            } finally {
                exchange.close();
            }
        }

        private boolean isWithinServeDirectory(File file) {
            try {
                return file.getCanonicalPath().startsWith(serveDirectory.getCanonicalPath());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "检查文件路径时出错: " + file.getPath(), e);
                return false;
            }
        }

        private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
            exchange.sendResponseHeaders(statusCode, message.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(message.getBytes());
            }
            exchange.close();
        }
    }
}