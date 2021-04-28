// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class FinishElementInstruction extends Instruction {
  private final Set<DfaVariableValue> myVarsToFlush = new HashSet<>();
  private final PsiElement myElement;

  public FinishElementInstruction(PsiElement element) {
    myElement = element;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState state, InstructionVisitor visitor) {
    if (!myVarsToFlush.isEmpty()) {
      for (DfaVariableValue value : myVarsToFlush) {
        state.flushVariable(value, false);
      }
    }
    return nextInstruction(runner, state);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myVarsToFlush.isEmpty()) return this;
    var instruction = new FinishElementInstruction(myElement);
    for (DfaVariableValue var : myVarsToFlush) {
      instruction.myVarsToFlush.add(var.bindToFactory(factory));
    }
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Override
  public String toString() {
    return "FINISH " + (myElement == null ? "" : myElement) + (myVarsToFlush.isEmpty() ? "" : "; flushing " + myVarsToFlush);
  }

  public Set<DfaVariableValue> getVarsToFlush() {
    return myVarsToFlush;
  }
}
