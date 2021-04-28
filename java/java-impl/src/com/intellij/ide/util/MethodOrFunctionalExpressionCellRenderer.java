// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;

import javax.swing.*;

public class MethodOrFunctionalExpressionCellRenderer extends DelegatingPsiElementCellRenderer<NavigatablePsiElement> {
  public static class MethodOrFunctionalExpressionCellRenderingInfo
    implements PsiElementCellRenderingInfo<NavigatablePsiElement> {
    private final MethodCellRenderer.MethodCellRenderingInfo myMethodCellRenderer;

    public MethodOrFunctionalExpressionCellRenderingInfo(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
      myMethodCellRenderer = new MethodCellRenderer.MethodCellRenderingInfo(showMethodNames, options);
    }

    @Override
    public String getElementText(NavigatablePsiElement element) {
      return element instanceof PsiMethod ? myMethodCellRenderer.getElementText((PsiMethod)element)
                                          : ClassPresentationUtil.getFunctionalExpressionPresentation((PsiFunctionalExpression)element, false);
    }

    @Override
    public String getContainerText(final NavigatablePsiElement element, final String name) {
      return element instanceof PsiMethod ? myMethodCellRenderer.getContainerText((PsiMethod)element, name)
                                          : PsiClassListCellRenderer.getContainerTextStatic(element);
    }

    @Override
    public int getIconFlags() {
      return PsiClassListCellRenderer.INFO.getIconFlags();
    }

    @Override
    public Icon getIcon(PsiElement element) {
      return element instanceof PsiMethod ? myMethodCellRenderer.getIcon(element) : PsiElementCellRenderingInfo.super.getIcon(element);
    }
  }

  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }
  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    super(new MethodOrFunctionalExpressionCellRenderingInfo(showMethodNames, options));
  }
}
