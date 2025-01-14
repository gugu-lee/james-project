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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.MAILBOX_BASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.UIDVALIDITY;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MailboxBaseTupleUtil;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxDAO {
    private final CassandraAsyncExecutor executor;
    private final MailboxBaseTupleUtil mailboxBaseTupleUtil;
    private final PreparedStatement readStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement updateUidValidityStatement;
    private final ConsistencyLevel consistencyLevel;

    @Inject
    public CassandraMailboxDAO(Session session, CassandraTypesProvider typesProvider,
                               CassandraConsistenciesConfiguration consistenciesConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.consistencyLevel = consistenciesConfiguration.getRegular();
        this.mailboxBaseTupleUtil = new MailboxBaseTupleUtil(typesProvider);
        this.insertStatement = prepareInsert(session);
        this.updateStatement = prepareUpdate(session);
        this.updateUidValidityStatement = prepareUpdateUidValidity(session);
        this.deleteStatement = prepareDelete(session);
        this.listStatement = prepareList(session);
        this.readStatement = prepareRead(session);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(NAME, bindMarker(NAME))
            .value(UIDVALIDITY, bindMarker(UIDVALIDITY))
            .value(MAILBOX_BASE, bindMarker(MAILBOX_BASE)));
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(MAILBOX_BASE, bindMarker(MAILBOX_BASE)))
            .and(set(NAME, bindMarker(NAME)))
            .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareUpdateUidValidity(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(UIDVALIDITY, bindMarker(UIDVALIDITY)))
            .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select(FIELDS).from(TABLE_NAME));
    }

    private PreparedStatement prepareRead(Session session) {
        return session.prepare(select(FIELDS).from(TABLE_NAME)
            .where(eq(ID, bindMarker(ID))));
    }

    public Mono<Void> save(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return executor.executeVoid(insertStatement.bind()
            .setUUID(ID, cassandraId.asUuid())
            .setString(NAME, mailbox.getName())
            .setLong(UIDVALIDITY, mailbox.getUidValidity().asLong())
            .setUDTValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailbox.getNamespace(), mailbox.getUser())));
    }

    public Mono<Void> updatePath(CassandraId mailboxId, MailboxPath mailboxPath) {
        return executor.executeVoid(updateStatement.bind()
            .setUUID(ID, mailboxId.asUuid())
            .setString(NAME, mailboxPath.getName())
            .setUDTValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser())));
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUUID(ID, mailboxId.asUuid()));
    }

    public Mono<Mailbox> retrieveMailbox(CassandraId mailboxId) {
        return executor.executeSingleRow(readStatement.bind()
            .setUUID(ID, mailboxId.asUuid())
            .setConsistencyLevel(consistencyLevel))
            .flatMap(row -> mailboxFromRow(row, mailboxId));
    }

    private Mono<Mailbox> mailboxFromRow(Row row, CassandraId cassandraId) {
        return sanitizeUidValidity(cassandraId, row.getLong(UIDVALIDITY))
            .map(uidValidity -> {
                UDTValue mailboxBase = row.getUDTValue(MAILBOX_BASE);
                return new Mailbox(
                    new MailboxPath(
                        mailboxBase.getString(CassandraMailboxTable.MailboxBase.NAMESPACE),
                        Username.of(mailboxBase.getString(CassandraMailboxTable.MailboxBase.USER)),
                        row.getString(NAME)),
                    uidValidity,
                    cassandraId);
            });
    }
    
    private Mono<UidValidity> sanitizeUidValidity(CassandraId cassandraId, long uidValidityAsLong) {
        if (!UidValidity.isValid(uidValidityAsLong)) {
            UidValidity newUidValidity = UidValidity.generate();
            return updateUidValidity(cassandraId, newUidValidity)
                .then(Mono.just(newUidValidity));
        }
        return Mono.just(UidValidity.of(uidValidityAsLong));
    }

    /**
     * Expected concurrency issue in the absence of performance expensive LightWeight transaction
     * As the Uid validity is updated only when equal to 0 (1 chance out of 4 billion) the benefits of LWT don't
     * outweigh the performance costs
     */
    private Mono<Void> updateUidValidity(CassandraId cassandraId, UidValidity uidValidity) {
        return executor.executeVoid(updateUidValidityStatement.bind()
                .setUUID(ID, cassandraId.asUuid())
                .setLong(UIDVALIDITY, uidValidity.asLong()));
    }

    public Flux<Mailbox> retrieveAllMailboxes() {
        return executor.executeRows(listStatement.bind())
            .flatMap(this::toMailboxWithId, DEFAULT_CONCURRENCY);
    }

    private Mono<Mailbox> toMailboxWithId(Row row) {
        return mailboxFromRow(row, CassandraId.of(row.getUUID(ID)));
    }
}
