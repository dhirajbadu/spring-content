package org.springframework.content.rest.config;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.junit.runner.RunWith;
import org.springframework.content.commons.annotations.Content;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.mongo.config.EnableMongoContentRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.rest.webmvc.config.RepositoryRestMvcConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

import java.util.Arrays;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.BeforeEach;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Describe;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(Ginkgo4jRunner.class)
@Ginkgo4jConfiguration(threads = 1)
public class RestConfigurationTest {

	private AnnotationConfigWebApplicationContext context;

	// mocks
	private static ContentRestConfigurer configurer;

	{
		Describe("RestConfiguration", () -> {
			BeforeEach(() -> {
				configurer = mock(ContentRestConfigurer.class);
			});
			Context("given a context with a ContentRestConfiguration", () -> {
				BeforeEach(() -> {
					context = new AnnotationConfigWebApplicationContext();
					context.setServletContext(new MockServletContext());
					context.register(TestConfig.class,
							DelegatingWebMvcConfiguration.class,
							RepositoryRestMvcConfiguration.class,
							RestConfiguration.class);
					context.refresh();
				});

				It("should have a content handler mapping bean", () -> {
					assertThat(context.getBean("contentHandlerMapping"),
							is(not(nullValue())));
				});

				It("should have the content rest controllers", () -> {
					assertThat(
							context.getBean("contentEntityRestController"),
							is(not(nullValue())));
					assertThat(
							context.getBean("contentPropertyCollectionRestController"),
							is(not(nullValue())));
					assertThat(
							context.getBean("contentPropertyRestController"),
							is(not(nullValue())));
					assertThat(context.getBean("storeRestController"),
							is(not(nullValue())));
				});

				It("should be configurable", () -> {
					RestConfiguration config = context.getBean(RestConfiguration.class);
					assertThat(config, is(not(nullValue())));

					verify(configurer).configure(config);
				});
			});
		});
	}

	@Configuration
	@EnableMongoContentRepositories
	public static class TestConfig extends AbstractMongoConfiguration {
		@Bean
		public GridFsTemplate gridFsTemplate() throws Exception {
			return new GridFsTemplate(mongoDbFactory(), mappingMongoConverter());
		}

		@Override
		protected String getDatabaseName() {
			return "spring-content";
		}

		@Override
		public MongoDbFactory mongoDbFactory() {

			if (System.getenv("spring_eg_content_mongo_host") != null) {
				String host = System.getenv("spring_eg_content_mongo_host");
				String port = System.getenv("spring_eg_content_mongo_port");
				String username = System.getenv("spring_eg_content_mongo_username");
				String password = System.getenv("spring_eg_content_mongo_password");

				// Set credentials
				MongoCredential credential = MongoCredential.createCredential(username,
						getDatabaseName(), password.toCharArray());
				ServerAddress serverAddress = new ServerAddress(host,
						Integer.parseInt(port));

				// Mongo Client
				MongoClient mongoClient = new MongoClient(serverAddress,
						Arrays.asList(credential));

				// Mongo DB Factory
				return new SimpleMongoDbFactory(mongoClient, getDatabaseName());
			}
			return super.mongoDbFactory();
		}

		@Override
		public MongoClient mongoClient() {
			return new MongoClient();
		}

		@Bean
		public ContentRestConfigurer configurer() {
			return configurer;
		}
	}

	@Document
	@Content
	public class TestEntity {
		@Id
		private String id;
		@ContentId
		private String contentId;
	}

	public interface TestEntityRepository extends MongoRepository<TestEntity, String> {
	}

	public interface TestEntityContentRepository
			extends ContentStore<TestEntity, String> {
	}
}
