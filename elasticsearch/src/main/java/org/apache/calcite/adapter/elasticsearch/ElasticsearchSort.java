/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link org.apache.calcite.rel.core.Sort}
 * relational expression in Elasticsearch.
 */
public class ElasticsearchSort extends Sort implements ElasticsearchRel {
  ElasticsearchSort(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
      RelCollation collation, @Nullable RexNode offset,
      @Nullable RexNode fetch) {
    super(cluster, traitSet, input, collation, offset, fetch);
    assert getConvention() == ElasticsearchRel.CONVENTION;
    assert getConvention() == input.getConvention();
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    return cost.multiplyBy(0.05);
  }

  @Override public Sort copy(RelTraitSet traitSet, RelNode input,
      RelCollation relCollation, @Nullable RexNode offset,
      @Nullable RexNode fetch) {
    return new ElasticsearchSort(getCluster(), traitSet, input, collation,
        offset, fetch);
  }

  @Override public void implement(Implementor implementor) {
    implementor.visitChild(0, getInput());
    final List<RelDataTypeField> fields = getRowType().getFieldList();

    for (RelFieldCollation fieldCollation : collation.getFieldCollations()) {
      final String name = fields.get(fieldCollation.getFieldIndex()).getName();
      final String rawName = implementor.expressionItemMap.getOrDefault(name, name);
      // if nulls order is not specified, default NULLS LAST/FIRST for ASC/DESC
      implementor.addNullsSort(rawName, fieldCollation.nullDirection);
      implementor.addSort(rawName, fieldCollation.getDirection());
    }

    if (offset != null) {
      implementor.offset(RexLiteral.numberValue(offset).longValue());
    }

    if (fetch != null) {
      implementor.fetch(RexLiteral.numberValue(fetch).longValue());
    }
  }

}
