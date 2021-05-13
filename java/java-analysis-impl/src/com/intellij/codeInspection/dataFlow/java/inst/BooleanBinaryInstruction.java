// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaWrappedValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.*;
import static com.intellij.psi.JavaTokenType.*;

public class BooleanBinaryInstruction extends ExpressionPushingInstruction implements BranchingInstruction {
  // AND and OR for boolean arguments only
  private static final TokenSet ourSignificantOperations = TokenSet.create(EQ, EQEQ, NE, LT, GT, LE, GE, INSTANCEOF_KEYWORD, AND, OR);

  /**
   * A special operation to express string comparison by content (like equals() method does).
   * Used to desugar switch statements
   */
  public static final IElementType STRING_EQUALITY_BY_CONTENT = EQ;

  private final @Nullable IElementType myOpSign;

  /**
   * @param opSign sign of the operation
   * @param anchor to bind instruction to
   */
  public BooleanBinaryInstruction(IElementType opSign, @Nullable DfaAnchor anchor) {
    super(anchor);
    myOpSign = opSign == XOR ? NE : ourSignificantOperations.contains(opSign) ? opSign : null;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();

    if (myOpSign == AND || myOpSign == OR) {
      return handleAndOrBinop(interpreter, stateBefore, dfaRight, dfaLeft);
    }
    return handleRelationBinop(interpreter, stateBefore, dfaRight, dfaLeft);
  }

  private static RelationType @NotNull [] splitRelation(RelationType relationType) {
    switch (relationType) {
      case LT:
      case LE:
      case GT:
      case GE:
        return new RelationType[]{RelationType.LT, RelationType.GT, RelationType.EQ};
      default:
        return new RelationType[]{relationType, relationType.getNegated()};
    }
  }

  /**
   * Returns true if two given values should be compared by content, rather than by reference.
   * @param memState memory state
   * @param dfaLeft left value
   * @param dfaRight right value
   * @return true if two given values should be compared by content, rather than by reference.
   */
  private static boolean shouldCompareByEquals(@NotNull DfaMemoryState memState, @NotNull DfaValue dfaLeft, @NotNull DfaValue dfaRight) {
    if (dfaLeft == dfaRight && !(dfaLeft instanceof DfaWrappedValue) && !(dfaLeft.getDfType() instanceof DfConstantType)) {
      return false;
    }
    return TypeConstraint.fromDfType(memState.getDfType(dfaLeft)).isComparedByEquals() &&
           TypeConstraint.fromDfType(memState.getDfType(dfaRight)).isComparedByEquals();

  }
  
  private DfaInstructionState @NotNull [] handleRelationBinop(@NotNull DataFlowInterpreter runner,
                                                              @NotNull DfaMemoryState memState,
                                                              @NotNull DfaValue dfaRight,
                                                              @NotNull DfaValue dfaLeft) {
    if((myOpSign == EQEQ || myOpSign == NE) && shouldCompareByEquals(memState, dfaLeft, dfaRight)) {
      ArrayList<DfaInstructionState> states = new ArrayList<>(2);
      DfaMemoryState equality = memState.createCopy();
      DfaCondition condition = dfaLeft.eq(dfaRight);
      if (equality.applyCondition(condition)) {
        pushResult(runner, equality, BOOLEAN);
        states.add(nextState(runner, equality));
      }
      if (memState.applyCondition(condition.negate())) {
        pushResult(runner, memState, booleanValue(myOpSign == NE));
        states.add(nextState(runner, memState));
      }
      return states.toArray(DfaInstructionState.EMPTY_ARRAY);
    }
    RelationType relationType = myOpSign == STRING_EQUALITY_BY_CONTENT ? RelationType.EQ :
                                DfaPsiUtil.getRelationByToken(myOpSign);
    if (relationType == null) {
      pushResult(runner, memState, BOOLEAN);
      return nextStates(runner, memState);
    }
    RelationType[] relations = splitRelation(relationType);

    ArrayList<DfaInstructionState> states = new ArrayList<>(relations.length);

    for (int i = 0; i < relations.length; i++) {
      RelationType relation = relations[i];
      DfaCondition condition = dfaLeft.cond(relation, dfaRight);
      if (condition == DfaCondition.getFalse()) continue;
      boolean result = relationType.isSubRelation(relation);
      if (condition == DfaCondition.getTrue()) {
        pushResult(runner, memState, booleanValue(result));
        return nextStates(runner, memState);
      }
      final DfaMemoryState copy = i == relations.length - 1 && !states.isEmpty() ? memState : memState.createCopy();
      if (copy.applyCondition(condition) &&
          copy.meetDfType(dfaLeft, copy.getDfType(dfaLeft).correctForRelationResult(relationType, result)) &&
          copy.meetDfType(dfaRight, copy.getDfType(dfaRight).correctForRelationResult(relationType, result))) {
        pushResult(runner, copy, booleanValue(result));
        states.add(nextState(runner, copy));
      }
    }
    if (states.isEmpty()) {
      // Neither of relations could be applied: likely comparison with NaN; do not split the state in this case, just push false
      pushResult(runner, memState, FALSE);
      return nextStates(runner, memState);
    }

    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private DfaInstructionState @NotNull [] handleAndOrBinop(@NotNull DataFlowInterpreter runner,
                                                           @NotNull DfaMemoryState memState,
                                                           @NotNull DfaValue dfaRight, 
                                                           @NotNull DfaValue dfaLeft) {
    List<DfaInstructionState> result = new ArrayList<>(2);
    boolean or = myOpSign == OR;
    DfaMemoryState copy = memState.createCopy();
    DfaCondition cond = dfaRight.eq(booleanValue(or));
    if (copy.applyCondition(cond)) {
      pushResult(runner, copy, booleanValue(or));
      result.add(nextState(runner, copy));
    }
    if (memState.applyCondition(cond.negate())) {
      pushResult(runner, memState, dfaLeft);
      result.add(nextState(runner, memState));
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public String toString() {
    return "BOOLEAN_OP " + myOpSign;
  }
}
