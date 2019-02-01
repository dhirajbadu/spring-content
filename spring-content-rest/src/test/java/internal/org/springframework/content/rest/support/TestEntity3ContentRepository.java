package internal.org.springframework.content.rest.support;

import org.springframework.content.commons.renditions.Renderable;
import org.springframework.content.commons.repository.ContentStore;

public interface TestEntity3ContentRepository extends ContentStore<TestEntity3, Long>, Renderable<TestEntity3> {
}
