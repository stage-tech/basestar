package io.basestar.spark.transform;

/*-
 * #%L
 * basestar-spark
 * %%
 * Copyright (C) 2019 - 2020 Basestar.IO
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.basestar.spark.util.SparkRowUtils;
import io.basestar.spark.util.SparkUtils;
import io.basestar.util.Nullsafe;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.encoders.RowEncoder;
import org.apache.spark.sql.types.StructType;


/**
 * Force the input to match the field order in the provided schema.
 */

public class ConformTransform implements Transform<Dataset<Row>, Dataset<Row>> {

    private final StructType structType;

    @lombok.Builder(builderClassName = "Builder")
    public ConformTransform(final StructType structType) {

        this.structType = Nullsafe.require(structType);
    }

    @Override
    public Dataset<Row> accept(final Dataset<Row> input) {

        final StructType structType = this.structType;
        return input.map(
                SparkUtils.map(row -> SparkRowUtils.conform(row, structType)),
                RowEncoder.apply(structType)
        );
    }
}
