// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalExpressionFlowUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InvocationKind;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

import java.util.*;
import java.util.function.BiConsumer;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final DFAFlowInfo myFlowInfo;
  private final InferenceCache myCache;
  private final InitialTypeProvider myInitialTypeProvider;
  private final PsiManager myManager;
  private final int lastInterestingInstructionIndex;

  TypeDfaInstance(Instruction @NotNull [] flow,
                  @NotNull DFAFlowInfo flowInfo,
                  @NotNull InferenceCache cache,
                  @NotNull PsiManager manager,
                  @NotNull InitialTypeProvider initialTypeProvider) {
    myFlow = flow;
    myManager = manager;
    myFlowInfo = flowInfo;
    myCache = cache;
    myInitialTypeProvider = initialTypeProvider;
    lastInterestingInstructionIndex = flowInfo.getInterestingInstructions().stream().mapToInt(Instruction::num).max().orElse(0);
  }

  @Override
  public void fun(@NotNull final TypeDfaState state, @NotNull final Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      handleReadWriteVariable(state, (ReadWriteVariableInstruction)instruction);
    }
    else if (instruction instanceof MixinTypeInstruction) {
      handleMixin(state, (MixinTypeInstruction)instruction);
    }
    else if (instruction instanceof ArgumentsInstruction) {
      handleArguments(state, (ArgumentsInstruction)instruction);
    }
    else if (instruction instanceof NegatingGotoInstruction) {
      handleNegation(state, (NegatingGotoInstruction)instruction);
    }
    else if (instruction.getElement() instanceof GrFunctionalExpression) {
      handleFunctionalExpression(state, instruction);
    }
  }

  private void handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
    final VariableDescriptor descriptor = instruction.getVariableDescriptor();
    if (descriptor == null) return;

    updateVariableType(state, instruction, descriptor, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
      assert originalInstr != null && !originalInstr.isWrite();

      DFAType original = state.getOrCreateVariableType(descriptor);
      original.addMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
      return original;
    });
  }

  private void handleReadWriteVariable(@NotNull TypeDfaState state, @NotNull ReadWriteVariableInstruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) return;
    VariableDescriptor descriptor = instruction.getDescriptor();
    if (instruction.isWrite()) {
      updateVariableType(
        state, instruction, descriptor,
        () -> {
          PsiType initializerType = TypeInferenceHelper.getInitializerType(element);
          if (initializerType == null && descriptor instanceof ResolvedVariableDescriptor) {
            GrVariable variable = ((ResolvedVariableDescriptor)descriptor).getVariable();
            PsiType augmentedType = TypeAugmenter.Companion.inferAugmentedType(variable);
            return DFAType.create(augmentedType);
          }
          else {
            return DFAType.create(initializerType);
          }
        }
      );
    }
    else {
      DFAType type = state.getVariableType(descriptor);
      if (type == null &&
          myFlowInfo.getInterestingInstructions().contains(instruction) &&
          myFlowInfo.getInterestingDescriptors().contains(descriptor)) {
        PsiType initialType = myInitialTypeProvider.initialType(descriptor);
        if (initialType != null) {
          updateVariableType(state, instruction, descriptor, () -> DFAType.create(initialType));
        }
      }
    }
  }

  private void handleArguments(TypeDfaState state, ArgumentsInstruction instruction) {
    for (Map.Entry<VariableDescriptor, Collection<Argument>> entry : instruction.getArguments().entrySet()) {
      final VariableDescriptor descriptor = entry.getKey();
      final Collection<Argument> arguments = entry.getValue();
      handleArgument(state, instruction, descriptor, arguments);
    }
  }

  private void handleArgument(TypeDfaState state, ArgumentsInstruction instruction, VariableDescriptor descriptor, Collection<Argument> arguments) {
    updateVariableType(state, instruction, descriptor, () -> {
      final DFAType result = state.getOrCreateVariableType(descriptor);
      final GroovyResolveResult[] results = instruction.getElement().multiResolve(false);
      for (GroovyResolveResult variant : results) {
        if (!(variant instanceof GroovyMethodResult)) continue;

        GroovyMethodCandidate candidate = ((GroovyMethodResult)variant).getCandidate();
        if (candidate == null) continue;

        ArgumentMapping mapping = candidate.getArgumentMapping();
        if (mapping == null) continue;

        for (Argument argument : arguments) {
          PsiType parameterType = mapping.expectedType(argument);
          if (parameterType == null) continue;

          PsiType typeToMixin = variant.getSubstitutor().substitute(parameterType);
          result.addMixin(typeToMixin, null);
        }
      }
      return result;
    });
  }

  private void updateVariableType(@NotNull TypeDfaState state,
                                  @NotNull Instruction instruction,
                                  @NotNull VariableDescriptor descriptor,
                                  @NotNull Computable<? extends DFAType> computation) {
    if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
      state.removeBinding(descriptor);
      return;
    }

    DFAType type = myCache.getCachedInferredType(descriptor, instruction);
    if (type == null) {
      if (myFlowInfo.getAcyclicInstructions().contains(instruction) && !myFlowInfo.getDependentOnSharedVariables().contains(instruction)) {
        type = computation.compute();
      }
      else {
        type = TypeInferenceHelper.doInference(state.getBindings(), computation);
      }
    }

    DFAType existingDfaType = state.getVariableType(descriptor);
    if (existingDfaType != null) {
      type = type.addFlushingType(existingDfaType.getFlushingType(), myManager);
    }
    state.putType(descriptor, type);
  }

  private static void handleNegation(@NotNull TypeDfaState state, @NotNull NegatingGotoInstruction negation) {
    for (Map.Entry<VariableDescriptor, DFAType> entry : state.getVarTypes().entrySet()) {
      entry.setValue(entry.getValue().negate(negation));
    }
  }

  private void handleFunctionalExpression(@NotNull TypeDfaState state, @NotNull Instruction instruction) {
    if (!FunctionalExpressionFlowUtil.isNestedFlowProcessingAllowed()) {
      return;
    }
    if (instruction.num() > lastInterestingInstructionIndex) {
      return;
    }
    if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
      return;
    }
    GrFunctionalExpression block = Objects.requireNonNull((GrFunctionalExpression)instruction.getElement());
    if (PsiUtil.isCompileStatic(block)) {
      return;
    }
    GrControlFlowOwner blockFlowOwner = FunctionalExpressionFlowUtil.getControlFlowOwner(block);
    if (blockFlowOwner == null) {
      return;
    }
    InvocationKind kind = FunctionalExpressionFlowUtil.getInvocationKind(block);
    Map<VariableDescriptor, DFAType> initialTypes = new LinkedHashMap<>(myFlowInfo.getInitialTypes());
    initialTypes.putAll(state.getVarTypes());
    switch (kind) {
      case IN_PLACE_ONCE:
        handleClosureDFAResult(instruction, blockFlowOwner, initialTypes, state::putType);
        break;
      case IN_PLACE_UNKNOWN:
        handleClosureDFAResult(instruction, blockFlowOwner, initialTypes, (descriptor, dfaType) -> {
          DFAType existingType = state.getVariableType(descriptor);
          if (existingType == null) {
            PsiType initialType = myInitialTypeProvider.initialType(descriptor);
            if (initialType != null) existingType = DFAType.create(initialType);
          }
          if (existingType != null) {
            DFAType mergedType = DFAType.create(dfaType, existingType, block.getManager());
            state.putType(descriptor, mergedType);
          }
        });
        break;
      case UNKNOWN:
        runWithCycleCheck(instruction, () -> {
          for (VariableDescriptor descriptor : myFlowInfo.getInterestingDescriptors()) {
            PsiType upperBoundByWrites = TypeDfaInstanceUtilKt.getLeastUpperBoundByAllWrites(blockFlowOwner, initialTypes, descriptor);
            if (upperBoundByWrites != PsiType.NULL) {
              DFAType existingType = state.getVariableType(descriptor);
              if (existingType == null) existingType = DFAType.create(null);
              DFAType flushedType = existingType.addFlushingType(upperBoundByWrites, myManager);
              state.putType(descriptor, flushedType);
            }
          }
          return null;
        });
    }
  }

  private void handleClosureDFAResult(@NotNull Instruction instruction,
                                      @NotNull GrControlFlowOwner block,
                                      @NotNull Map<VariableDescriptor, DFAType> initialTypes,
                                      @NotNull BiConsumer<? super VariableDescriptor, ? super DFAType> typeConsumer) {
    InferenceCache blockCache = TypeInferenceHelper.getInferenceCache(block);
    Instruction[] blockFlow = block.getControlFlow();
    Instruction lastBlockInstruction = blockFlow[blockFlow.length - 1];
    runWithCycleCheck(instruction, () -> {
      for (VariableDescriptor outerDescriptor : myFlowInfo.getInterestingDescriptors()) {
        PsiType descriptorType = blockCache.getInferredType(outerDescriptor, lastBlockInstruction, false, initialTypes);
        typeConsumer.accept(outerDescriptor, DFAType.create(descriptorType));
      }
      return null;
    });
  }

  private void runWithCycleCheck(@NotNull Instruction instruction, @NotNull Computable<?> action) {
    if (myFlowInfo.getAcyclicInstructions().contains(instruction)) {
      action.get();
    }
    else {
      // todo: IDEA-242437
      TypeInferenceHelper.doInference(Collections.emptyMap(), action);
    }
  }
}
