/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.kafkastore.kafka;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.common.annotation.Blocking;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.hibernate.exception.ConstraintViolationException;
import org.jboss.pnc.kafkastore.mapper.BuildStageRecordMapper;
import org.jboss.pnc.kafkastore.model.BuildStageRecord;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.transaction.TransactionManager;
import javax.transaction.Transactional;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Consume from a Kafka topic, parse the data and store it in the database
 */
@ApplicationScoped
@Slf4j
public class Consumer {

    private static final String className = Consumer.class.getName();

    @Inject
    BuildStageRecordMapper mapper;

    @Inject
    MeterRegistry registry;

    private Counter errCounter;

    @PostConstruct
    void initMetrics() {
        errCounter = registry.counter(className + ".error.count");
    }

    /**
     * Main method to consume information from a Kafka topic into the database.
     *
     * @param jsonString
     * @throws Exception
     */
    @Timed
    @Blocking
    @Incoming("duration")
    @Transactional
    public void consume(String jsonString) {
        try {
            Optional<BuildStageRecord> buildStageRecord = mapper.mapKafkaMsgToBuildStageRecord(jsonString);

            buildStageRecord.ifPresent(br -> {
                log.info(br.toString());
                br.persistAndFlush();
            });

        } catch (PersistenceException e) {
            errCounter.increment();
            if (e.getCause() instanceof ConstraintViolationException) {
                log.error("Kafka-store is receiving duplicate messages", e);
            } else {
                log.error("Error while saving data", e);
            }
        } catch (Exception e) {
            errCounter.increment();
            log.error("Error while saving data", e);
        }
    }
}
