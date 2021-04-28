// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.lang.ir.inst.Instruction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class StateQueue {
  private static final int FORCE_MERGE_THRESHOLD = 100;
  private boolean myWasForciblyMerged;
  private final PriorityQueue<DfaInstructionState> myQueue = new PriorityQueue<>();
  private final Map<DfaInstructionState, DfaInstructionState> myMap = new HashMap<>();

  void offer(DfaInstructionState state) {
    DfaInstructionState otherState = myMap.putIfAbsent(state, state);
    if (otherState == null) {
      myQueue.offer(state);
    }
    else if (otherState.getMemoryState() instanceof TrackingDfaMemoryState) {
      ((TrackingDfaMemoryState)otherState.getMemoryState()).afterMerge((TrackingDfaMemoryState)state.getMemoryState());
    }
  }

  boolean isEmpty() {
    return myQueue.isEmpty();
  }

  boolean processAll(@NotNull Processor<? super DfaInstructionState> processor) {
    for (DfaInstructionState state : myQueue) {
      if (!processor.process(state)) return false;
    }
    return true;
  }

  @NotNull
  List<DfaInstructionState> getNextInstructionStates(Set<Instruction> joinInstructions) {
    DfaInstructionState state = myQueue.remove();
    final Instruction instruction = state.getInstruction();
    myMap.remove(state);

    DfaInstructionState next = myQueue.peek();
    if (next == null || next.compareTo(state) != 0) return Collections.singletonList(state);

    List<DfaMemoryStateImpl> memoryStates = new ArrayList<>();
    memoryStates.add((DfaMemoryStateImpl)state.getMemoryState());
    while (!myQueue.isEmpty() && myQueue.peek().compareTo(state) == 0) {
      DfaInstructionState anotherInstructionState = myQueue.poll();
      myMap.remove(anotherInstructionState);
      DfaMemoryState anotherState = anotherInstructionState.getMemoryState();
      memoryStates.add((DfaMemoryStateImpl)anotherState);
    }

    if (memoryStates.size() > 1 && joinInstructions.contains(instruction)) {
      squash(memoryStates);
    }

    if (memoryStates.size() > 1 && joinInstructions.contains(instruction)) {
      while (true) {
        int beforeSize = memoryStates.size();
        MultiMap<Object, DfaMemoryStateImpl> groups = MultiMap.create();
        for (DfaMemoryStateImpl memoryState : memoryStates) {
          groups.putValue(memoryState.getSuperficialKey(), memoryState);
        }

        memoryStates = new ArrayList<>();
        for (Map.Entry<Object, Collection<DfaMemoryStateImpl>> entry : groups.entrySet()) {
          memoryStates.addAll(mergeGroup((List<DfaMemoryStateImpl>)entry.getValue()));
        }
        if (memoryStates.size() == beforeSize) break;
        beforeSize = memoryStates.size();
        if (beforeSize == 1) break;
        // If some states were merged it's possible that they could be further squashed
        squash(memoryStates);
        if (memoryStates.size() == beforeSize || memoryStates.size() == 1) break;
      }
    }

    memoryStates = forceMerge(memoryStates);

    return ContainerUtil.map(memoryStates, state1 -> new DfaInstructionState(instruction, state1));
  }

  @NotNull
  private static List<DfaMemoryStateImpl> squash(List<DfaMemoryStateImpl> states) {
    return DfaUtil.upwardsAntichain(states, (l, r) -> r.isSuperStateOf(l));
  }

  static List<DfaMemoryStateImpl> mergeGroup(List<DfaMemoryStateImpl> group) {
    if (group.size() < 2) {
      return group;
    }

    StateMerger merger = new StateMerger();
    while (group.size() > 1) {
      List<DfaMemoryStateImpl> nextStates = merger.mergeByRanges(group);
      if (nextStates == null) nextStates = merger.mergeByFacts(group);
      if (nextStates == null) break;
      group = nextStates;
    }
    return group;
  }

  private List<DfaMemoryStateImpl> forceMerge(List<DfaMemoryStateImpl> states) {
    if (states.size() < FORCE_MERGE_THRESHOLD) return states;
    myWasForciblyMerged = true;
    Collection<List<DfaMemoryStateImpl>> groups = StreamEx.of(states).groupingBy(DfaMemoryStateImpl::getMergeabilityKey).values();
    return StreamEx.of(groups)
      .flatMap(group -> StreamEx.ofSubLists(group, 2)
        .map(pair -> {
          if (pair.size() == 2) {
            pair.get(0).merge(pair.get(1));
          }
          return pair.get(0);
        })).distinct().toListAndThen(StateQueue::squash);
  }

  boolean wasForciblyMerged() {
    return myWasForciblyMerged;
  }
}
