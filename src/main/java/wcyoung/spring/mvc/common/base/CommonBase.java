package wcyoung.spring.mvc.common.base;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wcyoung.spring.mvc.common.config.ConfigurationProperties;

public abstract class CommonBase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    private ConfigurationProperties configurationProperties;

    protected String getProperty(String key) {
        return configurationProperties.getProperty(key);
    }

    protected String getProperty(String key, String defaultValue) {
        return configurationProperties.getProperty(key, defaultValue);
    }

}
