/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.file;

/**
 * Strategies for handling symbolic links. Either traverses the file/directory referenced by the symbolic link or
 * traverses the symbolic link itself.
 */
public enum SymbolicLinkStrategy {

    /**
     * Traverses the file or directory referenced by the symbolic link.
     */
    FOLLOW,

    /**
     * Preserves the symbolic link, without looking into its target.
     */
    PRESERVE

}
