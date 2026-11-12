package pl.edu.bidroom.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {
    @TempDir Path temporary;
    @Test void configurationRequiresOptInAndEnvironmentOverridesFile() throws Exception {
        Path file=temporary.resolve("configuration.json");
        Files.writeString(file,"{\"BIDROOM_PORT\":\"5012\",\"BIDROOM_PROFILE\":\"local\",\"PATH\":\"untrusted\"}");
        assertEquals(Map.of("BIDROOM_PORT","5013"),ServerConfig.configuration(null,Map.of("BIDROOM_PORT","5013")));
        var result=ServerConfig.configuration(file.toString(),Map.of("BIDROOM_PORT","5013"));
        assertEquals("5013",result.get("BIDROOM_PORT"));
        assertEquals("local",result.get("BIDROOM_PROFILE"));
        assertFalse(result.containsKey("PATH"));
    }
    @Test void malformedConfigurationDoesNotDiscloseItsContents() throws Exception {
        Path file=temporary.resolve("broken.json");Files.writeString(file,"{private-secret-example");
        var failure=assertThrows(IllegalArgumentException.class,()->ServerConfig.configuration(file.toString(),Map.of()));
        assertFalse(failure.getMessage().contains("private-secret-example"));assertNull(failure.getCause());
    }
}
