package ncei.onestop.api

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.SpringApplicationConfiguration
import org.springframework.boot.test.WebIntegrationTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.web.client.RestTemplate
import spock.lang.IgnoreRest
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
@WebIntegrationTest("server.port:8097")
@SpringApplicationConfiguration(classes = Application)
class SearchApiIntegrationSpec extends Specification {
    @Value('${local.server.port}')
    String testPort = "8097"
    MediaType contentType = MediaType.APPLICATION_JSON

    String searchBaseUrl = "http://localhost:${testPort}/onestop/search"

    private static RestTemplate setupRestClient() {
        RestTemplate restTemplate = new RestTemplate()
        restTemplate.setErrorHandler(new TestResponseErrorHandler())
        restTemplate
    }

    def searches = [
            notexistingwordsearch: [
                    body: '{"searchText": "spork"}'
            ],
            baresearch           : [
                    body: '{"searchText": "temperature"}'
            ],
            badjsonsearch        : [
                    body: '{"searchText": temperature"}'
            ],
            blankjsonsearch      : [
                    body: '{"searchText": ""}'
            ],
            strangecharjsonsearch: [
                    body: '{"searchText": "~"}'
            ]
    ]

    // -------- Test Cases --------
    def 'valid search returns ok and results'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body(searches.baresearch.body)

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Search ok"
        result.statusCode == HttpStatus.OK
        and: "result contains > 0 items"
        def items = result.body.items
        items
        items.size() > 0
        and: "each item has a title"
        items.each { item ->
            item.title
        }
    }

    @IgnoreRest
    def 'valid search returns ok and results when search test is strange character'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body(searches.strangecharjsonsearch.body)

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Search ok"
        result.statusCode == HttpStatus.OK
        and: "result contains > 0 items"
        def items = result.body.items
        items == []
    }

    def 'valid search returns ok and results when search test is non existing word'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body(searches.notexistingwordsearch.body)

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Search ok"
        result.statusCode == HttpStatus.OK
        and: "result contains > 0 items"
        def items = result.body.items
        items == []
    }

    def 'valid search returns ok and results when search test is blank'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body(searches.blankjsonsearch.body)

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Search ok"
        result.statusCode == HttpStatus.OK
        and: "result contains > 0 items"
        def items = result.body.items
        items == []
    }

    def 'invalid search returns returns error, json not parseable'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body(searches.badjsonsearch.body)

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Bad request"
        result.statusCode == HttpStatus.BAD_REQUEST
        and: "result contains no items"
        result.body.items == null
    }

    def 'invalid search returns returns error, need to specify body is json content type'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .body(searches.baresearch.body)

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Bad request"
        result.statusCode == HttpStatus.UNSUPPORTED_MEDIA_TYPE
        and: "result contains no items"
        result.body.items == null
    }

    def 'invalid search returns returns error, need to specify json content'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body("")

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Bad request"
        result.statusCode == HttpStatus.BAD_REQUEST
        and: "result contains no items"
        result.body.items == null
    }

    def 'valid search returns ok and results, if json is empty'() {
        def restTemplate = setupRestClient()
        def requestEntity = RequestEntity
                .post(searchBaseUrl.toURI())
                .contentType(contentType)
                .body("{}")

        when:
        def result = restTemplate.exchange(requestEntity, Map)

        then: "Search ok"
        result.statusCode == HttpStatus.OK
        and: "result contains > 0 items"
        def items = result.body.items
        items
        items.size() > 0
        and: "each item has a title"
        items.each { item ->
            item.title
        }
    }
}