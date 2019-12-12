package org.cedar.onestop.indexer;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.cedar.onestop.elastic.common.FileUtil;
import org.cedar.onestop.indexer.stream.BulkIndexingTransformer;
import org.cedar.onestop.indexer.stream.ElasticsearchRequestMapper;
import org.cedar.onestop.indexer.stream.FlatteningTriggerTransformer;
import org.cedar.onestop.indexer.stream.SitemapIndexer;
import org.cedar.onestop.indexer.util.ElasticsearchService;
import org.cedar.onestop.kafka.common.conf.AppConfig;
import org.cedar.onestop.kafka.common.constants.StreamsApps;
import org.cedar.onestop.kafka.common.constants.Topics;
import org.cedar.onestop.kafka.common.util.TimestampedValue;
import org.cedar.onestop.kafka.common.util.Timestamper;
import org.cedar.schemas.avro.psi.ParsedRecord;
import org.cedar.schemas.avro.psi.RecordType;
import org.cedar.schemas.avro.psi.Relationship;
import org.cedar.schemas.avro.psi.RelationshipType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SearchIndexTopology {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexTopology.class);
  private static final String FLATTEN_SCRIPT_PATH = "scripts/flattenGranules.painless";

  public static Topology buildSearchIndexTopology(ElasticsearchService esService, AppConfig appConfig) throws IOException {
    var streamsBuilder = new StreamsBuilder();
    long bulkIntervalMillis = Long.parseLong(appConfig.getOrDefault("elasticsearch.bulk.interval.ms", "10000").toString());
    long bulkMaxBytes = Long.parseLong(appConfig.getOrDefault("elasticsearch.bulk.max.bytes", "10000000").toString());
    var flatteningScript = FileUtil.textFromClasspathFile(FLATTEN_SCRIPT_PATH);
    if (flatteningScript == null || flatteningScript.isEmpty()) {
      throw new IllegalStateException("Unable to load required flattening script from [" + FLATTEN_SCRIPT_PATH + "]");
    }

    var collectionIndex = esService.config().COLLECTION_SEARCH_INDEX_ALIAS;
    var collectionTopic = Topics.parsedChangelogTopic(StreamsApps.REGISTRY_ID, RecordType.collection);
    var inputCollections = streamsBuilder.<String, ParsedRecord>stream(collectionTopic);
    var timestampedInputCollections = inputCollections.transformValues((ValueTransformerSupplier<ParsedRecord, TimestampedValue<ParsedRecord>>) Timestamper::new);
    var collectionRequests = timestampedInputCollections.mapValues(new ElasticsearchRequestMapper(esService.config(), collectionIndex));

    var granuleIndex = esService.config().GRANULE_SEARCH_INDEX_ALIAS;
    var granuleTopic = Topics.parsedChangelogTopic(StreamsApps.REGISTRY_ID, RecordType.granule);
    var inputGranules = streamsBuilder.<String, ParsedRecord>stream(granuleTopic);
    var timestampedInputGranules = inputGranules.transformValues((ValueTransformerSupplier<ParsedRecord, TimestampedValue<ParsedRecord>>) Timestamper::new);
    var granuleRequests = timestampedInputGranules.mapValues(new ElasticsearchRequestMapper(esService.config(), granuleIndex));

    var indexResults = collectionRequests.merge(granuleRequests)
        .filter((key, value) -> value != null)
        .transform(() -> new BulkIndexingTransformer(esService.client(), Duration.ofMillis(bulkIntervalMillis), bulkMaxBytes));

    var successfullyIndexed = indexResults.filter((key, value) -> value != null && !value.isFailed());
    var successfulCollections = successfullyIndexed.filter((key, value) -> value.getIndex().equals(collectionIndex));
    var successfulGranules = successfullyIndexed.filter((key, value) -> value.getIndex().equals(granuleIndex));

    // join the successfully-uploaded granules back to their inputs in order to retrieve their timestamps,
    // then re-key the stream based on those granules' parentIds to get a stream of collections that need
    // to be re-flattened and the timestamps from which flattening should start
    var flatteningUpdatesFromGranules = successfulGranules
        .join(
            timestampedInputGranules,
            (bulkItemResponse, inputGranule) -> inputGranule,
            JoinWindows.of(Duration.ofMillis(bulkIntervalMillis * 5)) // TODO - is this long enough?
        ) // stream of granuleId => timestampedInputGranule with same id
        .flatMap((granuleId, inputGranule) ->
          getParentIds(inputGranule.data).stream()
              .map(parentId -> new KeyValue<>(parentId, inputGranule.timestampMs))
              .collect(Collectors.toList())
        ); // stream of collectionId => timestamp of updated granule belonging to collection

    var flatteningUpdatesFromCollections = successfulCollections
        .mapValues(bulkItemResponse -> 0L); // stream of collectionId => 0, since all granules should be flattened

    flatteningUpdatesFromCollections.merge(flatteningUpdatesFromGranules)
        .through("NEW_TOPIC_NAME") // re-partition so all triggers for a given collection go to the same consumer
        .groupByKey()
        .windowedBy(TimeWindows.of(Duration.ofMillis(bulkIntervalMillis))) // TODO - separate config value?
        .reduce(Math::min) // each trigger uses the *earliest* time value in the window so all related granules are flattened
        .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
        .toStream()
        .transform(() -> new FlatteningTriggerTransformer(esService, flatteningScript))
        .filter((k, v) -> !v.successful)
        .mapValues(v -> v.timestamp)
        .to("NEW_TOPIC_NAME");

    timestampedInputCollections
        .mapValues(v -> v.timestampMs)
        .groupByKey()
        .windowedBy(TimeWindows.of(Duration.ofSeconds(1))) // TODO - make configurable
        .reduce(Math::max)
        .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
        .toStream()
        .foreach((k, v) -> SitemapIndexer.buildSitemap(esService, v));


    // potential example of streaming flattening:
    // creates a global ktable of collections and joins granules with it based on the relationship id
//    var collectionTable = streamsBuilder.<String, ParsedRecord>globalTable(Topics.parsedChangelogTopic(StreamsApps.REGISTRY_ID, RecordType.collection));
//    var granuleStream = streamsBuilder.<String, ParsedRecord>stream(Topics.parsedChangelogTopic(StreamsApps.REGISTRY_ID, RecordType.granule));
//    granuleStream
//        .mapValues((readOnlyKey, value) -> buildRequest(config.GRANULE_SEARCH_INDEX_ALIAS, readOnlyKey, value))
//        .filter((key, value) -> value != null)
//        .foreach((key, value) -> bulkProcessor.add(value));
//    granuleStream
//        .join(collectionTable,
//            (key, value) -> value.getRelationships().stream()
//                .filter(rel -> rel.getType() == RelationshipType.COLLECTION)
//                .findFirst()
//                .map(Relationship::getId)
//                .orElse(null),
//            IndexingHelpers::flattenRecords)
//        .mapValues((readOnlyKey, value) -> buildRequest(config.FLAT_GRANULE_SEARCH_INDEX_ALIAS, readOnlyKey, value))
//        .filter((key, value) -> value != null)
//        .foreach((key, value) -> bulkProcessor.add(value));

    return streamsBuilder.build();
  }

  private static List<String> getParentIds(ParsedRecord value) {
    return Optional.ofNullable(value)
        .map(ParsedRecord::getRelationships)
        .orElse(Collections.emptyList())
        .stream()
        .filter(rel -> rel.getType() == RelationshipType.COLLECTION)
        .map(Relationship::getId)
        .collect(Collectors.toList());
  }

}
