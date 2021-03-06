/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.jet.impl;

import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.core.DAG;
import com.hazelcast.jet.core.JobStatus;
import com.hazelcast.jet.impl.operation.GetJobStatusOperation;
import com.hazelcast.jet.impl.operation.JoinSubmittedJobOperation;
import com.hazelcast.jet.impl.operation.SubmitJobOperation;
import com.hazelcast.logging.LoggingService;
import com.hazelcast.nio.Address;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.Operation;
import com.hazelcast.spi.impl.NodeEngineImpl;
import com.hazelcast.spi.serialization.SerializationService;

import javax.annotation.Nonnull;

import static com.hazelcast.jet.impl.util.Util.uncheckCall;

/**
 * {@link com.hazelcast.jet.Job} proxy on member.
 */
public class JobProxy extends AbstractJobProxy<NodeEngineImpl> {

    public JobProxy(NodeEngineImpl nodeEngine, long jobId) {
        super(nodeEngine, jobId);
    }

    public JobProxy(NodeEngineImpl engine, long jobId, DAG dag, JobConfig config) {
        super(engine, jobId, dag, config);
    }

    @Nonnull @Override
    public JobStatus getJobStatus() {
        return uncheckCall(
                () -> this.<JobStatus>invokeOp(
                        new GetJobStatusOperation(getJobId(), shouldRetryJobStatus())
                ).get()
        );
    }

    @Override
    protected ICompletableFuture<Void> invokeSubmitJob(Data dag, JobConfig config) {
        return invokeOp(new SubmitJobOperation(getJobId(), dag, config));
    }

    @Override
    protected ICompletableFuture<Void> invokeJoinJob() {
        return invokeOp(new JoinSubmittedJobOperation(getJobId()));
    }

    @Override
    protected Address masterAddress() {
        return container().getMasterAddress();
    }

    @Override
    protected SerializationService serializationService() {
        return container().getSerializationService();
    }

    @Override
    protected LoggingService loggingService() {
        return container().getLoggingService();
    }

    private <T> ICompletableFuture<T> invokeOp(Operation op) {
        return container()
                .getOperationService()
                .createInvocationBuilder(JetService.SERVICE_NAME, op, masterAddress())
                .invoke();
    }
}
