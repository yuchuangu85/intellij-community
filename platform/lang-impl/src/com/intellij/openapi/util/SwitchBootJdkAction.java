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
package com.intellij.openapi.util;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.VersionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author denis
 */
public class SwitchBootJdkAction extends AnAction implements DumbAware {
  @NonNls private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.SwitchBootJdkAction");
  @NonNls private static final String productJdkConfigFileName = getExecutable() + ".jdk";
  @NonNls private static final File productJdkConfigFile = new File(PathManager.getConfigPath(), productJdkConfigFileName);
  @NonNls private static final File bundledJdkFile = getBundledJDKFile();



  @NotNull
  private static File getBundledJDKFile() {
    StringBuilder bundledJDKPath = new StringBuilder(PathManager.getHomePath() + File.separator + "jre");
    if (SystemInfo.isMac) {
      bundledJDKPath.append(File.separator).append("jdk");
    }
    return new File(bundledJDKPath.toString());
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (!(SystemInfo.isMac || SystemInfo.isLinux)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setText("Switch Boot JDK");
  }

  public static List<JdkBundleDescriptor> getBundlesFromFile(@NotNull File fileWithBundles) {
    InputStream stream = null;
    InputStreamReader inputStream;
    BufferedReader bufferedReader;

    List<JdkBundleDescriptor> list = new ArrayList<JdkBundleDescriptor>();


    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      stream = new FileInputStream(fileWithBundles);
      //noinspection IOResourceOpenedButNotSafelyClosed
      inputStream = new InputStreamReader(stream, Charset.forName("UTF-8"));
      //noinspection IOResourceOpenedButNotSafelyClosed
      bufferedReader = new BufferedReader(inputStream);

      String line;

      while ((line = bufferedReader.readLine()) != null) {
        File file = new File(line);
        if (file.exists()) {
          list.add(new JdkBundleDescriptor(file));
        }
      }

    } catch (IllegalStateException e) {
      // The device builders can throw IllegalStateExceptions if
      // build gets called before everything is properly setup
      LOG.error(e);
    } catch (Exception e) {
      LOG.error("Error reading JDK bundles", e);
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException ignore) {}
      }
    }
    return list;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {

    if (!productJdkConfigFile.exists()) {
      try {
        if (!productJdkConfigFile.createNewFile()){
          LOG.error("Could not create " + productJdkConfigFileName + " productJdkConfigFile");
          return;
        }
      }
      catch (IOException e) {
        LOG.error(e);
        return;
      }
    }

    SwitchBootJdkDialog dialog = new SwitchBootJdkDialog(null, getBundlesFromFile(productJdkConfigFile));
    if (dialog.showAndGet()) {
      File selectedJdkBundleFile = dialog.getSelectedFile();
      FileWriter fooWriter = null;
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        fooWriter = new FileWriter(productJdkConfigFile, false);
        fooWriter.write(selectedJdkBundleFile.getAbsolutePath());
      }
      catch (IOException e) {
        LOG.error(e);
      }
      finally {
        try {
          if (fooWriter != null) {
            fooWriter.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      ApplicationManager.getApplication().restart();
    }
  }

  private static class JdkBundleDescriptor {
    @NotNull private File myBundleAsFile;
    @NotNull private String myBundleName;
    @Nullable private Pair<Version,Integer> myVersionUpdate;
    private boolean myBoot;
    private boolean myBundled;

    public JdkBundleDescriptor(@NotNull File bundleAsFile, boolean boot) {
      myBundleAsFile = bundleAsFile;
      Pair<String, Pair<Version, Integer>> nameVersionAndUpdate = JdkUtil.getJDKNameVersionAndUpdate(bundleAsFile.getAbsolutePath());
      myVersionUpdate = nameVersionAndUpdate.second;
      myBundleName = nameVersionAndUpdate.first;
      myBoot = boot;
    }

    public JdkBundleDescriptor(@NotNull File bundleAsFile) {
      this(bundleAsFile, false);
    }

    @NotNull
    public File getBundleAsFile() {
      return myBundleAsFile;
    }

    public String getVisualRepresentation() {
      StringBuilder representation = new StringBuilder(myBundleName);
      if (myVersionUpdate != null) {
        representation.append(myVersionUpdate.first.toString()).append((myVersionUpdate.second > 0 ? "_" + myVersionUpdate.second : ""));
      }

      if (myBoot || myBundled) {
        representation.append(" [");
        if (myBoot) representation.append(myBundled ? "boot, " : "boot");
        if (myBundled) representation.append("bundled");
        representation.append("]");
      }
      return representation.toString();
    }

    public void setBundled(boolean bundled) {
      myBundled = bundled;
    }

    public boolean isBoot() {
      return myBoot;
    }

    @NotNull
    public String getBundleName() {
      return myBundleName;
    }

    @Nullable
    public Pair<Version,Integer> getVersionUpdate() {
      return myVersionUpdate;
    }
  }

  private static class JdkBundlesList {
    private ArrayList<JdkBundleDescriptor> bundleList = new ArrayList<JdkBundleDescriptor>();
    private HashMap<String, JdkBundleDescriptor> bundleMap = new HashMap<String, JdkBundleDescriptor>();
    private HashMap<String, JdkBundleDescriptor> versionMap = new HashMap<String, JdkBundleDescriptor>();

    public JdkBundlesList(File bootBundle) {
      addBundle(bootBundle, true, false);
    }

    public void addUniqueBundle(File bundle, boolean bundled) {
      JdkBundleDescriptor bundleDescr = bundleMap.get(bundle.getAbsolutePath());
      if (bundleDescr == null) {
        addBundle(bundle, false, bundled);
      }
      else {
        if (bundled) bundleDescr.setBundled(true); // preserve bundled flag
      }
    }

    public void addUniqueBundle(File bundle) {
      addUniqueBundle(bundle, false);
    }

    private void addBundle(File bundle, boolean boot, boolean bundled) {
      JdkBundleDescriptor bundleDescriptor = new JdkBundleDescriptor(bundle, boot);

      Pair<Version, Integer> versionUpdate = bundleDescriptor.getVersionUpdate();
      String versionUpdateKey = versionUpdate != null ? versionUpdate.first + versionUpdate.second.toString() : null;
      if (!bundleList.isEmpty() && versionUpdate != null) {
        JdkBundleDescriptor descr = versionMap.get(versionUpdateKey);
        if (descr != null) {
          Pair<Version, Integer> descrVersionUpdate = descr.getVersionUpdate();
          if (descrVersionUpdate != null && descrVersionUpdate.second >= versionUpdate.second) {
            return; // do not add old jdk builds
          }
        }
      }

      bundleList.add(bundleDescriptor);
      bundleMap.put(bundle.getAbsolutePath(), bundleDescriptor);
      bundleDescriptor.setBundled(bundled);

      if (versionUpdateKey != null) {
        versionMap.put(versionUpdateKey, bundleDescriptor);
      }
    }

    public ArrayList<JdkBundleDescriptor> toArrayList() {
      return bundleList;
    }

    public boolean contains(String path) {
      return bundleMap.keySet().contains(path);
    }
  }

  private static class SwitchBootJdkDialog extends DialogWrapper {

    @NotNull private final ComboBox myComboBox;

    protected SwitchBootJdkDialog(@Nullable Project project, final List<JdkBundleDescriptor> jdkBundlesList) {
      super(project, false);

      final JdkBundlesList pathsList = JdkUtil.findJdkPaths();
      if (!jdkBundlesList.isEmpty()) {
        JdkBundleDescriptor jdkBundleDescription = jdkBundlesList.get(0);
        pathsList.addUniqueBundle(jdkBundleDescription.getBundleAsFile());
      }

      myComboBox = new ComboBox();

      DefaultComboBoxModel model = new DefaultComboBoxModel();

      for (JdkBundleDescriptor jdkBundlePath : pathsList.toArrayList()) {
        //noinspection unchecked
        model.addElement(jdkBundlePath);
      }

      model.addListDataListener(new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) { }

        @Override
        public void intervalRemoved(ListDataEvent e) { }

        @Override
        public void contentsChanged(ListDataEvent e) {
          setOKActionEnabled(!((JdkBundleDescriptor)myComboBox.getSelectedItem()).isBoot());
        }
      });

      //noinspection unchecked
      myComboBox.setModel(model);

      myComboBox.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value != null) {
            JdkBundleDescriptor jdkBundleDescriptor = ((JdkBundleDescriptor)value);
            if (jdkBundleDescriptor.isBoot()) {
              setForeground(JBColor.DARK_GRAY);
            }
            setText(jdkBundleDescriptor.getVisualRepresentation());
          }
          else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Null value has been passed to a cell renderer. Available JDKs count: " + pathsList.toArrayList().size());
              StringBuilder jdkNames = new StringBuilder();
              for (JdkBundleDescriptor jdkBundlePath : pathsList.toArrayList()) {
                if (!jdkBundlesList.isEmpty()) {
                  continue;
                }
                jdkNames.append(jdkBundlePath.getVisualRepresentation()).append("; ");
              }
              if (LOG.isDebugEnabled()) {
                LOG.debug("Available JDKs names: " + jdkNames.toString());
              }
            }
          }
        }
      });

      setTitle("Switch IDE Boot JDK");
      setOKActionEnabled(false); // First item is a boot jdk
      init();
    }

    @Nullable
    @Override
    protected JComponent createNorthPanel() {
      return new JBLabel("Select Boot JDK");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myComboBox;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    public File getSelectedFile() {
      return ((JdkBundleDescriptor)myComboBox.getSelectedItem()).myBundleAsFile;
    }
  }

  private static final Pattern[] VERSION_UPDATE_PATTERNS = {
    Pattern.compile("^java version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+)\".*", Pattern.MULTILINE),
    Pattern.compile("^openjdk version \"([\\d]+\\.[\\d]+\\.[\\d]+)_([\\d]+).*\".*", Pattern.MULTILINE),
    Pattern.compile("^[a-zA-Z() \"\\d]*([\\d]+\\.[\\d]+\\.?[\\d]*).*", Pattern.MULTILINE)
  };

  private static final String STANDARD_JDK_LOCATION_ON_MAC_OS_X = "/Library/Java/JavaVirtualMachines/";
  private static final String STANDARD_JDK_6_LOCATION_ON_MAC_OS_X = "/System/Library/Java/JavaVirtualMachines/";
  private static final String STANDARD_JVM_LOCATION_ON_LINUX = "/usr/lib/jvm/";

  private static final Version JDK6_VERSION = new Version(1, 6, 0);
  private static final Version JDK8_VERSION = new Version(1, 8, 0);


  private static class JdkUtil {
    private static JdkBundlesList findJdkPaths () {

      File bootJDK = new File(System.getProperty("java.home")).getParentFile();

      JdkBundlesList jdkBundlesList = new JdkBundlesList(bootJDK);

      if (bundledJdkFile.exists()) {
        jdkBundlesList.addUniqueBundle(bundledJdkFile, true);
      }

      if (SystemInfo.isMac) {
        addJDKBundlesFromLocation(jdkBundlesList, STANDARD_JDK_6_LOCATION_ON_MAC_OS_X, JDK6_VERSION, JDK6_VERSION);
        addJDKBundlesFromLocation(jdkBundlesList, STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK6_VERSION, JDK6_VERSION);
        addJDKBundlesFromLocation(jdkBundlesList, STANDARD_JDK_LOCATION_ON_MAC_OS_X, JDK8_VERSION, null);
      }
      else if (SystemInfo.isLinux) {
        addJDKBundlesFromLocation(jdkBundlesList, STANDARD_JVM_LOCATION_ON_LINUX, JDK8_VERSION, null);
      }

      return jdkBundlesList;
    }

    private static void addJDKBundlesFromLocation(JdkBundlesList jdkList, String location, @Nullable Version minVer,
                                                  @Nullable Version maxVer) {
      File jvmLocation = new File(location);

      if (!jvmLocation.exists()) {
        LOG.debug("Standard jvm location does not exists: " + jvmLocation);
        return;
      }

      File[] jvms = jvmLocation.listFiles();

      if (jvms == null) {
        LOG.debug("Cannot get jvm list from: " + jvmLocation);
        return;
      }

      for (File jvm : jvms) {
        if (!new File(jvm, "lib/tools.jar").exists()) continue; // Skip JRE

        try {
          String jvmCanonicalPath = jvm.getCanonicalPath();

          if (jdkList.contains(jvmCanonicalPath)) continue; // Skip symlinked JDKs

          Pair<String, Pair<Version,Integer>> version = getJDKNameVersionAndUpdate(jvmCanonicalPath);

          if (version.second == null) continue; // Skip unknown
          Version jdkVer = version.second.first;
          if (minVer != null && jdkVer.lessThan(minVer.major, minVer.minor, minVer.bugfix))
            continue; // Skip below supported

          if (maxVer != null && maxVer.lessThan(jdkVer.major, jdkVer.minor, jdkVer.bugfix))
            continue; // Skip above supported

          jdkList.addUniqueBundle(new File(jvmCanonicalPath));
        }
        catch (Exception e) {
          LOG.debug(e);
        }
      }
    }

    private static Pair<String, Pair<Version,Integer>> getJDKNameVersionAndUpdate(String jvmPath) {
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(jvmPath + File.separator + "jre" + File.separator + "bin" + File.separator + "java");
      commandLine.addParameter("-version");

      String displayVersion = null;
      Pair<Version, Integer> versionAndUpdate = null;
      try {
        displayVersion = ExecUtil.readFirstLine(commandLine.createProcess().getErrorStream(), null);
      }
      catch (ExecutionException e) {
        LOG.debug(e);
      }

      if (displayVersion != null) {
        versionAndUpdate = VersionUtil.parseVersionAndUpdate(displayVersion, VERSION_UPDATE_PATTERNS);
        displayVersion = displayVersion.replaceFirst("\".*\"", "");
      }
      else {
        displayVersion = new File(jvmPath).getName();
      }

      return Pair.create(displayVersion, versionAndUpdate);
    }
  }

  @NotNull
  private static String getExecutable() {
    final String executable = System.getProperty("idea.executable");
    return executable != null ? executable : ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
  }
}
