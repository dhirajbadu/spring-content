package internal.org.springframework.content.jpa.repository;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.io.DeletableResource;
import org.springframework.content.commons.repository.AssociativeStore;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.repository.Store;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.content.commons.utils.Condition;
import org.springframework.content.jpa.io.BlobResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.WritableResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.UUID;

public class DefaultJpaStoreImpl<S, SID extends Serializable> implements Store<SID>, AssociativeStore<S, SID>, ContentStore<S,SID> {

    private static Log logger = LogFactory.getLog(DefaultJpaStoreImpl.class);

    private ResourceLoader loader;

    public DefaultJpaStoreImpl(ResourceLoader blobResourceLoader) {
        this.loader = blobResourceLoader;
    }

    @Override
    public Resource getResource(SID id) {
        return loader.getResource(id.toString());
    }

    @Override
    public Resource getResource(S entity) {
        Object id = BeanUtils.getFieldWithAnnotation(entity, ContentId.class);
        if (id == null) {
            id = -1L;
        }

        return loader.getResource(id.toString());
    }

    @Override
    public void associate(S entity, SID id) {
        BeanUtils.setFieldWithAnnotation(entity, ContentId.class, id.toString());
        Resource resource = loader.getResource(id.toString());
        try {
            BeanUtils.setFieldWithAnnotation(entity, ContentLength.class, resource.contentLength());
        } catch (IOException e) {
            logger.error(String.format("Unexpected error setting content length for %s", id.toString()), e);
        }
    }

    @Override
    public void unassociate(S entity) {
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
	public InputStream getContent(S metadata) {
        Object id = BeanUtils.getFieldWithAnnotation(metadata, ContentId.class);
        if (id == null) {
            return null;
        }
        Resource resource = loader.getResource(id.toString());
        try {
            return resource.getInputStream();
        } catch (IOException e) {
            logger.error(String.format("Unable to get input stream for resource %s", id));
        }
        return null;
    }

	@Override
	public void setContent(S metadata, InputStream content) {
        Resource resource = getResource(metadata);
        OutputStream os = null;
        long contentLen = -1L;
        try {
            if (resource instanceof WritableResource) {
                os = ((WritableResource)resource).getOutputStream();
                contentLen = IOUtils.copyLarge(content, os);
            }
        } catch (IOException e) {
            logger.error(String.format("Unable to get output stream for resource %s", resource));
        } finally {
            IOUtils.closeQuietly(content);
            IOUtils.closeQuietly(os);
        }

        waitForCommit((BlobResource) resource);

        BeanUtils.setFieldWithAnnotation(metadata, ContentId.class, ((BlobResource)resource).getId());
        BeanUtils.setFieldWithAnnotation(metadata, ContentLength.class, contentLen);

        return;
	}

    private void waitForCommit(BlobResource resource) {
        synchronized (resource) {
            return;
        }
    }

    @Override
	public void unsetContent(S metadata) {
        Object id = BeanUtils.getFieldWithAnnotation(metadata, ContentId.class);
        if (id == null) {
            id = -1L;
        }
        Resource resource = loader.getResource(id.toString());
        if (resource instanceof DeletableResource) {
            ((DeletableResource)resource).delete();
        }
        unassociate(metadata);
	}
}
