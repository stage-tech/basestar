package io.basestar.util;

/*-
 * #%L
 * basestar-core
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

import lombok.EqualsAndHashCode;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
public class Path extends AbstractPath<Path> implements Comparable<Path> {

    public static final Path EMPTY = Path.of();

    public static final char DELIMITER = '/';

    protected Path(final String ... parts) {

        super(parts);
    }

    protected Path(final List<String> parts) {

        super(parts);
    }

    @Override
    protected char delimiter() {

        return DELIMITER;
    }

    @Override
    protected Path create() {

        return EMPTY;
    }

    public File toFile() {

        return new File(toString(File.pathSeparator));
    }

    @Override
    protected Path create(final List<String> parts) {

        return new Path(parts);
    }

    public static Path parse(final String str) {

        return new Path(splitter(DELIMITER).splitToList(str));
    }

    public static Path parse(final String str, final char delimiter) {

        return new Path(splitter(delimiter).splitToList(str));
    }

    public static Path of(final String ... parts) {

        return new Path(Arrays.asList(parts));
    }

    public static Path of(final List<String> parts) {

        return new Path(parts);
    }

    public static Path of(final AbstractPath<?> path) {

        return new Path(path.getParts());
    }

    public static Path of(final File file) {

        return parse(file.getAbsolutePath(), File.separatorChar);
    }

    public static Path empty() {

        return new Path(Collections.emptyList());
    }
}
