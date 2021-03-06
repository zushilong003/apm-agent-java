/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.kafka.helper.KafkaInstrumentationHeadersHelper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;

import javax.annotation.Nullable;
import java.util.List;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments the {@link ConsumerRecords#records(TopicPartition)} method
 */
public class ConsumerRecordsRecordListInstrumentation extends KafkaConsumerRecordsInstrumentation {
    public ConsumerRecordsRecordListInstrumentation(ElasticApmTracer tracer) {
        super(tracer);
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("records")
            .and(isPublic())
            .and(takesArgument(0, named("org.apache.kafka.common.TopicPartition")))
            .and(returns(List.class));
    }

    @Override
    public Class<?> getAdviceClass() {
        return ConsumerRecordsAdvice.class;
    }

    @SuppressWarnings("rawtypes")
    public static class ConsumerRecordsAdvice {

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        public static void wrapRecordList(@Nullable @Advice.Return(readOnly = false) List<ConsumerRecord> list) {
            if (tracer == null || tracer.currentTransaction() != null) {
                return;
            }

            //noinspection ConstantConditions,rawtypes
            KafkaInstrumentationHeadersHelper<ConsumerRecord, ProducerRecord> kafkaInstrumentationHelper =
                kafkaInstrHeadersHelperManager.getForClassLoaderOfClass(KafkaProducer.class);
            if (list != null && kafkaInstrumentationHelper != null) {
                list = kafkaInstrumentationHelper.wrapConsumerRecordList(list);
            }
        }
    }
}
