import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private static final String CONFIG_FILE = "config.xml";
    private Map<String, String> config = new HashMap<>();

    public ConfigLoader() {
        loadConfig();
    }
    
    private void loadConfig() {
        try {
            File file = new File(CONFIG_FILE);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            NodeList nodes = doc.getElementsByTagName("*");
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    config.put(node.getNodeName(), node.getTextContent());
                }
            }
        } catch (Exception e) {
            System.out.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
    }

    public String get(String key) {
        return config.get(key);
    }
}
