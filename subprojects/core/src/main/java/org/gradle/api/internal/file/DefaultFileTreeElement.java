/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.filesystem.Stat;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.InputStream;

public class DefaultFileTreeElement extends AbstractFileTreeElement {
    private final File file;
    private final RelativePath relativePath;
    private final Stat stat;

    public DefaultFileTreeElement(File file, RelativePath relativePath, Chmod chmod, Stat stat) {
        this(file, relativePath, typeOf(file), chmod, stat);
    }

    public DefaultFileTreeElement(File file, RelativePath relativePath, Chmod chmod, Type type, Stat stat) {
        super(chmod, type);
        this.file = file;
        this.relativePath = relativePath;
        this.stat = stat;
    }

    public static DefaultFileTreeElement of(File file, FileSystem fileSystem) {
        RelativePath path = RelativePath.parse(!file.isDirectory(), file.getAbsolutePath());
        return new DefaultFileTreeElement(file, path, typeOf(file), fileSystem, fileSystem);
    }

    private static Type typeOf(File file) {
        Path path = file.toPath();

        if (path.isSymbolicLink())
            return Type.SYMBOLIC_LINK;
        else if (path.isDirectory())
            return Type.DIRECTORY;
        else
            return Type.REGULAR_FILE;
    }

    public File getFile() {
        return file;
    }

    public String getDisplayName() {
        return "file '" + file + "'";
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public long getSize() {
        return file.length();
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    public InputStream open() {
        return GFileUtils.openInputStream(file);
    }

    public RelativePath getRelativePath() {
        return relativePath;
    }

    public int getMode() {
        return stat.getUnixMode(file);
    }
}
