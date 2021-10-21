/* Copyright (c) 2021 vesoft inc. All rights reserved.
 *
 * This source code is licensed under Apache 2.0 License,
 * attached with Common Clause Condition 1.0, found in the LICENSES directory.
 */

package org.apache.flink.connector.nebula.sink;

import com.vesoft.nebula.client.graph.data.ResultSet;
import com.vesoft.nebula.client.graph.net.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.flink.connector.nebula.statement.ExecutionOptions;
import org.apache.flink.connector.nebula.statement.VertexExecutionOptions;
import org.apache.flink.connector.nebula.utils.NebulaVertex;
import org.apache.flink.connector.nebula.utils.NebulaVertices;
import org.apache.flink.connector.nebula.utils.VidTypeEnum;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NebulaVertexBatchExecutor<T> extends NebulaBatchExecutor<T> {
    private static final Logger LOG = LoggerFactory.getLogger(NebulaVertexBatchExecutor.class);

    private final List<NebulaVertex> nebulaVertexList;

    public NebulaVertexBatchExecutor(ExecutionOptions executionOptions,
                                     VidTypeEnum vidType, Map<String, Integer> schema) {
        super(executionOptions, vidType, schema);
        nebulaVertexList = new ArrayList<>();
    }

    /**
     * put record into buffer
     *
     * @param record represent vertex or edge
     */
    @Override
    void addToBatch(T record) {
        NebulaRowVertexOutputFormatConverter converter = new NebulaRowVertexOutputFormatConverter(
                (VertexExecutionOptions) executionOptions, vidType, schema);
        NebulaVertex vertex = converter.createVertex((Row) record, executionOptions.getPolicy());
        if (vertex == null) {
            return;
        }
        nebulaVertexList.add(vertex);
    }

    @Override
    String executeBatch(Session session) {
        NebulaVertices nebulaVertices = new NebulaVertices(executionOptions.getLabel(),
                executionOptions.getFields(), nebulaVertexList, executionOptions.getPolicy());
        // generate the write ngql statement
        String statement = null;
        switch (executionOptions.getWriteMode()) {
            case INSERT:
                statement = nebulaVertices.getInsertStatement();
                break;
            case UPDATE:
                statement = nebulaVertices.getUpdateStatement();
                break;
            case DELETE:
                statement = nebulaVertices.getDeleteStatement();
                break;
            default:
                throw new IllegalArgumentException("write mode is not supported");
        }
        LOG.debug("write statement={}", statement);

        // execute ngql statement
        ResultSet execResult = null;
        try {
            execResult = session.execute(statement);
        } catch (Exception e) {
            LOG.error("write data error, ", e);
            nebulaVertexList.clear();
            return statement;
        }

        if (execResult.isSucceeded()) {
            LOG.debug("write success");
        } else {
            LOG.error("write data failed: {}", execResult.getErrorMessage());
            nebulaVertexList.clear();
            return statement;
        }
        nebulaVertexList.clear();
        return null;
    }

}