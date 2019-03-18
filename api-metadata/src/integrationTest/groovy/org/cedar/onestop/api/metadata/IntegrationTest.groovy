package org.cedar.onestop.api.metadata

import org.cedar.onestop.api.metadata.authorization.configs.SpringSecurityConfig
import org.cedar.onestop.api.metadata.authorization.configs.SpringSecurityDisabled
import org.cedar.onestop.api.metadata.springsecurity.IdentityProviderConfig
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Specification

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@ActiveProfiles("integration")
@SpringBootTest(classes = [Application, IntegrationTestConfig, SpringSecurityDisabled, SpringSecurityConfig, IdentityProviderConfig], webEnvironment = RANDOM_PORT)
// disable security so we aren't testing HTTPS/CAC authentication,
// but explicitly enable secure upload feature so UploadController endpoint is visible to test
@TestPropertySource(locations = ["classpath:test-securitydisabled.yml"], properties = ["features.secure.upload=true"])
class IntegrationTest extends Specification {

  @Autowired
  RestClient restClient

  @Value('${elasticsearch.index.prefix:}${elasticsearch.index.search.collection.name}')
  String COLLECTION_SEARCH_INDEX

  @Value('${elasticsearch.index.prefix:}${elasticsearch.index.search.granule.name}')
  String GRANULE_SEARCH_INDEX

  @Value('${elasticsearch.index.prefix:}${elasticsearch.index.search.flattened-granule.name}')
  String FLATTENED_GRANULE_SEARCH_INDEX

  @Value('${elasticsearch.index.prefix:}${elasticsearch.index.staging.collection.name}')
  String COLLECTION_STAGING_INDEX

  @Value('${elasticsearch.index.prefix:}${elasticsearch.index.staging.granule.name}')
  String GRANULE_STAGING_INDEX

  @Value('${elasticsearch.index.prefix:}${elasticsearch.index.sitemap.name}')
  String SITEMAP_INDEX

  @Value('${elasticsearch.index.prefix:}')
  String PREFIX

  @Value('${elasticsearch.index.universal-type}')
  String TYPE
}
