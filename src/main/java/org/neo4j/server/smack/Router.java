/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.smack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.MatchResult;

import org.apache.log4j.Logger;
import org.neo4j.server.smack.core.RequestEvent;

import com.sun.jersey.server.impl.uri.PathPattern;
import com.sun.jersey.server.impl.uri.PathTemplate;

public class Router extends RoutingDefinition {

    private RouteEntry [] routes;
    private static final Logger logger=Logger.getLogger(Router.class);
    
    public Endpoint route(RequestEvent event)
    {
        String path = event.getPath();
        for(RouteEntry route : routes)
        {
            MatchResult matchResult = route.pattern.match(path);
            if(matchResult != null)
            {
                event.setPathVariables(new PathVariables(matchResult, route.pattern));
                Endpoint endpoint = route.getEndpoint(event.getVerb());
                if(endpoint != null) {
                    return endpoint;   
                }
                throw new ResourceNotFoundException("Path '" + path + "' does not support '"+event.getVerb()+"'." );
            }
        }
        throw new ResourceNotFoundException("Path '" + path + "' not found." );
    }
    
    public void compileRoutes() {
        Map<String, RouteEntry> routeMap = new LinkedHashMap<String, RouteEntry>();

        for(RouteDefinitionEntry routeDef : getRouteDefinitionEntries())
        {
            if(!routeMap.containsKey(routeDef.getPath()))
            {
                RouteEntry route = new RouteEntry();
                final PathTemplate template = new PathTemplate(routeDef.getPath());
                route.pattern = new PathPattern(template,"");
                routeMap.put(routeDef.getPath(), route);
            }
            logger.debug("Adding Route: "+routeDef.getEndpoint().getVerb() +" to: "+ routeDef.getPath());
            
            RouteEntry route = routeMap.get(routeDef.getPath());
            route.setEndpoint(routeDef.getEndpoint().getVerb(), routeDef.getEndpoint());
        }
        
        routes = new RouteEntry[routeMap.size()];
        int i = 0;
        for(String path : routeMap.keySet()) {
            routes[i] = routeMap.get(path);
            i++;
        }
    }
}
