/*
 * Copyright 2016 the original author or authors.
 *
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
 */
package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.SymlinkAwareFileVisitor;

/**
 * A helper base class for a minimal file tree implementation. Assumes that the classes that extend this
 * class will treat all symbolic links as regular files.
 */
public abstract class AbstractMinimalFileTree implements MinimalFileTree {
    /**
     * Visits the elements of this tree, in depth-first prefix order, handling symbolic links as regular
     * files.
     */
    public void visit(SymlinkAwareFileVisitor visitor) {
        visit((FileVisitor) visitor);
    }
}
