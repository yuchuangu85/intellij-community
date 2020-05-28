// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.notNull;

public class ClassInnerStuffCache {
  private final PsiExtensibleClass myClass;
  private final SimpleModificationTracker myTracker = new SimpleModificationTracker();

  public ClassInnerStuffCache(@NotNull PsiExtensibleClass aClass) {
    myClass = aClass;
  }

  public PsiMethod @NotNull [] getConstructors() {
    return copy(CachedValuesManager.getCachedValue(myClass, () -> makeResult(PsiImplUtil.getConstructors(myClass))));
  }

  public PsiField @NotNull [] getFields() {
    return copy(CachedValuesManager.getCachedValue(myClass, () -> makeResult(calcFields())));
  }

  public PsiMethod @NotNull [] getMethods() {
    return copy(CachedValuesManager.getCachedValue(myClass, () -> makeResult(calcMethods())));
  }

  public PsiClass @NotNull [] getInnerClasses() {
    return copy(CachedValuesManager.getCachedValue(myClass, () -> makeResult(calcInnerClasses())));
  }

  public PsiRecordComponent @NotNull [] getRecordComponents() {
    return copy(CachedValuesManager.getCachedValue(myClass, () -> makeResult(calcRecordComponents())));
  }

  @Nullable
  public PsiField findFieldByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findFieldByName(myClass, name, true);
    }
    else {
      return CachedValuesManager.getCachedValue(myClass, () -> makeResult(getFieldsMap())).get(name);
    }
  }

  public PsiMethod @NotNull [] findMethodsByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findMethodsByName(myClass, name, true);
    }
    else {
      return copy(notNull(CachedValuesManager.getCachedValue(myClass, () -> makeResult(getMethodsMap())).get(name), PsiMethod.EMPTY_ARRAY));
    }
  }

  @Nullable
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    if (checkBases) {
      return PsiClassImplUtil.findInnerByName(myClass, name, true);
    }
    else {
      return CachedValuesManager.getCachedValue(myClass, () -> makeResult(getInnerClassesMap())).get(name);
    }
  }

  @Nullable
  PsiMethod getValuesMethod() {
    return myClass.isEnum() && !isAnonymousClass() ? CachedValuesManager.getCachedValue(myClass, () -> makeResult(makeValuesMethod())) : null;
  }

  @Nullable
  private PsiMethod getValueOfMethod() {
    return myClass.isEnum() && !isAnonymousClass() ? CachedValuesManager.getCachedValue(myClass, () -> makeResult(makeValueOfMethod())) : null;
  }

  private boolean isAnonymousClass() {
    return myClass.getName() == null || myClass instanceof PsiAnonymousClass;
  }

  private static <T> T[] copy(T[] value) {
    return value.length == 0 ? value : value.clone();
  }

  private <T> CachedValueProvider.Result<T> makeResult(T value) {
    return CachedValueProvider.Result.create(value, PsiModificationTracker.MODIFICATION_COUNT, myTracker);
  }

  private PsiField @NotNull [] calcFields() {
    List<PsiField> own = myClass.getOwnFields();
    List<PsiField> ext = PsiAugmentProvider.collectAugments(myClass, PsiField.class, null);
    return ArrayUtil.mergeCollections(own, ext, PsiField.ARRAY_FACTORY);
  }

  private PsiMethod @NotNull [] calcMethods() {
    List<PsiMethod> own = myClass.getOwnMethods();
    List<PsiMethod> ext = PsiAugmentProvider.collectAugments(myClass, PsiMethod.class, null);
    if (myClass.isEnum()) {
      ContainerUtil.addIfNotNull(ext, getValuesMethod());
      ContainerUtil.addIfNotNull(ext, getValueOfMethod());
    }
    return ArrayUtil.mergeCollections(own, ext, PsiMethod.ARRAY_FACTORY);
  }

  private PsiClass @NotNull [] calcInnerClasses() {
    List<PsiClass> own = myClass.getOwnInnerClasses();
    List<PsiClass> ext = PsiAugmentProvider.collectAugments(myClass, PsiClass.class, null);
    return ArrayUtil.mergeCollections(own, ext, PsiClass.ARRAY_FACTORY);
  }

  private PsiRecordComponent @NotNull [] calcRecordComponents() {
    PsiRecordHeader header = myClass.getRecordHeader();
    return header == null ? PsiRecordComponent.EMPTY_ARRAY : header.getRecordComponents();
  }

  @NotNull
  private Map<String, PsiField> getFieldsMap() {
    Map<String, PsiField> cachedFields = new THashMap<>();
    for (PsiField field : myClass.getOwnFields()) {
      String name = field.getName();
      if (!cachedFields.containsKey(name)) {
        cachedFields.put(name, field);
      }
    }
    return ConcurrentFactoryMap.createMap(name -> {
      PsiField result = cachedFields.get(name);
      return result != null ? result : ContainerUtil.getFirstItem(PsiAugmentProvider.collectAugments(myClass, PsiField.class, name));
    });
  }

  @NotNull
  private Map<String, PsiMethod[]> getMethodsMap() {
    List<PsiMethod> ownMethods = myClass.getOwnMethods();
    return ConcurrentFactoryMap.createMap(name -> {
      return JBIterable
        .from(ownMethods).filter(m -> name.equals(m.getName()))
        .append("values".equals(name) ? getValuesMethod() : null)
        .append("valueOf".equals(name) ? getValueOfMethod() : null)
        .append(PsiAugmentProvider.collectAugments(myClass, PsiMethod.class, name))
        .toArray(PsiMethod.EMPTY_ARRAY);
    });
  }

  @NotNull
  private Map<String, PsiClass> getInnerClassesMap() {
    Map<String, PsiClass> cachedInners = new THashMap<>();
    for (PsiClass psiClass : myClass.getOwnInnerClasses()) {
      String name = psiClass.getName();
      if (name == null) {
        Logger.getInstance(ClassInnerStuffCache.class).error(psiClass);
      }
      else if (!(psiClass instanceof ExternallyDefinedPsiElement) || !cachedInners.containsKey(name)) {
        cachedInners.put(name, psiClass);
      }
    }
    return ConcurrentFactoryMap.createMap(name -> {
      PsiClass result = cachedInners.get(name);
      return result != null ? result : ContainerUtil.getFirstItem(PsiAugmentProvider.collectAugments(myClass, PsiClass.class, name));
    });
  }

  private PsiMethod makeValuesMethod() {
    return new EnumSyntheticMethod(myClass, "public static " + myClass.getName() + "[] values() { }");
  }

  private PsiMethod makeValueOfMethod() {
    return new EnumSyntheticMethod(myClass, "public static " + myClass.getName() + " valueOf(java.lang.String name) throws java.lang.IllegalArgumentException { }");
  }

  public void dropCaches() {
    myTracker.incModificationCount();
  }

  private static class EnumSyntheticMethod extends LightMethod implements SyntheticElement {
    private final PsiClass myClass;
    private final String myText;

    EnumSyntheticMethod(@NotNull PsiClass enumClass, @NotNull String text) {
      super(enumClass.getManager(), JavaPsiFacade.getElementFactory(enumClass.getProject()).createMethodFromText(text, enumClass), enumClass);
      myClass = enumClass;
      myText = text;
    }

    @Override
    public int getTextOffset() {
      return myClass.getTextOffset();
    }

    @Override
    public boolean equals(Object another) {
      return this == another ||
             another instanceof EnumSyntheticMethod &&
             myClass.equals(((EnumSyntheticMethod)another).myClass) &&
             myText.equals(((EnumSyntheticMethod)another).myText);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myText, myClass);
    }
  }
}