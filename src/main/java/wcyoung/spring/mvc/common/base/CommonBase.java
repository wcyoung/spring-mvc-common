package wcyoung.spring.mvc.common.base;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wcyoung.spring.mvc.common.property.ApplicationProperties;

public abstract class CommonBase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    private ApplicationProperties applicationProperties;

    protected String getProperty(String key) {
        return applicationProperties.getProperty(key);
    }

    protected String getProperty(String key, String defaultValue) {
        return applicationProperties.getProperty(key, defaultValue);
    }

}
