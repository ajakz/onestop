package org.cedar.psi.registry

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.fge.jsonschema.main.JsonSchema
import com.github.fge.jsonschema.main.JsonSchemaFactory
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import io.confluent.kafka.schemaregistry.RestApp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.streams.KafkaStreams
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import spock.lang.Specification

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT

@Slf4j
@DirtiesContext
@EmbeddedKafka
@ActiveProfiles('integration')
@SpringBootTest(classes = [MetadataRegistryMain], webEnvironment = RANDOM_PORT)
class RegistryIntegrationSpec extends Specification {

  @Configuration
  static class IntegrationConfig {
    @Value('${spring.embedded.zookeeper.connect}')
    String zkConnect

    @Bean(initMethod = 'start')
    RestApp schemaRegistryRestApp() {
      new RestApp(8081, zkConnect, '_schemas')
    }
  }

  @Value('${local.server.port}')
  String port

  @Value('${server.servlet.context-path:}')
  String contextPath

  @Autowired
  KafkaStreams streamsApp

  @Autowired
  AdminClient adminClient

  @Autowired
  RestApp schemaRegistryRestApp

  RestTemplate restTemplate
  String baseUrl

  final ObjectMapper mapper = new ObjectMapper()
  final JsonSchemaFactory factory = JsonSchemaFactory.byDefault()
  final String registryResponseSchemaString = ClassLoader.systemClassLoader.getResourceAsStream('jsonSchema/registryResponse-schema.json').text
  final JsonNode registryResponseSchemaNode = mapper.readTree(registryResponseSchemaString)
  final JsonSchema registryResponseSchema = factory.getJsonSchema(registryResponseSchemaNode)

  def setup() {
    restTemplate = new RestTemplate()
    baseUrl = "http://localhost:${port}/${contextPath}"
  }

  def 'can post then retrieve input info'() {
    def restTemplate = new RestTemplate()
    def granuleText = ClassLoader.systemClassLoader.getResourceAsStream('test_granule.json').text
    def granuleMap = new JsonSlurper().parseText(granuleText) as Map

    when:
    def createEntity = RequestEntity
        .post("${baseUrl}metadata/granule/common-ingest/${granuleMap.trackingId}".toURI())
        .contentType(MediaType.APPLICATION_JSON)
        .body(granuleText)
    def createResponse = restTemplate.exchange(createEntity, Map)
    def granuleId = createResponse.body.id

    then:
    granuleId instanceof String
    createResponse.statusCode == HttpStatus.OK

    when:
    sleep(200)
    def retrieveEntity = RequestEntity
        .get("${baseUrl}metadata/granule/common-ingest/${granuleId}".toURI())
        .build()
    def retrieveResponse = restTemplate.exchange(retrieveEntity, Map)

    then:
    registryResponseSchema.validate(mapper.readTree(JsonOutput.toJson(retrieveResponse.body)))
    retrieveResponse.statusCode == HttpStatus.OK
    retrieveResponse.body.data.id == granuleId
    retrieveResponse.body.data.type == 'granule'
    retrieveResponse.body.links.parsed == "${baseUrl}metadata/granule/common-ingest/${granuleId}/parsed"
    retrieveResponse.body.data.attributes.content == granuleText
    retrieveResponse.body.data.attributes.contentType == "application/json"
    retrieveResponse.body.data.attributes.source == "common-ingest"

    and: // let's verify the full response just this once
    retrieveResponse.body.data == [
        id        : granuleId,
        type      : 'granule',
        attributes: [
            "content"    : granuleText,
            "contentType": "application/json",
            "method"     : "POST",
            "source"     : "common-ingest",
            "type"       : "granule"
        ]
    ]
  }

  def 'collection that is not yet parsed returns 404'() {
    def restTemplate = new RestTemplate()
    def collectionText = ClassLoader.systemClassLoader.getResourceAsStream('dscovr_fc1.xml').text
    def collectionId = UUID.randomUUID()

    when:
    def createEntity = RequestEntity
        .post("${baseUrl}metadata/collection/${collectionId}".toURI())
        .contentType(MediaType.APPLICATION_XML)
        .body(collectionText)
    def createResponse = restTemplate.exchange(createEntity, Map)

    then:
    createResponse.statusCode == HttpStatus.OK

    when:
    sleep(200)
    def retrieveEntity = RequestEntity
        .get("${baseUrl}metadata/collection/${collectionId}/parsed".toURI())
        .build()
    def retrieveResponse = restTemplate.exchange(retrieveEntity, Map)

    then:
    def e = thrown(HttpClientErrorException)
    def body = new JsonSlurper().parseText(e.responseBodyAsString)

    e.statusCode == HttpStatus.NOT_FOUND
    registryResponseSchema.validate(mapper.readTree(JsonOutput.toJson(body)))
    body.links.input == "${baseUrl}metadata/collection/${collectionId}"
    body.errors instanceof List
  }

  def 'returns 404 for unsupported type'() {
    def restTemplate = new RestTemplate()
    def collectionText = ClassLoader.systemClassLoader.getResourceAsStream('dscovr_fc1.xml').text

    when:
    def createEntity = RequestEntity
        .post("${baseUrl}metadata/not-a-real-type/".toURI())
        .contentType(MediaType.APPLICATION_XML)
        .body(collectionText)
    def createResponse = restTemplate.exchange(createEntity, Map)

    then:
    def exception = thrown(HttpClientErrorException)
    exception.statusCode.value() == 404
    def body = new JsonSlurper().parse(exception.responseBodyAsByteArray) as Map
    body.errors instanceof List
    body.errors[0] instanceof Map
    body.errors[0].title instanceof CharSequence
  }

}
