package wcyoung.spring.mvc.common.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationProperties {

    private Logger log = LoggerFactory.getLogger(getClass());

    private Properties properties;

    public ConfigurationProperties(String defaultFilePath) {
        this("application.configurationFile", defaultFilePath);
    }

    public ConfigurationProperties(String argumentKey, String defaultFilePath) {
        try {
            InputStream inputStream = null;

            String argumentFilePath = System.getProperty(argumentKey);
            if (argumentFilePath != null && argumentFilePath.trim().length() != 0) {
                inputStream = new FileInputStream(argumentFilePath);
            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream(defaultFilePath);
            }

            properties = new Properties();
            properties.load(inputStream);
            inputStream.close();
        } catch (Exception e) {
            log.error("Exception: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

}
