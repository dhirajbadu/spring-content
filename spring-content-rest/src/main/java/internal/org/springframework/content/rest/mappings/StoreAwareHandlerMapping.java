package internal.org.springframework.content.rest.mappings;

import internal.org.springframework.content.rest.annotations.StoreAwareController;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Set;

import static java.lang.String.format;

public class StoreAwareHandlerMapping extends RequestMappingHandlerMapping {

	private RestConfiguration configuration;

	private String prefix;

	public StoreAwareHandlerMapping(RestConfiguration configuration) {
		Assert.notNull(configuration, "RestConfiguration must not be null!");
		this.configuration = configuration;
	}

	public RestConfiguration getConfiguration() {
		return configuration;
	}

	@Override
	protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {

		RequestMappingInfo info = super.getMappingForMethod(method, handlerType);

		if (info == null) {
			return null;
		}

		PatternsRequestCondition patternsCondition = customize(info.getPatternsCondition(), prefix);
//		ProducesRequestCondition producesCondition = customize(info.getProducesCondition());

		return new RequestMappingInfo(patternsCondition, info.getMethodsCondition(), info.getParamsCondition(),
				info.getHeadersCondition(), info.getConsumesCondition(), info.getProducesCondition(), info.getCustomCondition());
	}

	@Override
	protected boolean isHandler(Class<?> beanType) {

		Class<?> type = ClassUtils.getUserClass(beanType);

		return type.isAnnotationPresent(StoreAwareController.class);
	}

	@Override
	public void afterPropertiesSet() {

		URI baseUri = configuration.getBaseUri();

		if (baseUri.isAbsolute()) {
//			HttpServletRequest request = new BasePathAwareHandlerMapping.UriAwareHttpServletRequest(getServletContext(), baseUri);
//			this.prefix = URL_PATH_HELPER.getPathWithinApplication(request);
			throw new UnsupportedOperationException(format("absolute base URIs not supported %s", baseUri));
		} else {
			this.prefix = baseUri.toString();
		}

		super.afterPropertiesSet();
	}

	/**
	 * Customize the given {@link PatternsRequestCondition} and prefix.
	 *
	 * @param condition will never be {@literal null}.
	 * @param prefix will never be {@literal null}.
	 * @return
	 */
	protected PatternsRequestCondition customize(PatternsRequestCondition condition, String prefix) {

		Set<String> patterns = condition.getPatterns();
		String[] augmentedPatterns = new String[patterns.size()];
		int count = 0;

		for (String pattern : patterns) {
			augmentedPatterns[count++] = prefix.concat(pattern);
		}

		return new PatternsRequestCondition(augmentedPatterns, getUrlPathHelper(), getPathMatcher(),
				useSuffixPatternMatch(), useTrailingSlashMatch(), getFileExtensions());
	}
}
