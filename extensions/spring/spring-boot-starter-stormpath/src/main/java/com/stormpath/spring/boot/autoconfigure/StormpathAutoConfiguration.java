/*
 * Copyright 2015 Stormpath, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stormpath.spring.boot.autoconfigure;

import com.stormpath.sdk.api.ApiKey;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.cache.Caches;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.client.ClientBuilder;
import com.stormpath.sdk.client.Clients;
import com.stormpath.sdk.client.Proxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("SpringFacetCodeInspection")
@Configuration
@EnableConfigurationProperties({ StormpathApplicationProperties.class, StormpathClientApiKeyProperties.class,
                                   StormpathClientAuthenticationProperties.class,
                                   StormpathClientProxyProperties.class })
public class StormpathAutoConfiguration {

    @Autowired
    protected StormpathClientApiKeyProperties apiKeyProperties;

    @Autowired
    protected StormpathApplicationProperties applicationProperties;

    @Bean
    @ConditionalOnMissingBean(name = "stormpathClientApiKey")
    public ApiKey stormpathClientApiKey() {
        return apiKeyProperties.resolveApiKey();
    }

    @Bean
    @ConditionalOnMissingBean(name="stormpathApplication")
    public Application stormpathApplication(Client client) {
        return applicationProperties.resolveApplication(client);
    }

    protected static abstract class AbstractClientConfiguration {

        @Autowired
        protected StormpathClientAuthenticationProperties authenticationProperties;

        @Autowired
        protected StormpathClientProxyProperties proxyProperties;

        @Autowired
        @Qualifier("stormpathClientApiKey")
        protected ApiKey apiKey;

        protected ClientBuilder newClientBuilder() {

            ClientBuilder builder = Clients.builder().setAuthenticationScheme(authenticationProperties.getScheme());

            builder.setApiKey(apiKey);

            Proxy proxy = proxyProperties.resolveProxy();
            if (proxy != null) {
                builder.setProxy(proxy);
            }

            return builder;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @Configuration
    @ConditionalOnMissingBean(type={"org.springframework.cache.CacheManager", "com.stormpath.sdk.cache.CacheManager"})
    protected static class DefaultStormpathClientConfiguration extends AbstractClientConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public Client stormpathClient() {
            return newClientBuilder().setCacheManager(Caches.newCacheManager().build()).build();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @Configuration
    @ConditionalOnBean(com.stormpath.sdk.cache.CacheManager.class)
    protected static class CachingStormpathClientConfiguration extends AbstractClientConfiguration {

        @Autowired
        private com.stormpath.sdk.cache.CacheManager cacheManager;

        @Bean
        @ConditionalOnMissingBean
        public Client stormpathClient() {
            return newClientBuilder().setCacheManager(cacheManager).build();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @Configuration
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(com.stormpath.sdk.cache.CacheManager.class)
    protected static class SpringCachingStormpathClientConfiguration extends AbstractClientConfiguration {

        @Autowired
        private CacheManager cacheManager;

        @Bean
        @ConditionalOnMissingBean
        public Client stormpathClient() {
            return newClientBuilder().setCacheManager(new SpringStormpathCacheManager(cacheManager)).build();
        }

    }

    @SuppressWarnings("unchecked")
    protected static class SpringStormpathCacheManager implements com.stormpath.sdk.cache.CacheManager {

        private CacheManager springCacheManager;

        public SpringStormpathCacheManager(CacheManager springCacheManager) {
            this.springCacheManager = springCacheManager;
        }

        @Override
        public <K, V> com.stormpath.sdk.cache.Cache<K, V> getCache(String name) {

            final Cache cache = this.springCacheManager.getCache(name);

            return new com.stormpath.sdk.cache.Cache<K,V>() {
                @Override
                public V get(K key) {
                    Cache.ValueWrapper vw = cache.get(key);
                    if (vw == null) {
                        return null;
                    }
                    return (V)vw.get();
                }

                @Override
                public V put(K key, V value) {
                    Cache.ValueWrapper vw = cache.putIfAbsent(key, value);
                    if (vw == null) {
                        return null;
                    }
                    return (V)vw.get();
                }

                @Override
                public V remove(K key) {
                    V v = get(key);
                    cache.evict(key);
                    return v;
                }
            };
        }
    }
}
