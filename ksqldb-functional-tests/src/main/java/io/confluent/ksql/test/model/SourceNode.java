/*
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.test.model;

import static org.hamcrest.Matchers.allOf;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.annotations.VisibleForTesting;
import io.confluent.ksql.metastore.TypeRegistry;
import io.confluent.ksql.metastore.model.DataSource;
import io.confluent.ksql.metastore.model.KsqlStream;
import io.confluent.ksql.metastore.model.KsqlTable;
import io.confluent.ksql.parser.SchemaParser;
import io.confluent.ksql.schema.ksql.LogicalSchema;
import io.confluent.ksql.test.model.matchers.MetaStoreMatchers;
import io.confluent.ksql.test.tools.exceptions.InvalidFieldException;
import io.confluent.ksql.test.utils.JsonParsingUtil;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsInstanceOf;

@JsonDeserialize(using = SourceNode.Deserializer.class)
final class SourceNode {

  private final String name;
  private final String type;
  private final Optional<String> schema;
  private final Optional<KeyFormatNode> keyFormat;

  @VisibleForTesting
  SourceNode(
      final String name,
      final String type,
      final Optional<String> schema,
      final Optional<KeyFormatNode> keyFormat
  ) {
    this.name = Objects.requireNonNull(name, "name");
    this.type = Objects.requireNonNull(type, "type");
    this.schema = Objects.requireNonNull(schema, "schema");
    this.keyFormat = Objects.requireNonNull(keyFormat, "keyFormat");

    if (this.name.isEmpty()) {
      throw new InvalidFieldException("name", "missing or empty");
    }

    // Fail early:
    build();
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public Optional<KeyFormatNode> getKeyFormat() {
    return keyFormat;
  }

  public Optional<String> getSchema() {
    return schema;
  }

  @SuppressWarnings("unchecked")
  Matcher<? super DataSource> build() {
    if (name.isEmpty()) {
      throw new InvalidFieldException("name", "missing or empty");
    }

    final Matcher<DataSource> nameMatcher = MetaStoreMatchers
        .hasName(name);

    final Matcher<Object> typeMatcher = IsInstanceOf
        .instanceOf(toType(type));

    final Matcher<DataSource> schemaMatcher = schema
        .map(SourceNode::parseSchema)
        .map(Matchers::is)
        .map(MetaStoreMatchers::hasSchema)
        .orElse(null);

    final Matcher<DataSource> keyFormatMatcher = keyFormat
        .map(KeyFormatNode::build)
        .map(MetaStoreMatchers::hasKeyFormat)
        .orElse(null);

    final Matcher<DataSource>[] matchers = Stream
        .of(nameMatcher, typeMatcher, schemaMatcher, keyFormatMatcher)
        .filter(Objects::nonNull)
        .toArray(Matcher[]::new);

    return allOf(matchers);
  }

  private static Class<? extends DataSource> toType(final String type) {
    switch (type.toUpperCase()) {
      case "STREAM":
        return KsqlStream.class;

      case "TABLE":
        return KsqlTable.class;

      default:
        throw new InvalidFieldException("type", "must be either STREAM or TABLE");
    }
  }

  private static LogicalSchema parseSchema(final String text) {
    return SchemaParser.parse(text, TypeRegistry.EMPTY)
        .toLogicalSchema();
  }

  public static class Deserializer extends JsonDeserializer<SourceNode> {

    @Override
    public SourceNode deserialize(
        final JsonParser jp,
        final DeserializationContext ctxt
    ) throws IOException {
      final JsonNode node = jp.getCodec().readTree(jp);

      final String name = JsonParsingUtil.getRequired("name", node, jp, String.class);
      final String type = JsonParsingUtil.getRequired("type", node, jp, String.class);

      final Optional<String> rawSchema = JsonParsingUtil
          .getOptional("schema", node, jp, String.class);

      final Optional<KeyFormatNode> keyFormat = JsonParsingUtil
          .getOptional("keyFormat", node, jp, KeyFormatNode.class);

      return new SourceNode(name, type, rawSchema, keyFormat);
    }
  }
}