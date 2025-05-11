package eogd.musicplayer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

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
    private final String contextPath;
    private final File serveDirectory;

    public HttpFileServer(MusicPlayerPlugin plugin, String contextPath, File serveDirectory) {
        this.plugin = plugin;
        this.contextPath = "/" + contextPath.replaceFirst("^/", "");
        this.serveDirectory = serveDirectory;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(this.contextPath, new FileHandler(this.serveDirectory, this.plugin));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("HTTP File Server started on port " + port + " serving " + this.contextPath);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not start HTTP File Server on port " + port, e);
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("HTTP File Server stopped.");
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public String getServePath() {
        return contextPath.substring(1);
    }

    public String getFileUrl(@NotNull String publicAddress, int port, @NotNull String fileName) {
        if (publicAddress.isEmpty()) {
            plugin.getLogger().warning("Public address for HTTP server is not configured! URLs will be incomplete.");
            return "http://<YOUR_SERVER_IP_OR_DOMAIN>:" + port + this.contextPath + "/" + fileName;
        }
        return "http://" + publicAddress + ":" + port + this.contextPath + "/" + fileName;
    }


    private static class FileHandler implements HttpHandler {
        private final File baseDir;
        private final MusicPlayerPlugin pluginInstance;


        public FileHandler(File baseDir, MusicPlayerPlugin plugin) {
            this.baseDir = baseDir;
            this.pluginInstance = plugin;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            String relativePath = requestPath.substring(pluginInstance.getHttpFileServer().contextPath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            File fileToServe = new File(baseDir, relativePath);
            pluginInstance.getLogger().fine("HTTP Request: " + requestPath + " -> Trying to serve: " + fileToServe.getAbsolutePath());

            if (fileToServe.exists() && !fileToServe.isDirectory() && isSafePath(baseDir, fileToServe)) {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, fileToServe.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(fileToServe.toPath(), os);
                }
            } else {
                pluginInstance.getLogger().warning("HTTP File not found or unsafe: " + fileToServe.getAbsolutePath() + " (Requested: " + requestPath + ")");
                String response = "404 (Not Found)\n";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }

        private boolean isSafePath(File base, File potentiallyOutside) {
            try {
                String basePath = base.getCanonicalPath();
                String potentialPath = potentiallyOutside.getCanonicalPath();
                return potentialPath.startsWith(basePath);
            } catch (IOException e) {
                return false;
            }
        }
    }
}