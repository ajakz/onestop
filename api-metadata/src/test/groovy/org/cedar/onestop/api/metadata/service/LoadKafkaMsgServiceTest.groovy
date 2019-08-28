package org.cedar.onestop.api.metadata.service

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.cedar.schemas.avro.psi.ParsedRecord
import org.cedar.schemas.avro.util.AvroUtils
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class LoadKafkaMsgServiceTest extends Specification {

  static testCollectionTopic = 'test_collection_topic'
  static testGranuleTopic = 'test_granule_topic'

  def consumerService = new KafkaConsumerService()
  def mockMetadataService = Mock(MetadataManagementService)

  def setup() {
    consumerService.parsedCollectionTopic = testCollectionTopic
    consumerService.parsedGranulesTopic = testGranuleTopic
    consumerService.metadataManagementService = mockMetadataService
  }
  
  def "loads a valid metadata record" () {
    given:
    def inputKey = 'ABC'
    def inputStream = ClassLoader.systemClassLoader.getResourceAsStream('example-record-avro.json')
    def inputValue = AvroUtils.<ParsedRecord> jsonToAvro(inputStream, ParsedRecord.classSchema)
    def inputRecord = new ConsumerRecord(testCollectionTopic, 0, 0, inputKey, inputValue)
   
    when:
    consumerService.listen([inputRecord])

    then:
    1 * mockMetadataService.loadParsedRecords([[id: inputKey, parsedRecord: inputValue]])
  }

  def "ignores invalid metadata record" () {
    given:
    def inputKey = 'ABC'
    def inputValue = new ParsedRecord()
    def inputRecord = new ConsumerRecord(testCollectionTopic, 0, 0, inputKey, inputValue)

    when:
    consumerService.listen([inputRecord])

    then:
    0 * mockMetadataService.loadParsedRecords(_)
  }

  def "filters out invalid metadata records" () {
    given:
    def inputKey = 'ABC'
    def inputStream = ClassLoader.systemClassLoader.getResourceAsStream('example-record-avro.json')
    def validValue = AvroUtils.<ParsedRecord> jsonToAvro(inputStream, ParsedRecord.classSchema)
    def validRecord = new ConsumerRecord(testCollectionTopic, 0, 0, inputKey, validValue)
    def invalidValue = new ParsedRecord()
    def invalidRecord = new ConsumerRecord(testCollectionTopic, 0, 0, inputKey, invalidValue)

    when:
    consumerService.listen([validRecord, invalidRecord])

    then:
    1 * mockMetadataService.loadParsedRecords({ it.size() == 1 && it[0].parsedRecord == validRecord.value() })
  }

  def "appends default index-ready Discovery and Analysis to ParsedRecord"() {
    given:
    def inputKey = 'default123'
    def inputStream = ClassLoader.systemClassLoader.getResourceAsStream('parsed-record-no-discovery-or-analysis.json')
    def inputValue = AvroUtils.<ParsedRecord> jsonToAvro(inputStream, ParsedRecord.classSchema)
    def inputRecord = new ConsumerRecord(testCollectionTopic, 0 , 0, inputKey, inputValue)

    def expectedOutputStream = ClassLoader.systemClassLoader.getResourceAsStream('parsed-record-with-default-discovery.json')
    def expectedOutputValue = AvroUtils.<ParsedRecord> jsonToAvro(expectedOutputStream, ParsedRecord.classSchema)

    when:
    consumerService.listen([inputRecord])

    then:
    1 * mockMetadataService.loadParsedRecords([[id: inputKey, parsedRecord: expectedOutputValue]])
  }

}
