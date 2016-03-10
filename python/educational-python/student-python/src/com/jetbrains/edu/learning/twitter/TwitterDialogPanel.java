package com.jetbrains.edu.learning.twitter;

import com.intellij.openapi.ui.VerticalFlowLayout;
import com.jetbrains.edu.courseFormat.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.net.URL;

public class TwitterDialogPanel extends StudyTwitterUtils.TwitterDialogPanel {
  private final JTextField myTwitterTextField;
  private final JLabel myRemainSymbolsLabel;
  private URL myImageUrl;

  public TwitterDialogPanel(@NotNull Task solvedTask) {
    setLayout(new VerticalFlowLayout());
    myRemainSymbolsLabel = new JLabel();
    myTwitterTextField = new JTextField();
    create(solvedTask);
  }

  public void create(@NotNull Task solvedTask) {
    myImageUrl = getMediaSourceForTask(solvedTask);
    addImageLabel();

    String messageForTask = getMessageForTask(solvedTask);
    myTwitterTextField.setText(messageForTask);
    add(myTwitterTextField);
    
    myRemainSymbolsLabel.setText(String.valueOf(140 - messageForTask.length()));
    add(myRemainSymbolsLabel);
  }

  private void addImageLabel() {
    if (myImageUrl != null) {
      Icon icon = new ImageIcon(myImageUrl);
      add(new JLabel(icon));
    }
  }

  @NotNull
  @Override
  public String getMessage() {
    return myTwitterTextField.getText();
  }
  
  private String getMessageForTask(@NotNull final Task task) {
    return "I've completed smth. See ya, i'm clever";
  }

  @Nullable
  @Override
  public URL getMediaSource() {
    return myImageUrl;
  }
  
  private URL getMediaSourceForTask(@NotNull final Task task) {
    return getClass().getResource("/twitter/2level.gif");
  }


  @Override
  public void addTextFieldVerifier(@NotNull DocumentListener documentListener) {
    myTwitterTextField.getDocument().addDocumentListener(documentListener);
  }

  @NotNull
  @Override
  public JLabel getRemainSymbolsLabel() {
    return myRemainSymbolsLabel;
  }
}
