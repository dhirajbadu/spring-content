package internal.org.springframework.content.rest.utils;

import static org.springframework.data.rest.webmvc.ControllerUtils.EMPTY_RESOURCE_LIST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.rest.webmvc.PersistentEntityResourceAssembler;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.core.EmbeddedWrappers;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class ControllerUtils {

    private static final EmbeddedWrappers WRAPPERS = new EmbeddedWrappers(false);

    private ControllerUtils() {}

    protected static CollectionModel<?> entitiesToResources(Page<Object> page,
            PagedResourcesAssembler<Object> prAssembler,
            PersistentEntityResourceAssembler assembler,
            Class<?> domainType) {

        if (page.getContent().isEmpty()) {
            return prAssembler.toEmptyModel(page, domainType);
        }

        if (assembler != null) {
            return prAssembler.toModel(page, assembler);
        } else {
            return prAssembler.toModel(page);
        }
    }

    protected static CollectionModel<?> entitiesToResources(Iterable<Object> entities,
            PersistentEntityResourceAssembler assembler,
            Class<?> domainType) {

        if (!entities.iterator().hasNext()) {

            List<Object> content = Arrays.<Object> asList(WRAPPERS.emptyCollectionOf(domainType));
            return new CollectionModel<Object>(content, getDefaultSelfLink());
        }

        List<EntityModel<Object>> resources = new ArrayList<EntityModel<Object>>();

        for (Object obj : entities) {
            if (assembler != null) {
                resources.add(obj == null ? null : assembler.toModel(obj));
            } else {

                EntityModel m = EntityModel.of(obj);
                resources.add(m);
            }
        }

        return new CollectionModel<EntityModel<Object>>(resources, getDefaultSelfLink());
    }

    protected static Link getDefaultSelfLink() {
        return new Link(
                ServletUriComponentsBuilder.fromCurrentRequest().build().toUriString());
    }

    public static CollectionModel<?> toCollectionModel(Iterable<?> source,
            PagedResourcesAssembler<Object> prAssembler,
            PersistentEntityResourceAssembler assembler,
            Class<?> domainType) {

        if (source instanceof Page) {
            Page<Object> page = (Page<Object>) source;
            return entitiesToResources(page, prAssembler, assembler, domainType);
        } else if (source instanceof Iterable) {
            return entitiesToResources((Iterable<Object>) source, assembler, domainType);
        } else {
            return new CollectionModel(EMPTY_RESOURCE_LIST);
        }
    }
}
