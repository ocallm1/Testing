package com.clearstream.hydrogen.messagetransform;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.CamelContext;
import org.apache.camel.model.RouteDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class StatusController {

    @Autowired
    CamelContext camelContext;

    @RequestMapping(path = "/")
    public String listRoutesAndStatuses(Map<String, Object> model) {
        List<RouteDefinition> routes = camelContext.getRouteDefinitions();
        TreeMap<String, String> routeStatuses = new TreeMap<String, String>();
        for (RouteDefinition route : routes) {
            routeStatuses.put(route.getId(), route.getStatus(camelContext).toString());
        }
        model.put("routeStatuses", routeStatuses);
        return "index.jsp";
    }
}

