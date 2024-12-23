import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

public class ProxyCache {

    private static final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private static int proxyPort;
    private static int xamppPort;
    private static List<CacheRule> cacheRules = new ArrayList<>();

    public static void main(String[] args) {
        loadConfig();
        new Thread(ProxyCache::startProxyServer).start();
        startCliInterface();
    }

    private static void startProxyServer() {
        try (ServerSocket serverSocket = new ServerSocket(proxyPort)) {
            System.out.println("Proxy cache démarré sur le port " + proxyPort + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du démarrage du serveur proxy : " + e.getMessage());
        }
    }

    private static void startCliInterface() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nMenu Cache Proxy:");
            System.out.println("1. Vider la cache");
            System.out.println("2. Afficher le cache");
            System.out.println("3. Quitter");
            System.out.print("Choisir une option (1-3): ");
            int choice = scanner.nextInt();
            scanner.nextLine();
            switch (choice) {
                case 1:
                  RemoveCache();
                    break;
                case 2:
                    displayCache();
                    break;
                case 3:
                    System.out.println("Au revoir!");
                    scanner.close();
                    return;
                default:
                    System.out.println("Option invalide. Essayez encore.");
                    break;
            }
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))
            ) {
                String requestLine = in.readLine();
                if (requestLine != null && requestLine.startsWith("GET")) {
                    System.out.println("requete : " + requestLine);
                    String url = requestLine.split(" ")[1];
                    if (cache.containsKey(url)) {
                        sendResponse(out, cache.get(url).getContent());
                    } else {
                        String response = getFromXampp(url);
                        cacheElement(url, response);
                        sendResponse(out, response);
                    }
                } else if (requestLine != null && requestLine.startsWith("CONNECT")) {
                } else {
                    sendResponse(out, "HTTP/1.1 405 Method Not Allowed\r\n\r\n");
                }
            } catch (IOException e) {
                System.err.println("Erreur lors du traitement de la requête : " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Erreur lors de la fermeture du socket : " + e.getMessage());
                }
            }
        }

        private String getFromXampp(String url) {
            try (
                Socket xamppSocket = new Socket("localhost", xamppPort);
                BufferedWriter xamppOut = new BufferedWriter(new OutputStreamWriter(xamppSocket.getOutputStream()));
                BufferedReader xamppIn = new BufferedReader(new InputStreamReader(xamppSocket.getInputStream()))
            ) {
                xamppOut.write("GET " + url + " HTTP/1.1\r\n");
                xamppOut.write("Host: localhost\r\n");
                xamppOut.write("Connection: close\r\n\r\n");
                xamppOut.flush();
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = xamppIn.readLine()) != null) {
                    response.append(line).append("\r\n");
                }
                return response.toString();
            } catch (IOException e) {
                return "HTTP/1.1 500 Internal Server Error\r\n\r\nErreur lors de la récupération du fichier.";
            }
        }

        private void sendResponse(BufferedWriter out, String response) throws IOException {
            out.write(response);
            out.flush();
        }
    }

    private static void loadConfig() {
        try {
            File configFile = new File("config.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(configFile);
            proxyPort = Integer.parseInt(doc.getElementsByTagName("proxyPort").item(0).getTextContent());
            xamppPort = Integer.parseInt(doc.getElementsByTagName("xamppPort").item(0).getTextContent());
            NodeList cacheDurationNodes = doc.getElementsByTagName("cacheDuration");
            for (int i = 0; i < cacheDurationNodes.getLength(); i++) {
                Element element = (Element) cacheDurationNodes.item(i);
                String size = element.getAttribute("size");
                int duration = Integer.parseInt(element.getAttribute("duration"));
                cacheRules.add(new CacheRule(size.equals("max") ? Integer.MAX_VALUE : Integer.parseInt(size), duration));
            }
            cacheRules.sort(Comparator.comparingInt(rule -> rule.size));
        } catch (Exception e) {
            proxyPort = 6969;
            xamppPort = 80;
        }
    }

    private static void cacheElement(String url, String response) {
        int responseSize = response.length();
        int duration = calculateCacheDuration(responseSize);
        CacheEntry entry = new CacheEntry(response, System.currentTimeMillis() + duration * 1000L);
        cache.put(url, entry);
        scheduler.schedule(() -> cache.remove(url), duration, TimeUnit.SECONDS);
    }

    private static int calculateCacheDuration(int size) {
        for (CacheRule rule : cacheRules) {
            if (size <= rule.size) {
                return rule.duration;
            }
        }
        return 60;
    }


    private static void RemoveCache() {
        if(cache.isEmpty()) {
            System.out.println("La cache est deja vide");
        } else {
            cache.clear();
            System.out.println("Cache videe manuellement");
        }
    }


    private static void displayCache() {
        if (cache.isEmpty()) {
            System.out.println("Le cache est vide.");
        } else {
            System.out.println("Contenu du cache : ");
            cache.forEach((url, entry) -> System.out.println("URL : " + url));
        }
    }

    static class CacheEntry {
        private final String content;
        @SuppressWarnings("unused")
        private final long expiryTime;

        public CacheEntry(String content, long expiryTime) {
            this.content = content;
            this.expiryTime = expiryTime;
        }

        public String getContent() {
            return content;
        }
    }

    static class CacheRule {
        private final int size;
        private final int duration;

        public CacheRule(int size, int duration) {
            this.size = size;
            this.duration = duration;
        }
    }
}
