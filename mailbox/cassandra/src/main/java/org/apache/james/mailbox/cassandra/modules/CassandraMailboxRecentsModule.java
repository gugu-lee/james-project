/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.cassandra.modules;

import static com.datastax.driver.core.DataType.bigint;
import static com.datastax.driver.core.DataType.timeuuid;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxRecentsTable;

import com.datastax.driver.core.schemabuilder.SchemaBuilder;

public interface CassandraMailboxRecentsModule {
    CassandraModule MODULE = CassandraModule.table(CassandraMailboxRecentsTable.TABLE_NAME)
        .comment("Denormalisation table. This table holds for each mailbox the messages marked as RECENT. This" +
            " is a SELECT optimisation.")
        .options(options -> options
            .compactionOptions(SchemaBuilder.sizedTieredStategy())
            .gcGraceSeconds(0)
            .caching(SchemaBuilder.KeyCaching.ALL,
                SchemaBuilder.rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> statement
            .addPartitionKey(CassandraMailboxRecentsTable.MAILBOX_ID, timeuuid())
            .addClusteringColumn(CassandraMailboxRecentsTable.RECENT_MESSAGE_UID, bigint()))
        .build();
}
