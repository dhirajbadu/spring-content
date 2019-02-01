package internal.org.springframework.content.rest.controllers;

import com.theoryinpractise.halbuilder.api.ReadableRepresentation;
import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.theoryinpractise.halbuilder.standard.StandardRepresentationFactory;
import internal.org.springframework.content.rest.support.ContentEntity;
import internal.org.springframework.content.rest.support.TestEntity3;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.repository.CrudRepository;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.io.StringReader;
import java.util.Optional;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.Context;
import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.It;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Getter
@Setter
public class Entity {

	private MockMvc mvc;
	private String url;
	private String linkRel;
	private ContentEntity entity;
	private CrudRepository repository;

	public static Entity tests() {
		return new Entity();
	}

	{
		Context("a GET to /{store}/{id} accepting hal+json", () -> {
			It("should return the entity", () -> {
				MockHttpServletResponse response = mvc
						.perform(get(url)
								.accept("application/hal+json"))
						.andExpect(status().isOk())
						.andReturn().getResponse();

				RepresentationFactory representationFactory = new StandardRepresentationFactory();
				ReadableRepresentation halResponse = representationFactory
						.readRepresentation("application/hal+json",
								new StringReader(response.getContentAsString()));
				assertThat(halResponse.getLinks().size(), is(2));
				assertThat(halResponse.getLinksByRel(linkRel), is(not(nullValue())));
			});
		});
		Context("a PUT to /{store}/{id} with a json body", () -> {
			It("should set Entities data and return 200", () -> {
				mvc.perform(put(url)
						.content("{\"name\":\"Spring Content\"}")
						.contentType("application/hal+json"))
						.andExpect(status().is2xxSuccessful());

				Optional<TestEntity3> fetched = repository.findById(entity.getId());
				assertThat(fetched.isPresent(), is(true));
				assertThat(fetched.get().name, is("Spring Content"));
				assertThat(fetched.get().contentId, is(nullValue()));
				assertThat(fetched.get().len, is(nullValue()));
				assertThat(fetched.get().mimeType, is(nullValue()));
			});
		});
		Context("a PATCH to /{store}/{id} with a json body", () -> {
			It("should patch the entity data and return 200", () -> {
				mvc.perform(patch(url)
						.content("{\"name\":\"Spring Content Modified\"}")
						.contentType("application/hal+json"))
						.andExpect(status().is2xxSuccessful());

				Optional<TestEntity3> fetched = repository.findById(entity.getId());
				assertThat(fetched.isPresent(), is(true));
				assertThat(fetched.get().name, is("Spring Content Modified"));
				assertThat(fetched.get().contentId, is(nullValue()));
				assertThat(fetched.get().len, is(nullValue()));
				assertThat(fetched.get().mimeType, is(nullValue()));
			});
		});
		Context("a HEAD to /{store}/{id} with a json body", () -> {
			It("should return 200", () -> {
				mvc.perform(head(url))
						.andExpect(status().is2xxSuccessful());
			});
		});

	}
}
