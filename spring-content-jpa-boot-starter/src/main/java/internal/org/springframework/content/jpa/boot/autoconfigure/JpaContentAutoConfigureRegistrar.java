package internal.org.springframework.content.jpa.boot.autoconfigure;

import java.util.Set;

import internal.org.springframework.content.commons.utils.StoreUtils;
import internal.org.springframework.content.jpa.config.JpaContentRepositoriesRegistrar;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.content.jpa.config.EnableJpaContentRepositories;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;

public class JpaContentAutoConfigureRegistrar extends JpaContentRepositoriesRegistrar {

	@Override
	protected void registerContentStoreBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

		AnnotationMetadata metadata = new StandardAnnotationMetadata(EnableMongoContentAutoConfiguration.class);
		AnnotationAttributes attributes = new AnnotationAttributes(metadata.getAnnotationAttributes(this.getAnnotation().getName()));

		String[] basePackages = this.getBasePackages();

		Set<GenericBeanDefinition> definitions = StoreUtils.getContentRepositoryCandidates(this.getResourceLoader(), basePackages);

		for (BeanDefinition definition : definitions) {

			String factoryBeanName = StoreUtils.getRepositoryFactoryBeanName(attributes);

			BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(factoryBeanName);

			builder.getRawBeanDefinition().setSource(importingClassMetadata);
			builder.addPropertyValue("contentStoreInterface", definition.getBeanClassName());

			registry.registerBeanDefinition(StoreUtils.getRepositoryBeanName(definition), builder.getBeanDefinition());
		}
	}

	protected String[] getBasePackages() {
		return AutoConfigurationPackages.get(this.getBeanFactory()).toArray(new String[] {});
	}

	@EnableJpaContentRepositories
	private static class EnableMongoContentAutoConfiguration {
	}
}
