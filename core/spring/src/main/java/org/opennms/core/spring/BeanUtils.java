/*
 * Licensed to The OpenNMS Group, Inc (TOG) under one or more
 * contributor license agreements.  See the LICENSE.md file
 * distributed with this work for additional information
 * regarding copyright ownership.
 *
 * TOG licenses this file to You under the GNU Affero General
 * Public License Version 3 (the "License") or (at your option)
 * any later version.  You may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at:
 *
 *      https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package org.opennms.core.spring;

import static org.springframework.util.Assert.notNull;

import java.lang.reflect.Field;

import javax.annotation.Resource;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.access.BeanFactoryLocator;
import org.springframework.beans.factory.access.BeanFactoryReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.access.DefaultLocatorFactory;

/**
 * Helper methods for working with Spring beans.
 *
 * @author <a href="mailto:dj@opennms.org">DJ Gregor</a>
 * @version $Id: $
 */
public class BeanUtils implements ApplicationContextAware {

    public static final Logger LOG = LoggerFactory.getLogger(BeanUtils.class);

    private static ApplicationContext m_context;

    @Override
    public void setApplicationContext(ApplicationContext context) {
        setStaticApplicationContext(context);
    }

    public static void setStaticApplicationContext(ApplicationContext context) {
        m_context = context;
    }

    /**
     * Get a Spring BeanFactory by context ID.
     *
     * @param contextId the context ID of the BeanFactory to fetch
     * @return the BeanFactory
     */
    public static BeanFactoryReference getBeanFactory(String contextId) {
        // If no ApplicationContext has been injected by an existing Spring
        // context, then use DefaultLocatorFactory to find or create the context
        if (m_context == null) {
            BeanFactoryLocator beanFactoryLoader = DefaultLocatorFactory.getInstance();
            return beanFactoryLoader.useBeanFactory(contextId);
        } else {
            return new BeanFactoryReference() {
                @Override
                public BeanFactory getFactory() {
                    return m_context;
                }

                @Override
                public void release() {}
            };
        }
    }

    /**
     * Get a Spring bean by name.  Uses Java 5 generics to cast the returned
     * class to the appropriate type.
     *
     * @param <T> class for the returned bean
     * @param beanFactory bean factory to use to fetch this bean
     * @param beanId ID of the bean to fetch
     * @param clazz class representing the type for the returned bean
     * @return bean for given bean ID casted to the appropriate class
     */
    public static <T> T getBean(BeanFactoryReference beanFactory, String beanId, Class<T> clazz) {
        return clazz.cast(beanFactory.getFactory().getBean(beanId));
    }
    
    /**
     * Helper method that combines getBeanFactory and getBean.
     *
     * @param <T> class for the returned bean
     * @param contextId the context ID of the BeanFactory from which to fetch
     *      this bean
     * @param beanId ID of the bean to fetch
     * @param clazz class representing the type for the returned bean
     * @return bean for given bean ID casted to the appropriate class
     */
    public static <T> T getBean(String contextId, String beanId, Class<T> clazz) {
        return getBean(getBeanFactory(contextId), beanId, clazz);
    }
    
    /**
     * Helper method that calls getBeanFactory(contextId).getFactory()
     * and casts the result.
     *
     * @param <T> class for the returned factory
     * @param contextId the context ID of the BeanFactory to fetch
     * @param clazz class representing the type for the returned factory
     * @return the factory casted to &lt;T&gt;
     */
    public static <T> T getFactory(String contextId, Class<T> clazz) {
        return clazz.cast(getBeanFactory(contextId).getFactory());
    }

    /**
     * Check that all fields that are marked with @Autowired are not null.
     * This will identify classes that have been loaded by Spring but have
     * not been autowired via {@code <context:annotation-config />}.
     */
    public static <T> void assertAutowiring(T instance) {
        for (Field field : instance.getClass().getDeclaredFields()) {
            Autowired autowired = field.getAnnotation(Autowired.class);
            Inject inject = field.getAnnotation(Inject.class);
            Resource resource = field.getAnnotation(Resource.class);
            if (
                (autowired != null && autowired.required()) ||
                (inject != null) ||
                (resource != null)
            ) {
                try {
                    field.setAccessible(true);
                    notNull(field.get(instance), "@Autowired/@Inject/@Resource field " + field.getName() + " cannot be null");
                    LOG.debug("{} is not null", field.getName());
                } catch (IllegalAccessException e) {
                    throw new IllegalArgumentException("Illegal access to @Autowired/@Resource field " + field.getName());
                }
            }
        }
    }
}
