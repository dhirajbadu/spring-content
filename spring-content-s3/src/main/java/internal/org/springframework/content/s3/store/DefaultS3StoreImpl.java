package internal.org.springframework.content.s3.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import com.amazonaws.services.s3.model.S3ObjectId;
import internal.org.springframework.content.s3.io.S3StoreResource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.repository.AssociativeStore;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.repository.Store;
import org.springframework.content.commons.repository.StoreAccessException;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.content.commons.utils.Condition;
import org.springframework.content.s3.S3ObjectIdResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.WritableResource;
import org.springframework.util.Assert;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;

public class DefaultS3StoreImpl<S, SID extends Serializable> implements Store<SID>, AssociativeStore<S, SID>, ContentStore<S,SID> {

	private static Log logger = LogFactory.getLog(DefaultS3StoreImpl.class);

	private ResourceLoader loader;
	private ConversionService converter;
	private AmazonS3 client;
	private S3ObjectIdResolver idResolver = null;
	private String defaultBucket;

	public DefaultS3StoreImpl(ResourceLoader loader, ConversionService converter, AmazonS3 client, S3ObjectIdResolver idResolver, String defaultBucket) {
		Assert.notNull(loader, "loader must be specified");
		Assert.notNull(converter, "converter must be specified");
		Assert.notNull(client, "client must be specified");
		Assert.notNull(idResolver, "idResolver must be specified");
		this.loader = loader;
		this.converter = converter;
		this.client = client;
		this.idResolver = idResolver;
		this.defaultBucket = defaultBucket;
	}

	public S3ObjectIdResolver getS3ObjectIdResolver() {
		return idResolver;
	}

	@Override
	public Resource getResource(SID id) {
		if (id == null)
			return null;

		if (id instanceof S3ObjectId == false) {
			this.getS3ObjectIdResolver().validate(id);
			String bucket = this.getS3ObjectIdResolver().getBucket(id, this.defaultBucket);
			String objectId = this.getS3ObjectIdResolver().getKey(id);

			if (bucket == null) {
				throw new StoreAccessException("Bucket not set");
			}

			S3ObjectId s3ObjectId = new S3ObjectId(bucket, objectId);
			return this.getResourceInternal(s3ObjectId);
		} else {
			return this.getResourceInternal((S3ObjectId)id);
		}
	}

	@Override
	public Resource getResource(S entity) {
		if (entity == null)
			return null;

		String bucket = this.getS3ObjectIdResolver().getBucket(entity, this.defaultBucket);
		String objectId = this.getS3ObjectIdResolver().getKey(entity);

		if (bucket == null) {
			throw new StoreAccessException("Bucket not set");
		}

		S3ObjectId s3ObjectId = new S3ObjectId(bucket.toString(), objectId.toString());
		return this.getResourceInternal(s3ObjectId);
	}

	protected Resource getResourceInternal(S3ObjectId id) {
		String bucket = id.getBucket();
		Object objectId = id.getKey();

		String location = converter.convert(objectId, String.class);
		location = absolutify(bucket, location);
		Resource resource = loader.getResource(location);
		return new S3StoreResource(client, bucket, resource);
	}

	@Override
	public void associate(Object entity, Serializable id) {
        BeanUtils.setFieldWithAnnotation(entity, ContentId.class, id);
	}

	@Override
	public void unassociate(Object entity) {
        BeanUtils.setFieldWithAnnotationConditionally(entity, ContentId.class, null, new Condition() {
            @Override
            public boolean matches(Field field) {
                for (Annotation annotation : field.getAnnotations()) {
                    if ("javax.persistence.Id".equals(annotation.annotationType().getCanonicalName()) ||
						"org.springframework.data.annotation.Id".equals(annotation.annotationType().getCanonicalName())) {
                        return false;
                    }
                }
                return true;
            }});
        BeanUtils.setFieldWithAnnotation(entity, ContentLength.class, 0);
    }

	@Override
	public void setContent(S property, InputStream content) {
		Resource resource = this.getResource(property);

		OutputStream os = null;
		try {
			if (resource instanceof WritableResource) {
				os = ((WritableResource)resource).getOutputStream();
				IOUtils.copy(content, os);
			}
		} catch (IOException e) {
			logger.error(String.format("Unexpected error setting content for resource %s", resource.toString()), e);
		} finally {
	        try {
	            if (os != null) {
	                os.close();
	            }
	        } catch (IOException ioe) {
	            // ignore
	        }
		}
			
		try {
			BeanUtils.setFieldWithAnnotation(property, ContentLength.class, resource.contentLength());
		} catch (IOException e) {
			logger.error(String.format("Unexpected error setting content length for resource %s", resource.toString()), e);
		}
	}

	@Override
	public InputStream getContent(S property) {
		if (property == null)
			return null;

		Resource resource = this.getResource(property);

		try {
			if (resource.exists()) {
				return resource.getInputStream();
			}
		} catch (IOException e) {
			logger.error(String.format("Unexpected error getting content for resource %s", resource.toString()), e);
		}
		
		return null;
	}

	@Override
	public void unsetContent(S property) {
		if (property == null)
			return;

		try {
			deleteIfExists(property);

			// reset content fields
			BeanUtils.setFieldWithAnnotationConditionally(property, ContentId.class, null, new Condition() {
				@Override
				public boolean matches(Field field) {
					for (Annotation annotation : field.getAnnotations()) {
						if ("javax.persistence.Id".equals(annotation.annotationType().getCanonicalName()) ||
								"org.springframework.data.annotation.Id".equals(annotation.annotationType().getCanonicalName())) {
							return false;
						}
					}
					return true;
				}});
	        BeanUtils.setFieldWithAnnotation(property, ContentLength.class, 0);
		} catch (Exception ase) {
			logger.error(String.format("Unexpected error unsetting content for entity %s", property.toString()), ase);
		}
	}

	private String absolutify(String bucket, String location) {
		String locationToUse = null;
		Assert.state(location.startsWith("s3://") == false);
		if (location.startsWith("/")) {
			locationToUse = location.substring(1);
		} else {
			locationToUse = location;
		}
		return String.format("s3://%s/%s", bucket, locationToUse);
	}

	private void deleteIfExists(SID contentId) {
		String bucketName = this.idResolver.getBucket(contentId, this.defaultBucket);
		
		Resource resource = this.getResource(contentId);
		if (resource.exists()) {
			client.deleteObject(new DeleteObjectRequest(bucketName, resource.getFilename()));
		}
	}

	private void deleteIfExists(S entity) {
		String bucketName = this.getS3ObjectIdResolver().getBucket(entity, this.defaultBucket);

		Resource resource = this.getResource(entity);
		if (resource.exists()) {
			client.deleteObject(new DeleteObjectRequest(bucketName, resource.getFilename()));
		}
	}
}
