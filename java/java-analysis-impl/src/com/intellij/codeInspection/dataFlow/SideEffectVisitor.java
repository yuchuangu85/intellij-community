// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.java.JavaDfaInstructionVisitor;
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * A visitor which cancels a dataflow once side effect occurs
 * @see DataFlowRunner#cancel()
 */
public class SideEffectVisitor extends JavaDfaInstructionVisitor implements DfaInterceptor<PsiExpression> {
  private final @NotNull DataFlowRunner myRunner;

  protected SideEffectVisitor(@NotNull DataFlowRunner runner) {
    myRunner = runner;
  }
  
  /**
   * Override this method to allow some variable modifications which do not count as side effects
   *
   * @param variable variable to test
   * @return true if variable modification is not allowed
   */
  protected boolean isModificationAllowed(DfaVariableValue variable) {
    return false;
  }

  @Override
  public DfaInstructionState[] visitFlushFields(FlushFieldsInstruction instruction,
                                                DataFlowRunner runner,
                                                DfaMemoryState memState) {
    runner.cancel();
    return super.visitFlushFields(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction,
                                                  DataFlowRunner runner,
                                                  DfaMemoryState memState) {
    if (!isModificationAllowed(instruction.getVariable())) {
      runner.cancel();
    }
    return super.visitFlushVariable(instruction, runner, memState);
  }

  @Override
  public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                          @NotNull DfaValue value,
                          @NotNull ThreeState failed,
                          @NotNull DfaMemoryState state) {
    if (failed != ThreeState.NO) {
      myRunner.cancel();
    }
  }

  @Override
  public DfaInstructionState @NotNull [] visitControlTransfer(@NotNull ControlTransferInstruction instruction,
                                                              @NotNull DataFlowRunner runner, @NotNull DfaMemoryState state) {
    if (instruction instanceof ReturnInstruction && (((ReturnInstruction)instruction).getAnchor() != null ||
                                                     ((ReturnInstruction)instruction).isViaException())) {
      runner.cancel();
    }
    return super.visitControlTransfer(instruction, runner, state);
  }

  @Override
  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                               DataFlowRunner runner,
                                               DfaMemoryState memState) {
    if (!instruction.getMutationSignature().isPure()) {
      runner.cancel();
    }
    return super.visitMethodCall(instruction, runner, memState);
  }

  @Override
  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue src = memState.getStackValue(1);
    if (!(src instanceof DfaVariableValue) || !isModificationAllowed((DfaVariableValue)src)) {
      runner.cancel();
    }
    return super.visitAssign(instruction, runner, memState);
  }
}
