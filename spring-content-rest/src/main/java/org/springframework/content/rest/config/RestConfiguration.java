package org.springframework.content.rest.config;

import internal.org.springframework.content.rest.controllers.ContentServiceHandlerMethodArgumentResolver;
import internal.org.springframework.content.rest.controllers.ResourceETagMethodArgumentResolver;
import internal.org.springframework.content.rest.controllers.ResourceHandlerMethodArgumentResolver;
import internal.org.springframework.content.rest.controllers.ResourceTypeMethodArgumentResolver;
import internal.org.springframework.content.rest.mappings.ContentHandlerMapping;
import internal.org.springframework.content.rest.mappings.StoreByteRangeHttpRequestHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.commons.storeservice.StoreResolver;
import org.springframework.content.commons.storeservice.Stores;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.repository.support.DefaultRepositoryInvokerFactory;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Configuration
@ComponentScan("internal.org.springframework.content.rest.controllers, org.springframework.data.rest.extensions, org.springframework.data.rest.versioning")
public class RestConfiguration implements InitializingBean {

	private static final URI NO_URI = URI.create("");

	@Autowired
	Stores stores;

	@Autowired(required = false)
	private List<ContentRestConfigurer> configurers = new ArrayList<>();

	private URI baseUri = NO_URI;
	private StoreCorsRegistry corsRegistry;
	private boolean fullyQualifiedLinks = true;

	private Map<Class<?>, DomainTypeConfig> domainTypeConfigMap = new HashMap<>();

	public RestConfiguration() {
		this.corsRegistry = new StoreCorsRegistry();
	}

	public URI getBaseUri() {
		return baseUri;
	}

	public void setBaseUri(URI baseUri) {
		this.baseUri = baseUri;
	}

	public boolean fullyQualifiedLinks() {
		return fullyQualifiedLinks;
	}

	public void setFullyQualifiedLinks(boolean fullyQualifiedLinks) {
		this.fullyQualifiedLinks = fullyQualifiedLinks;
	}

	public StoreCorsRegistry getCorsRegistry() {
		return corsRegistry;
	}

	public void addStoreResolver(String name, StoreResolver resolver) {
		stores.addStoreResolver(name, resolver);
	}

	public DomainTypeConfig forDomainType(Class<?> type) {
		DomainTypeConfig config = domainTypeConfigMap.get(type);
		if (config  == null) {
			config = new DomainTypeConfig();
			domainTypeConfigMap.put(type, config);
		}
		return config;
	}

	@Bean
	RequestMappingHandlerMapping contentHandlerMapping() {
		ContentHandlerMapping mapping = new ContentHandlerMapping(stores, this);
		mapping.setCorsConfigurations(this.getCorsRegistry().getCorsConfigurations());
		return mapping;
	}

	@Bean
	StoreByteRangeHttpRequestHandler byteRangeRestRequestHandler() {
		return new StoreByteRangeHttpRequestHandler();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		for (ContentRestConfigurer configurer : configurers) {
			configurer.configure(this);
		}
	}

	@Configuration
	public static class WebConfig implements WebMvcConfigurer, InitializingBean {

		@Autowired
		private RestConfiguration config;

		@Autowired
		private ApplicationContext context;

		@Autowired(required = false)
		private Repositories repositories;

		@Autowired(required = false)
		private RepositoryInvokerFactory repoInvokerFactory;

		@Autowired
		private Stores stores;

		@Override
		public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {

			argumentResolvers.add(new ResourceHandlerMethodArgumentResolver(config, repositories, repoInvokerFactory, stores));
			argumentResolvers.add(new ResourceTypeMethodArgumentResolver(config, repositories, repoInvokerFactory, stores));
			argumentResolvers.add(new ResourceETagMethodArgumentResolver(config, repositories, repoInvokerFactory, stores));
			argumentResolvers.add(new ContentServiceHandlerMethodArgumentResolver(config, repositories, repoInvokerFactory, stores));
		}

		@Override
		public void afterPropertiesSet() throws Exception {

			if (repositories == null) {
				repositories = new Repositories(context);
			}

			if (repoInvokerFactory == null) {
				repoInvokerFactory = new DefaultRepositoryInvokerFactory(repositories);
			}
		}
	}

	public class DomainTypeConfig {

        private Resolver<Method, HttpHeaders> setContentResolver = new Resolver<Method, HttpHeaders>(){

            @Override
            public boolean resolve(Method method, HttpHeaders context) {
                return preferInputStream(method);
            }
        };

		public DomainTypeConfig(){}

		public Resolver<Method, HttpHeaders> getSetContentResolver() {
			return setContentResolver;
		}

        public void setSetContentResolver(Resolver<Method, HttpHeaders> resolver) {
            this.setContentResolver = resolver;
        }

        public void putAndPostPreferResource() {
			setContentResolver = new Resolver<Method, HttpHeaders>(){

	            @Override
	            public boolean resolve(Method method, HttpHeaders context) {
	                return preferResource(method);
	            }
	        };
		}

		/* package */ boolean preferResource(Method method) {
			if (Resource.class.equals(method.getParameterTypes()[1])) {
				return true;
			}
			return false;
		}

		/* package */ boolean preferInputStream(Method method) {
			if (InputStream.class.equals(method.getParameterTypes()[1])) {
				return true;
			}
			return false;
		}
	}

	public interface Resolver<S, C> {

	    boolean resolve(S subject, C context);
	}
}
