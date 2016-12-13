/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.SymlinkAwareFileVisitor;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.util.GUtil;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directory walker supporting {@link Spec}s for includes and excludes.
 * The file system is traversed in depth-first prefix order - all files in a directory will be
 * visited before any child directory is visited.
 *
 * A file or directory will only be visited if it matches all includes and no
 * excludes.
 */
public class DirectoryFileTree implements MinimalFileTree, PatternFilterableFileTree, RandomAccessFileCollection, LocalFileTree, DirectoryTree {
    private static final Logger LOGGER = Logging.getLogger(DirectoryFileTree.class);
    private static final Factory<DirectoryWalker> DEFAULT_DIRECTORY_WALKER_FACTORY = new DefaultDirectoryWalkerFactory();

    private final File dir;
    private final PatternSet patternSet;
    private final boolean postfix;
    private final FileSystem fileSystem;
    private final Factory<DirectoryWalker> directoryWalkerFactory;

    /**
     * Use {@link DirectoryFileTreeFactory} instead.
     */
    @Deprecated
    public DirectoryFileTree(File dir) {
        this(dir, new PatternSet(), FileSystems.getDefault());
    }

    /**
     * Use {@link DirectoryFileTreeFactory} instead.
     */
    @Deprecated
    public DirectoryFileTree(File dir, PatternSet patternSet) {
        this(dir, patternSet, FileSystems.getDefault());
    }

    public DirectoryFileTree(File dir, PatternSet patternSet, FileSystem fileSystem) {
        this(FileUtils.canonicalize(dir), patternSet, DEFAULT_DIRECTORY_WALKER_FACTORY, fileSystem, false);
    }

    DirectoryFileTree(File dir, PatternSet patternSet, Factory<DirectoryWalker> directoryWalkerFactory, FileSystem fileSystem, boolean postfix) {
        this.patternSet = patternSet;
        this.dir = dir;
        this.directoryWalkerFactory = directoryWalkerFactory;
        this.fileSystem = fileSystem;
        this.postfix = postfix;
    }

    public String getDisplayName() {
        String includes = patternSet.getIncludes().isEmpty() ? "" : String.format(" include %s", GUtil.toString(patternSet.getIncludes()));
        String excludes = patternSet.getExcludes().isEmpty() ? "" : String.format(" exclude %s", GUtil.toString(patternSet.getExcludes()));
        return String.format("directory '%s'%s%s", dir, includes, excludes);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public PatternSet getPatterns() {
        return patternSet;
    }

    public File getDir() {
        return dir;
    }

    public Collection<DirectoryFileTree> getLocalContents() {
        return Collections.singletonList(this);
    }

    public DirectoryFileTree filter(PatternFilterable patterns) {
        PatternSet patternSet = this.patternSet.intersect();
        patternSet.copyFrom(patterns);
        return new DirectoryFileTree(dir, patternSet, directoryWalkerFactory, fileSystem, postfix);
    }

    public boolean contains(File file) {
        return DirectoryTrees.contains(fileSystem, this, file) && file.isFile();
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        builder.add(this);
    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visit(visitor);
    }

    private SymlinkAwareFileVisitor wrapFileVisitor(final FileVisitor visitor) {
        return new SymlinkAwareFileVisitor() {
            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                visitor.visitFile(fileDetails);
            }

            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                visitor.visitDir(dirDetails);
            }

            @Override
            public void visitSymbolicLink(FileVisitDetails symbolicLinkDetails) {
                visitor.visitFile(symbolicLinkDetails);
            }
        };
    }

    public void visit(final FileVisitor visitor) {
        visitFrom(wrapFileVisitor(visitor), dir, RelativePath.EMPTY_ROOT);
    }

    public void visitFollowingSymbolicLinks(FileVisitor visitor) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void visit(SymlinkAwareFileVisitor visitor) {
        visitFrom(visitor, dir, RelativePath.EMPTY_ROOT);
    }

    /**
     * Process the specified file or directory.  If it is a directory, then its contents
     * (but not the directory itself) will be checked with {@link #isAllowed(FileTreeElement, Spec)} and notified to
     * the listener.  If it is a file, the file will be checked and notified.
     */
    public void visitFrom(FileVisitor visitor, File fileOrDirectory, RelativePath path) {
        visitFrom(wrapFileVisitor(visitor), fileOrDirectory, path);
    }

    /**
     * Process the specified file or directory.  If it is a directory, then its contents
     * (but not the directory itself) will be checked with {@link #isAllowed(FileTreeElement, Spec)} and notified to
     * the listener.  If it is a file, the file will be checked and notified.
     */
    public void visitFrom(SymlinkAwareFileVisitor visitor, File fileOrDirectory, RelativePath path) {
        AtomicBoolean stopFlag = new AtomicBoolean();
        Spec<FileTreeElement> spec = patternSet.getAsSpec();
        if (fileOrDirectory.exists()) {
            if (Files.isSymbolicLink(fileOrDirectory.toPath())) {
                processSymbolicLink(fileOrDirectory, visitor, spec, stopFlag);
            } else if (fileOrDirectory.isFile()) {
                processSingleFile(fileOrDirectory, visitor, spec, stopFlag);
            } else {
                walkDir(fileOrDirectory, path, visitor, spec, stopFlag);
            }
        } else {
            LOGGER.info("file or directory '{}', not found", fileOrDirectory);
        }
    }

    private void processSymbolicLink(File file, SymlinkAwareFileVisitor visitor, Spec<FileTreeElement> spec, AtomicBoolean stopFlag) {
        RelativePath path = new RelativePath(true, file.getName());
        FileVisitDetails details = new DefaultFileVisitDetails(file, path, stopFlag, fileSystem, fileSystem, false);
        if (isAllowed(details, spec)) {
            visitor.visitSymbolicLink(details);
        }
    }

    private void processSingleFile(File file, FileVisitor visitor, Spec<FileTreeElement> spec, AtomicBoolean stopFlag) {
        RelativePath path = new RelativePath(true, file.getName());
        FileVisitDetails details = new DefaultFileVisitDetails(file, path, stopFlag, fileSystem, fileSystem, false);
        if (isAllowed(details, spec)) {
            visitor.visitFile(details);
        }
    }

    private void walkDir(File file, RelativePath path, FileVisitor visitor, Spec<FileTreeElement> spec, AtomicBoolean stopFlag) {
        directoryWalkerFactory.create().walkDir(file, path, visitor, spec, stopFlag, postfix);
    }

    static boolean isAllowed(FileTreeElement element, Spec<? super FileTreeElement> spec) {
        return spec.isSatisfiedBy(element);
    }

    /**
     * Returns a copy that traverses directories (but not files) in postfix rather than prefix order.
     *
     * @return {@code this}
     */
    public DirectoryFileTree postfix() {
        if (postfix) {
            return this;
        }
        return new DirectoryFileTree(dir, patternSet, directoryWalkerFactory, fileSystem, true);
    }

    public PatternSet getPatternSet() {
        return patternSet;
    }

}
