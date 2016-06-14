package ncei.onestop.api.controller

import groovy.util.logging.Slf4j
import ncei.onestop.api.service.SearchService
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RestController

@Slf4j
@RestController
class SearchController {

    private SearchService searchService

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService
    }

    // POST in order to support request bodies from clients that won't send bodies with GETs
    @RequestMapping(path = "/search", method = [RequestMethod.POST, RequestMethod.GET])
    Map search(@RequestBody Map params) {
        searchService.search(params)
    }
}
