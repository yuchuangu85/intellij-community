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
package com.jetbrains.env.python;

import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphElement;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyGraphModel;
import com.jetbrains.python.debugger.concurrency.PyConcurrencyService;
import com.jetbrains.python.debugger.concurrency.model.ConcurrencyThreadState;

import java.util.ArrayList;

public class PythonConcurrencyGraphTest extends PyEnvTestCase {

  public static void compareGraphRows(int row, ConcurrencyGraphModel graphModel, ConcurrencyThreadState[] correctElements) {
    ArrayList<ConcurrencyGraphElement> elements = graphModel.getDrawElementsForRow(row);
    assertEquals(String.format("row = %d", row), correctElements.length, elements.size());
    for (int i = 0; i < elements.size(); ++i) {
      ConcurrencyThreadState graphElement = elements.get(i).getThreadState();
      ConcurrencyThreadState correctElement = correctElements[i];
      assertEquals(String.format("row = %d column = %d", row, i), correctElement, graphElement);
    }
  }

  public static void compareGraphs(ConcurrencyGraphModel graphModel, ConcurrencyThreadState[][] correctGraph) {
    for (int i = 0; i < correctGraph.length; ++i) {
      compareGraphRows(i, graphModel, correctGraph[i]);
    }
  }


  public void testThreadMain() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test1.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Stopped},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

  public void testThreadCreation() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test2.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
        };
        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

  public void testThreadJoin() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test3.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();
        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Stopped, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Stopped, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Stopped, ConcurrencyThreadState.Stopped},
          {ConcurrencyThreadState.Stopped, ConcurrencyThreadState.Stopped, ConcurrencyThreadState.Stopped},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

  public void testThreadLockWith() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test4.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

  public void testThreadLockAcquireRelease() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test5.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

  public void testThreadDoubleLock() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test9.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);

      }
    });
  }

  public void testThreadDeadlock() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test6.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getThreadingInstance();
      }

      @Override
      public void testing() throws Exception {
        waitFor(myPausedSemaphore, 3000);

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait, ConcurrencyThreadState.LockOwn},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Deadlock, ConcurrencyThreadState.Deadlock},
        };

        compareGraphRows(10, myPyConcurrencyGraphModel, correct[0]);
        compareGraphRows(11, myPyConcurrencyGraphModel, correct[1]);
      }
    });
  }

  public void testAsyncioTaskCreation() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test7.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getAsyncioInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

  public void testAsyncioLock() throws Exception {
    runPythonTest(new PyDebuggerTask("/concurrency", "test8.py") {
      public ConcurrencyGraphModel myPyConcurrencyGraphModel;

      @Override
      public void before() throws Exception {
        PyDebuggerOptionsProvider.getInstance(getProject()).setSaveThreadingLog(true);
        myPyConcurrencyGraphModel = PyConcurrencyService.getInstance(getProject()).getAsyncioInstance();
      }

      @Override
      public void testing() throws Exception {
        waitForTerminate();

        ConcurrencyThreadState[][] correct = {
          {ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.LockWait, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.LockOwn, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockWait, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.LockOwn, ConcurrencyThreadState.Run},
          {ConcurrencyThreadState.Run, ConcurrencyThreadState.Run, ConcurrencyThreadState.Run},
        };

        compareGraphs(myPyConcurrencyGraphModel, correct);
      }
    });
  }

}
