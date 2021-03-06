/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package com.google.errorprone.apply;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.DescriptionListener;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.Replacement;
import com.google.errorprone.fixes.Replacements;
import com.google.errorprone.matchers.Description;

import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implementation of a {@link Diff} that performs the modifications that are passed to its
 * {@link #onDescribed} method, with no formatting.
 * 
 * <p>If imports are changed, they are resorted as per Google Java style.
 * 
 * @author lowasser@google.com (Louis Wasserman)
 */
public final class DescriptionBasedDiff implements DescriptionListener, Diff {

  private final String sourcePath;
  private final JCCompilationUnit compilationUnit;
  private final Set<String> importsToAdd;
  private final Set<String> importsToRemove;
  private final EndPosTable endPositions;
  private final Replacements replacements = new Replacements();

  public static DescriptionBasedDiff create(JCCompilationUnit compilationUnit) {
    return new DescriptionBasedDiff(compilationUnit);
  }

  private DescriptionBasedDiff(JCCompilationUnit compilationUnit) {
    this.compilationUnit = checkNotNull(compilationUnit);
    this.sourcePath = compilationUnit.getSourceFile().toUri().getPath();
    this.importsToAdd = new LinkedHashSet<>();
    this.importsToRemove = new LinkedHashSet<>();
    this.endPositions = compilationUnit.endPositions;
  }

  @Override
  public String getRelevantFileName() {
    return sourcePath;
  }
  
  public boolean isEmpty() {
    return importsToAdd.isEmpty() && importsToRemove.isEmpty() && replacements.isEmpty();
  }

  @Override
  public void onDescribed(Description description) {
    // Use only first (most likely) suggested fix
    if (description.fixes.size() > 0) {
      handleFix(description.fixes.get(0));
    }
  }

  public void handleFix(Fix fix) {
    importsToAdd.addAll(fix.getImportsToAdd());
    importsToRemove.addAll(fix.getImportsToRemove());
    for (Replacement replacement : fix.getReplacements(endPositions)) {
      replacements.add(replacement);
    }
  }

  @Override
  public void applyDifferences(SourceFile sourceFile) throws DiffNotApplicableException {
    if (!importsToAdd.isEmpty() || !importsToRemove.isEmpty()) {
      ImportStatements importStatements = ImportStatements.create(compilationUnit);
      importStatements.addAll(importsToAdd);
      importStatements.removeAll(importsToRemove);
      replacements.add(
          Replacement.create(
              importStatements.getStartPos(),
              importStatements.getEndPos(),
              importStatements.toString()));
    }
    for (Replacement replacement : replacements.descending()) {
      sourceFile.replaceChars(replacement.startPosition(), replacement.endPosition(),
          replacement.replaceWith());
    }
  }
}
