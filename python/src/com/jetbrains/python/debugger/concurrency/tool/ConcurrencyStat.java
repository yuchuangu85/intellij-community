/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.concurrency.tool;

public class ConcurrencyStat {
  private long myStartTime;
  private long myFinishTime;
  private long myPauseTime;
  private long myLastAcquireStartTime;
  private long myWaitTime;

  public ConcurrencyStat(long startTime) {
    myStartTime = startTime;
  }

  public void setPauseTime(long pauseTime) {
    myPauseTime = pauseTime;
  }

  public long getStartTime() {
    return myStartTime;
  }

  public void setStartTime(long startTime) {
    myStartTime = startTime;
  }

  public long getFinishTime() {
    return myFinishTime;
  }

  public void setFinishTime(long finishTime) {
    myFinishTime = finishTime;
  }

  public long getLastAcquireStartTime() {
    return myLastAcquireStartTime;
  }

  public void setLastAcquireStartTime(long lastAcquireStartTime) {
    myLastAcquireStartTime = lastAcquireStartTime;
  }

  public long getWaitTime() {
    return myLastAcquireStartTime != 0 ? myWaitTime + (myPauseTime - myLastAcquireStartTime) : myWaitTime;
  }

  public void incWaitTime(long timePeriod) {
    myWaitTime += timePeriod;
  }

  public long getWorkTime() {
    return myFinishTime == 0 ? myPauseTime - myStartTime : myFinishTime - myStartTime;
  }
}
