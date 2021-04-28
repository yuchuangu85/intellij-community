/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.dataFlow.lang.ir.inst;


import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class ConditionalGotoInstruction extends Instruction implements BranchingInstruction {
  private ControlFlow.ControlFlowOffset myOffset;
  private final boolean myIsNegated;
  private final PsiElement myAnchor;

  public ConditionalGotoInstruction(ControlFlow.ControlFlowOffset offset, boolean isNegated, @Nullable PsiElement psiAnchor) {
    myAnchor = psiAnchor;
    myOffset = offset;
    myIsNegated = isNegated;
  }

  public boolean isNegated() {
    return myIsNegated;
  }

  @Nullable
  public PsiElement getPsiAnchor() {
    return myAnchor;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitConditionalGoto(this, runner, stateBefore);
  }

  public String toString() {
    return "IF_" + (isNegated() ? "NE" : "EQ") + " " + getOffset();
  }

  public boolean isTarget(boolean whenTrueOnStack, Instruction target) {
    return target.getIndex() == (whenTrueOnStack == myIsNegated ? getIndex() + 1 : getOffset());
  }

  public int getOffset() {
    return myOffset.getInstructionOffset();
  }

  public void setOffset(final int offset) {
    myOffset = new ControlFlow.FixedOffset(offset);
  }
}
