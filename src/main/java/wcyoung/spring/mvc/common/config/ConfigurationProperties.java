package wcyoung.spring.mvc.common.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
            String filePath = "";
            if (argumentFilePath != null && argumentFilePath.trim().length() != 0) {
                filePath = argumentFilePath;
                inputStream = new FileInputStream(argumentFilePath);
            } else {
                filePath = "classpath:" + defaultFilePath;
                inputStream = getClass().getClassLoader().getResourceAsStream(defaultFilePath);
            }

            if (inputStream == null) {
                throw new FileNotFoundException("'" + filePath + "' is not found.");
            }

            log.info("============================================================");
            log.info("configuration file: [{}]", filePath);
            log.info("============================================================");

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
