package edu.escuelaing.arep;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Application to consult information about movies.
 *
 * @author Jefer Alexis Gonzalez Romero
 * @version 1.0
 * @since 2024-01-28
 */
public class MovieInfoServer {

    private static final Logger LOGGER = Logger.getLogger(MovieInfoServer.class.getName());
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static final MovieDataProvider movieDataProvider = new OMDbMovieDataProvider();

    /**
     * Start the server and start listening to client requests.
     *
     * @param args They are not used.
     */
    public static void main(String[] args) throws URISyntaxException {
        try (ServerSocket serverSocket = new ServerSocket(35000)) {
            while (true) {
                LOGGER.info("Listo para recibir ...");
                handleClientRequest(serverSocket.accept());
            }
        } catch (IOException e) {
            LOGGER.info("Could not listen on port: 35000.");
            System.exit(1);
        }
    }

    /**
     * Handles a single client request.
     *
     * @param clientSocket The Socket object representing the client connection.
     */
    public static void handleClientRequest(Socket clientSocket) throws URISyntaxException {
        try (
                OutputStream outputStream = clientSocket.getOutputStream();
                PrintWriter out = new PrintWriter(outputStream, true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        ) {
            String requestLine = in.readLine();
            LOGGER.log(Level.INFO, "Received:{0}", requestLine);
            if (requestLine != null) {
                String uriStr = requestLine.split(" ")[1];
                URI fileUrl = new URI(uriStr);
                String path = fileUrl.getPath();
                LOGGER.log(Level.INFO, "Path: {0}", path);
                String contentType = contentType(path);
                String outputLine;
                if (path.startsWith("/movies")) {
                    outputLine = handleTitleValue(parseTitleFromRequest(requestLine));
                    out.println(outputLine);
                } else {
                    if (contentType.contains("image")) {
                        outputStream.write(httpHeader(path).toString().getBytes());
                        outputStream.write(httpClientImage(path));
                    } else {
                        outputLine = httpClientFiles(path);
                        out.println(outputLine);
                    }
                }
            }
            clientSocket.close();
        } catch (IOException e) {
            LOGGER.info("Accept failed.");
            System.exit(1);
        }
    }

    /**
     * Handles the provided title value, either fetching movie data or generating default HTML.
     *
     * @param titleValue The title value extracted from the client request.
     * @return The response to send back to the client, either the fetched movie data in HTML format or default HTML content.
     */
    public static String handleTitleValue(String titleValue) {
        return "HTTP/1.1 200 OK\r\n" +
                "Content-Type:application/json; charset=ISO-8859-1\r\n" +
                "\r\n" +
                CACHE.computeIfAbsent(titleValue, movieDataProvider::fetchMovieData);
    }

    /**
     * Retrieves a specified file from the "target/classes/public" directory and constructs an HTTP response.
     *
     * @param path The path to the file, including its extension.
     * @return A string containing the HTTP response, including headers and file content.
     */
    public static String httpClientFiles(String path) {
        StringBuilder outputLine = httpHeader(path);
        Path file = Paths.get("target/classes/public" + path);
        Charset charset = StandardCharsets.UTF_8;
        try (BufferedReader reader = Files.newBufferedReader(file, charset)) {
            String line;
            while ((line = reader.readLine()) != null) outputLine.append(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputLine.toString();
    }

    /**
     * Retrieves a specified image from the "target/classes/public" directory and constructs an HTTP response.
     *
     * @param path The path to the file, including its extension.
     * @return A string containing the HTTP response, including headers and file content.
     */
    public static byte[] httpClientImage(String path) {
        Path file = Paths.get("target/classes/public" + path);
        byte[] imageData = null;
        try {
            imageData = Files.readAllBytes(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageData;
    }

    /**
     * Constructs the HTTP response header based on the given file extension.
     *
     * @param path The path to the file, including its extension.
     * @return A StringBuilder containing the HTTP response header.
     */
    public static StringBuilder httpHeader(String path) {
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 200 OK\r\n");
        header.append("Content-Type:");
        header.append(contentType(path));
        header.append("\r\n");
        header.append("\r\n");
        return header;
    }

    /**
     * Determines the content type of file based on its path.
     *
     * @param path The path to the file.
     * @return The content type of the file, or an empty string if the content type could not be determined.
     */
    public static String contentType(String path) {
        File file = new File(path);
        String contenType = "";
        try {
            contenType = Files.probeContentType(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contenType;
    }

    /**
     * Parses the title value from the given request line.
     *
     * @param path Request path.
     * @return The title value extracted from the query string, or null if not found.
     */
    public static String parseTitleFromRequest(String path) {
        if ((path.startsWith("GET") || path.startsWith("POST")) && path.contains("?")) {
            String queryString = path.split(" ")[1].split("\\?")[1];
            Map<String, String> params = parseParams(queryString);
            return params.get("title");
        } else {
            return null;
        }
    }

    /**
     * Parses query parameters from the given query string.
     *
     * @param queryString The query string containing key-value pairs.
     * @return A Map containing the parsed parameters, where keys are parameter names and values are parameter values.
     */
    public static Map<String, String> parseParams(String queryString) {
        Map<String, String> params = new HashMap<>();
        for (String param : queryString.split("&")) {
            String[] nameValue = param.split("=");
            params.put(nameValue[0], nameValue[1]);
        }
        return params;
    }
}