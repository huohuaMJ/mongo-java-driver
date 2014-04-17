/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.operation;

import org.mongodb.CommandResult;
import org.mongodb.Decoder;
import org.mongodb.Document;
import org.mongodb.Encoder;
import org.mongodb.Function;
import org.mongodb.MongoFuture;
import org.mongodb.MongoNamespace;
import org.mongodb.session.Session;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mongodb.operation.DocumentHelper.putIfNotNull;
import static org.mongodb.operation.DocumentHelper.putIfNotZero;
import static org.mongodb.operation.DocumentHelper.putIfTrue;
import static org.mongodb.operation.OperationHelper.executeWrappedCommandProtocol;
import static org.mongodb.operation.OperationHelper.executeWrappedCommandProtocolAsync;
import static org.mongodb.operation.OperationHelper.transformResult;

public class FindAndReplaceOperation<T> implements AsyncOperation<T>, Operation<T> {
    private final MongoNamespace namespace;
    private final FindAndReplace<T> findAndReplace;
    private final CommandResultWithPayloadDecoder<T> resultDecoder;
    private final CommandWithPayloadEncoder<T> commandEncoder;

    public FindAndReplaceOperation(final MongoNamespace namespace, final FindAndReplace<T> findAndReplace, final Decoder<T> payloadDecoder,
                                   final Encoder<T> payloadEncoder) {
        this.namespace = namespace;
        this.findAndReplace = findAndReplace;
        resultDecoder = new CommandResultWithPayloadDecoder<T>(payloadDecoder, "value");
        commandEncoder = new CommandWithPayloadEncoder<T>("update", payloadEncoder);
    }

    @Override
    public T execute(final Session session) {
        CommandResult result = executeWrappedCommandProtocol(namespace, getCommand(), commandEncoder, resultDecoder, session);
        return transformResult(result, transformer());
    }

    @Override
    public MongoFuture<T> executeAsync(final Session session) {
        MongoFuture<CommandResult> result = executeWrappedCommandProtocolAsync(namespace, getCommand(), commandEncoder, resultDecoder,
                                                                               session);
        return transformResult(result, transformer());
    }

    private Function<CommandResult, T> transformer() {
        return new Function<CommandResult, T>() {
            @SuppressWarnings("unchecked")
            @Override
            public T apply(final CommandResult result) {
                return (T) result.getResponse().get("value");
            }
        };
    }

    private Document getCommand() {
        Document command = new Document("findandmodify", namespace.getCollectionName());
        putIfNotNull(command, "query", findAndReplace.getFilter());
        putIfNotNull(command, "fields", findAndReplace.getSelector());
        putIfNotNull(command, "sort", findAndReplace.getSortCriteria());
        putIfTrue(command, "new", findAndReplace.isReturnNew());
        putIfTrue(command, "upsert", findAndReplace.isUpsert());
        putIfNotZero(command, "maxTimeMS", findAndReplace.getOptions().getMaxTime(MILLISECONDS));

        command.put("update", findAndReplace.getReplacement());
        return command;
    }
}
