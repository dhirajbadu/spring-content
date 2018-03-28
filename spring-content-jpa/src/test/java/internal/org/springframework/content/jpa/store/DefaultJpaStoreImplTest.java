package internal.org.springframework.content.jpa.store;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import internal.org.springframework.content.jpa.io.GenericBlobResource;
import internal.org.springframework.content.jpa.repository.DefaultJpaStoreImpl;
import org.hamcrest.CoreMatchers;
import org.junit.runner.RunWith;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.jpa.io.BlobResource;
import org.springframework.content.jpa.io.BlobResourceLoader;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(Ginkgo4jRunner.class)
public class DefaultJpaStoreImplTest {

    private DefaultJpaStoreImpl<Object,Integer> store;

    private BlobResourceLoader blobResourceLoader;

    private TestEntity entity;
    private InputStream stream;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Resource resource;
    private Integer id;
    private Exception e;

    {
        Describe("DefaultJpaStoreImpl", () -> {
            JustBeforeEach(() -> {
                    store = new DefaultJpaStoreImpl(blobResourceLoader);
            });

            Describe("Store", () -> {
                BeforeEach(() -> {
                    blobResourceLoader = mock(BlobResourceLoader.class);
                });
                Context("#getResource", () -> {
                    Context("given an id", () -> {
                        BeforeEach(() -> {
                            id = 1;
                        });
                        JustBeforeEach(() -> {
                            resource = store.getResource(id);
                        });
                        It("should use the blob resource loader to load a blob resource", () -> {
                            verify(blobResourceLoader).getResource(id.toString());
                        });
                    });
                });
                Context("#associate", () -> {
                    BeforeEach(() -> {
                        id = 12345;

                        entity = new TestEntity();

                        resource = mock(BlobResource.class);
                        when(blobResourceLoader.getResource(eq("12345"))).thenReturn(resource);
                        when(resource.contentLength()).thenReturn(20L);
                    });
                    JustBeforeEach(() -> {
                        store.associate(entity, id);
                    });
                    It("should use the conversion service to get a resource path", () -> {
                        verify(blobResourceLoader).getResource(eq("12345"));
                    });
                    It("should set the entity's content ID attribute", () -> {
                        assertThat(entity.getContentId(), CoreMatchers.is(12345));
                    });
                    It("should set the entity's content length attribute", () -> {
                        assertThat(entity.getContentLen(), CoreMatchers.is(20L));
                    });
                });
            });

            Context("#getContent", () -> {
                BeforeEach(() -> {
                    blobResourceLoader= mock(BlobResourceLoader.class);
                    resource = mock(GenericBlobResource.class);

                    entity = new TestEntity(12345);

                    when(blobResourceLoader.getResource(entity.getContentId().toString())).thenReturn((BlobResource)resource);
                });
                JustBeforeEach(() -> {
                    inputStream = store.getContent(entity);
                });
                Context("given content", () -> {
                    BeforeEach(() -> {
                        stream = new ByteArrayInputStream("hello content world!".getBytes());

                        when(resource.getInputStream()).thenReturn(stream);
                    });

                    It("should use the blob resource factory to create a new blob resource", () -> {
                        verify(blobResourceLoader).getResource(entity.getContentId().toString());
                    });

                    It("should return an inputstream", () -> {
                        assertThat(inputStream, is(not(nullValue())));
                    });
                });
                Context("given fetching the input stream fails", () -> {
                    BeforeEach(() -> {
                        when(resource.getInputStream()).thenThrow(new IOException());
                    });
                    It("should return null", () -> {
                        assertThat(inputStream, is(nullValue()));
                    });
                });
            });
            Context("#setContent", () -> {
                JustBeforeEach(() -> {
                    try {
                        store.setContent(entity, inputStream);
                    } catch (Exception e) {
                        this.e = e;
                    }
                });
                Context("when the row does not exist", () -> {
                    BeforeEach(() -> {
                        blobResourceLoader = mock(BlobResourceLoader.class);

                        entity = new TestEntity();
                        byte[] content = new byte[5000];
                        new Random().nextBytes(content);
                        inputStream = new ByteArrayInputStream(content);

                        resource = mock(BlobResource.class);
                        when(blobResourceLoader.getResource("-1")).thenReturn((BlobResource)resource);
                        outputStream = mock(OutputStream.class);
                        when(((BlobResource) resource).getOutputStream()).thenReturn(outputStream);
                        when(((BlobResource) resource).getId()).thenReturn(12345);
                    });
                    It("should write the contents of the inputstream to the resource's outputstream", () -> {
                        verify(outputStream, atLeastOnce()).write(anyObject(), anyInt(), anyInt());
                    });
                    It("should update the @ContentId field", () -> {
                        assertThat(entity.getContentId(), is(12345));
                    });
                    It("should update the @ContentLength field", () -> {
                        assertThat(entity.getContentLen(), is(5000L));
                    });
                });
            });
        });
    }

    public static class TestEntity {
        @ContentId
        private Integer contentId;
        @ContentLength
        private long contentLen;

        public TestEntity() {
            this.contentId = null;
        }

        public TestEntity(int contentId) {
            this.contentId = new Integer(contentId);
        }

        public Integer getContentId() {
            return this.contentId;
        }

        public void setContentId(Integer contentId) {
            this.contentId = contentId;
        }

        public long getContentLen() {
            return contentLen;
        }

        public void setContentLen(long contentLen) {
            this.contentLen = contentLen;
        }

    }

}
