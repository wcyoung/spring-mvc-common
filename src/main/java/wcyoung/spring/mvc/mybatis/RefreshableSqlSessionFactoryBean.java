package wcyoung.spring.mvc.mybatis;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.Resource;

/**
 * mybatis mapper 자동 감지 후 자동으로 서버 재시작이 필요 없이 반영
 */
public class RefreshableSqlSessionFactoryBean extends SqlSessionFactoryBean implements DisposableBean {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private SqlSessionFactory proxy;
    private int interval = 500;

    private Timer timer;
    private TimerTask task;

    private Resource[] mapperLocations;

    /**
     * 파일 감시 쓰레드가 실행중인지 여부.
     */
    private boolean isRunning = false;

    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock readLock = rwl.readLock();
    private final Lock writeLock = rwl.writeLock();

    @Override
    public void setMapperLocations(Resource... mapperLocations) {
        super.setMapperLocations(mapperLocations);
        this.mapperLocations = mapperLocations;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public void refresh() throws Exception {
        log.info("refreshing sqlMapClient.");
        writeLock.lock();
        try {
            super.afterPropertiesSet();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 싱글톤 멤버로 SqlMapClient 원본 대신 프록시로 설정하도록 오버라이드.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        setRefreshable();
    }

    private void setRefreshable() {
        proxy = (SqlSessionFactory) Proxy.newProxyInstance(
                SqlSessionFactory.class.getClassLoader(),
                new Class[]{SqlSessionFactory.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        return method.invoke(getParentObject(), args);
                    }
                });

        task = new TimerTask() {
            private Map<Resource, Long> map = new HashMap<>();

            @Override
            public void run() {
                if (isModified()) {
                    try {
                        refresh();
                    } catch (Exception e) {
                        log.error("Exception: {}", ExceptionUtils.getStackTrace(e));
                    }
                }
            }

            private boolean isModified() {
                boolean retVal = false;

                if (mapperLocations != null) {
                    for (int i = 0; i < mapperLocations.length; i++) {
                        Resource mappingLocation = mapperLocations[i];
                        retVal |= findModifiedResource(mappingLocation);
                    }
                }

                return retVal;
            }

            private boolean findModifiedResource(Resource resource) {
                boolean retVal = false;
                List<String> modifiedResources = new ArrayList<>();

                try {
                    long modified = resource.lastModified();

                    if (map.containsKey(resource)) {
                        long lastModified = ((Long) map.get(resource)).longValue();

                        if (lastModified != modified) {
                            map.put(resource, new Long(modified));
                            modifiedResources.add(resource.getDescription());
                            retVal = true;
                        }
                    } else {
                        map.put(resource, new Long(modified));
                    }
                } catch (IOException e) {
                    log.error("Exception: {}", ExceptionUtils.getStackTrace(e));
                }

                if (retVal) {
                    log.info("modified files: {}", modifiedResources);
                }
                return retVal;
            }
        };

        timer = new Timer(true);
        resetInterval();
    }

    private Object getParentObject() throws Exception {
        readLock.lock();
        try {
            return super.getObject();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public SqlSessionFactory getObject() {
        return this.proxy;
    }

    @Override
    public Class<? extends SqlSessionFactory> getObjectType() {
        return (this.proxy != null ? this.proxy.getClass() : SqlSessionFactory.class);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setCheckInterval(int ms) {
        interval = ms;

        if (timer != null) {
            resetInterval();
        }
    }

    private void resetInterval() {
        if (isRunning) {
            timer.cancel();
            isRunning = false;
        }
        if (interval > 0) {
            timer.schedule(task, 0, interval);
            isRunning = true;
        }
    }

    @Override
    public void destroy() throws Exception {
        timer.cancel();
    }

}