package wcyoung.spring.mvc.common.security;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import wcyoung.spring.mvc.common.security.annotation.ApplyXssFilter;
import wcyoung.spring.mvc.common.security.annotation.ApplyXssFilterBeans;
import wcyoung.spring.mvc.common.security.annotation.IgnoreXssFilter;
import wcyoung.spring.mvc.common.security.annotation.IgnoreXssFilterField;

public abstract class AbstractResponseXssFilterAdvice implements ResponseBodyAdvice<Object> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected abstract boolean supportsMediaType(MediaType mediaType);

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType.hasMethodAnnotation(IgnoreXssFilter.class)) {
            log.debug("{} - @IgnoreXssFilter has been applied.", returnType.getMethod());
            return false;
        }

        if (returnType.getContainingClass().isAnnotationPresent(IgnoreXssFilter.class)
                && !returnType.hasMethodAnnotation(ApplyXssFilter.class)) {
            log.debug("{} - @IgnoreXssFilter has been applied.", returnType.getContainingClass());
            return false;
        }

        log.trace("{} is filtered.", returnType.getMethod());
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        if (!supportsMediaType(selectedContentType)) {
            log.debug("{} type is not supported.", selectedContentType);
            return body;
        }

        String[] ignoreKeys = new String[] {};

        ApplyXssFilter applyXssFilter = returnType.getMethodAnnotation(ApplyXssFilter.class);
        if (applyXssFilter != null) {
            ignoreKeys = (applyXssFilter.ignoreKeys() == null) ? ignoreKeys : applyXssFilter.ignoreKeys();
        };

        if (body instanceof String) {
            if (ignoreKeys.length > 0) {
                throw new IllegalArgumentException(returnType.getMethod()
                        + " - @ApplyXssFilter.ignoreKeys only applies when response body type is Map.");
            }
            return filter((String) body);
        }

        if (body instanceof Map) {
            if (ignoreKeys.length > 0) {
                log.debug("{} - ignoreKeys{} has been applied.", returnType.getMethod(), ignoreKeys);
            }
            return filter((Map<String, Object>) body, ignoreKeys);
        }

        if (body instanceof List) {
            if (ignoreKeys.length > 0) {
                throw new IllegalArgumentException(returnType.getMethod()
                        + " - @ApplyXssFilter.ignoreKeys only applies when response body type is Map.");
            }
            return filter((List<Object>) body, ignoreKeys);
        }

        if (ignoreKeys.length > 0) {
            throw new IllegalArgumentException(returnType.getMethod()
                    + " - @ApplyXssFilter.ignoreKeys only applies when response body type is Map.");
        }
        return filter(body, ignoreKeys);
    }

    protected String filter(String value) {
        if (value == null) {
            return value;
        }

        value = value.replace("<", "&lt;").replace(">", "&gt;");
        value = value.replace("'", "&#39;").replace("\"", "&quot;");
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> filter(Map<String, Object> map, String[] ignoreKeys) {
        if (map == null) {
            return map;
        }

        for (Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.equalsAny(key, ignoreKeys)) {
                continue;
            }

            Object value = entry.getValue();

            if (value instanceof String) {
                map.put(key, filter((String) value));
            } else if (value instanceof Map) {
                map.put(key, filter((Map<String, Object>) value, ignoreKeys));
            } else if (value instanceof List) {
                map.put(key, filter((List<Object>) value, ignoreKeys));
            } else {
                map.put(key, filter(value, ignoreKeys));
            }
        }

        return map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> filter(List<Object> list, String[] ignoreKeys) {
        if (list == null) {
            return list;
        }

        for (int i = 0, length = list.size(); i < length; i++) {
            Object value = list.get(i);

            if (value instanceof String) {
                list.set(i, filter((String) value));
            } else if (value instanceof Map) {
                list.set(i, filter((Map<String, Object>) value, ignoreKeys));
            } else if (value instanceof List) {
                list.set(i, filter((List<Object>) value, ignoreKeys));
            } else {
                list.set(length, filter(value, ignoreKeys));
            }
        }

        return list;
    }

    @SuppressWarnings("unchecked")
    private Object filter(Object object, String[] ignoreKeys) {
        if (object == null) {
            return object;
        }

        Class<?> clazz = ClassUtils.getUserClass(object);
        ApplyXssFilterBeans applyXssFilterBeans = clazz.getAnnotation(ApplyXssFilterBeans.class);

        if (applyXssFilterBeans == null) {
            return object;
        }

        List<Field> fields = FieldUtils.getAllFieldsList(clazz);

        for (int i = 0, length = fields.size(); i < length; i++) {
            Field field = fields.get(i);
            if (field.getAnnotation(IgnoreXssFilterField.class) != null) {
                continue;
            }

            try {
                if (field.getType().equals(String.class)) {
                    String value = (String) FieldUtils.readField(field, object, true);
                    FieldUtils.writeField(field, object, filter(value));
                } else if (field.getType().equals(Map.class)) {
                    Map<String, Object> value = (Map<String, Object>) FieldUtils.readField(field, object, true);
                    FieldUtils.writeField(field, object, filter(value, ignoreKeys));
                } else if (field.getType().equals(List.class)) {
                    List<Object> value = (List<Object>) FieldUtils.readField(field, object, true);
                    FieldUtils.writeField(field, object, filter(value, ignoreKeys));
                } else {
                    Object value = FieldUtils.readField(field, object, true);
                    FieldUtils.writeField(field, object, filter(value, ignoreKeys));
                }
            } catch (Exception e) {
                log.error("Exception: {}", ExceptionUtils.getStackTrace(e));
            }
        }

        return object;
    }

}
