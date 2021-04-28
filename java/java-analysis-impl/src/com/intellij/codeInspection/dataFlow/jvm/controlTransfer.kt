// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package com.intellij.codeInspection.dataFlow.jvm

import com.intellij.codeInspection.dataFlow.DataFlowRunner
import com.intellij.codeInspection.dataFlow.DfaInstructionState
import com.intellij.codeInspection.dataFlow.DfaMemoryState
import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.inst.ControlTransferInstruction
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor
import com.intellij.psi.*

interface JvmTransferTarget : DfaControlTransferValue.TransferTarget {
  /** @return next instruction states assuming no traps */
  fun dispatch(state: DfaMemoryState, runner: DataFlowRunner) : List<DfaInstructionState> = emptyList()
}
data class ExceptionTransfer(val throwable: TypeConstraint) : JvmTransferTarget {
  override fun toString(): String = "Exception($throwable)"
}
data class InstructionTransfer(val offset: ControlFlow.ControlFlowOffset, private val varsToFlush: List<VariableDescriptor>) : JvmTransferTarget {
  override fun dispatch(state: DfaMemoryState, runner: DataFlowRunner): List<DfaInstructionState> {
    val varFactory = runner.factory.varFactory
    varsToFlush.forEach { desc -> state.flushVariable(varFactory.createVariableValue(desc)) }
    return listOf(DfaInstructionState(runner.getInstruction(offset.instructionOffset), state))
  }

  override fun getPossibleTargets(): List<Int> = listOf(offset.instructionOffset)
  override fun toString(): String = "-> $offset"
}
// ExitFinallyTransfer formally depends on enterFinally that has backLinks which are instructions bound to the DfaValueFactory
// however, we actually use only instruction offsets from there, and binding to another factory does not change the offsets.
data class ExitFinallyTransfer(private val enterFinally: JvmTrap.EnterFinally) : JvmTransferTarget {
  override fun getPossibleTargets(): Set<Int> = enterFinally.backLinks.asIterable().flatMap { it.getPossibleTargetIndices() }
    .filter { index -> index != enterFinally.jumpOffset.instructionOffset }.toSet()

  override fun dispatch(state: DfaMemoryState, runner: DataFlowRunner): List<DfaInstructionState> {
    return ControlTransferHandler.dispatch(state, runner, state.pop() as DfaControlTransferValue)
  }

  override fun toString(): String = "ExitFinally"
}
object ReturnTransfer : JvmTransferTarget {
  override fun toString(): String = "Return"
}

sealed class JvmTrap(private val anchor: PsiElement) : DfaControlTransferValue.Trap {
  open fun link(instruction: ControlTransferInstruction) {}

  abstract fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState>
  override fun toString(): String = javaClass.simpleName!!
  override fun getAnchor(): PsiElement = anchor

  class TryCatch(tryStatement: PsiTryStatement, val clauses: LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset>)
    : JvmTrap(tryStatement) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      val target = handler.target
      return if (target is ExceptionTransfer) handler.processCatches(target.throwable, clauses)
      else handler.doDispatch()
    }

    override fun getPossibleTargets() = clauses.values.map { it.instructionOffset }
    override fun toString(): String = "${super.toString()} -> ${clauses.values}"
  }
  class TryCatchAll(anchor: PsiElement, val target : ControlFlow.ControlFlowOffset)
    : JvmTrap(anchor) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      return if (handler.target is ExceptionTransfer)
        listOf(DfaInstructionState(handler.runner.getInstruction(target.instructionOffset), handler.state))
      else handler.doDispatch()
    }

    override fun getPossibleTargets() = listOf(target.instructionOffset)
    override fun toString(): String = "${super.toString()} -> ${target}"
  }
  abstract class EnterFinally(anchor: PsiElement, val jumpOffset: ControlFlow.ControlFlowOffset): JvmTrap(anchor) {
    internal val backLinks = ArrayList<ControlTransferInstruction>()

    override fun link(instruction: ControlTransferInstruction) {
      backLinks.add(instruction)
    }

    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      handler.state.push(handler.runner.factory.controlTransfer(handler.target, handler.traps))
      return listOf(DfaInstructionState(handler.runner.getInstruction(jumpOffset.instructionOffset), handler.state))
    }

    override fun getPossibleTargets() = listOf(jumpOffset.instructionOffset)
    override fun toString(): String = "${super.toString()} -> $jumpOffset"
  }
  class TryFinally(finallyBlock: PsiCodeBlock, jumpOffset: ControlFlow.ControlFlowOffset): EnterFinally(finallyBlock, jumpOffset)
  class TwrFinally(resourceList: PsiResourceList, jumpOffset: ControlFlow.ControlFlowOffset) : EnterFinally(resourceList, jumpOffset) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> =
      if (handler.target is ExceptionTransfer) handler.doDispatch()
      else super.dispatch(handler)
  }
  class InsideFinally(finallyBlock: PsiElement): JvmTrap(finallyBlock) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      handler.state.pop() as DfaControlTransferValue
      return handler.doDispatch()
    }
  }
  class InsideInlinedBlock(block: PsiCodeBlock): JvmTrap(block) {
    override fun dispatch(handler: ControlTransferHandler): List<DfaInstructionState> {
      (handler.state.pop() as DfaControlTransferValue).target as ReturnTransfer
      return handler.doDispatch()
    }
  }
}
