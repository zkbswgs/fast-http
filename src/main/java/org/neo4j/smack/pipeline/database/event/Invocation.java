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
package org.neo4j.smack.pipeline.database.event;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.smack.pipeline.database.TransactionRegistry;
import org.neo4j.smack.routing.PathVariables;

public interface Invocation {
    
    public PathVariables getPathVariables();

    public <T> T getContent();
    
    public <T> T getContent(Class<T> type);

    public GraphDatabaseService getDB();
    
    public TransactionRegistry getTxRegistry();
    
    public long getTxId();
    
    public String getPath();

    // Path variable management
    
    public long getLongParameter(String nodeIdName, long defaultValue);

    public String getStringParameter(String nodePropertyKeyName);
    
    public String getStringParameter(String nodePropertyKeyName, String defaultValue);
    
}
