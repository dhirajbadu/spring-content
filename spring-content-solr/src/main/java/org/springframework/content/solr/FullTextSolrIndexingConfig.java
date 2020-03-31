package org.springframework.content.solr;

import internal.org.springframework.content.solr.SolrFulltextIndexServiceImpl;
import org.apache.solr.client.solrj.SolrClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.content.commons.search.IndexService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = { "org.springframework.content.solr" })
public class FullTextSolrIndexingConfig {

	@Autowired
	private SolrClient solrClient;

	@Autowired
	private SolrProperties props;

//	@Autowired
//	private ConversionService contentConversionService;

//	@Bean
//	public StoreExtension solrFulltextSearcher() {
//		return new SolrSearchContentRepositoryExtension(solrClient,
//				new ReflectionServiceImpl(), contentConversionService, props);
//	}

	@Bean
	public Object solrFulltextEventListener() {
		return new SolrIndexer(solrClient, props);
	}

	@Bean
	public IndexService solrFulltextIndexService() {
		return new SolrFulltextIndexServiceImpl();
	}
}
