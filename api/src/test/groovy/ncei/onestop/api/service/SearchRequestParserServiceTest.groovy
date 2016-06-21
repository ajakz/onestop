package ncei.onestop.api.service

import groovy.json.JsonSlurper
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class SearchRequestParserServiceTest extends Specification {

    private slurper = new JsonSlurper()
    private requestParser = new SearchRequestParserService()

    def "request with #label creates empty elasticsearch request" () {
        given:
        def params = slurper.parseText(json)

        when:
        def result = requestParser.parseSearchRequest(params)
        def expectedString = """\
        {
          "bool" : {
            "must" : {
              "bool" : { }
            },
            "filter" : {
              "bool" : { }
            }
          }
        }""".stripIndent()

        then:
        !result.toString().empty
        result.toString() == expectedString

        where:
        label                       | json
        'nothing'                   | '{}'
        'empty queries and filters' | '{"queries":[],"filters":[]}'
        'only queries'              | '{"queries":[]}'
        'only filters'              | '{"filters":[]}'
    }

    def "Test only queryText specified" () {
        given:
        def request = '{"queries":[{"type":"queryText","value":"winter"}]}'
        def params = slurper.parseText(request)

        when:
        def result = requestParser.parseSearchRequest(params)
        def expectedString = """\
        {
          "bool" : {
            "must" : {
              "bool" : {
                "must" : {
                  "query_string" : {
                    "query" : "winter"
                  }
                }
              }
            },
            "filter" : {
              "bool" : { }
            }
          }
        }""".stripIndent()

        then:
        !result.toString().empty
        result.toString().equals expectedString
    }
}