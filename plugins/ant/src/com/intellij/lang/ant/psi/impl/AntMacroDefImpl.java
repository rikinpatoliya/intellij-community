package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AntMacroDefImpl extends AntTaskImpl implements AntMacroDef {

  @NonNls public static final String ANT_MACRODEF_NAME = "AntMacroDef";

  private AntTypeDefinitionImpl myMacroDefinition;

  public AntMacroDefImpl(final AntStructuredElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    invalidateMacroDefinition();
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntMacroDef(this);
  }

  public String toString() {
    return createMacroClassName(getName());
  }

  public static String createMacroClassName(final String macroName) {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(ANT_MACRODEF_NAME);
      builder.append("[");
      builder.append(macroName);
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.MACRODEF_ROLE;
  }


  public AntTypeDefinition getMacroDefinition() {
    return myMacroDefinition;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      final AntFile file = getAntFile();
      if (myMacroDefinition != null) {
        for (AntTypeId id : myMacroDefinition.getNestedElements()) {
          final AntTypeDefinition nestedDef = file.getBaseTypeDefinition(myMacroDefinition.getNestedClassName(id));
          if (nestedDef != null) {
            file.unregisterCustomType(nestedDef);
          }
        }
        final AntStructuredElement parent = getAntProject();
        if (parent != null) {
          parent.unregisterCustomType(myMacroDefinition);
        }
        myMacroDefinition = null;
      }
      file.clearCaches();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void invalidateMacroDefinition() {
    if (!hasNameElement()) {
      myMacroDefinition = null;
      return;
    }

    final AntFile file = getAntFile();
    if (file == null) return;

    final String thisClassName = createMacroClassName(getName());
    myMacroDefinition = (AntTypeDefinitionImpl)file.getBaseTypeDefinition(thisClassName);
    final Map<String, AntAttributeType> attributes =
      (myMacroDefinition == null) ? new HashMap<String, AntAttributeType>() : myMacroDefinition.getAttributesMap();
    attributes.clear();
    final Map<AntTypeId, String> nestedElements =
      (myMacroDefinition == null) ? new HashMap<AntTypeId, String>() : myMacroDefinition.getNestedElementsMap();
    for (AntElement child : getChildren()) {
      if (child instanceof AntStructuredElement) {
        final AntStructuredElement se = (AntStructuredElement)child;
        final String name = se.getName();
        if (name != null) {
          final String tagName = se.getSourceElement().getName();
          if (tagName.equals("attribute")) {
            attributes.put(name.toLowerCase(Locale.US), AntAttributeType.STRING);
          }
          else if (tagName.equals("element")) {
            final String elementClassName = thisClassName + '$' + name;
            AntTypeDefinitionImpl nestedDef = (AntTypeDefinitionImpl)file.getBaseTypeDefinition(elementClassName);
            if (nestedDef == null) {
              final AntTypeDefinitionImpl targetDef = (AntTypeDefinitionImpl)file.getTargetDefinition();
              if (targetDef != null) {
                nestedDef = new AntTypeDefinitionImpl(targetDef);
              }
            }
            if (nestedDef != null) {
              final AntTypeId typeId = new AntTypeId(name);
              nestedDef.setTypeId(typeId);
              nestedDef.setClassName(elementClassName);
              nestedDef.setIsTask(false);
              nestedDef.setDefiningElement(child);
              file.registerCustomType(nestedDef);
              nestedElements.put(typeId, nestedDef.getClassName());
            }
          }
        }
      }
    }
    final AntTypeId definedTypeId = new AntTypeId(getName());
    if (myMacroDefinition == null) {
      myMacroDefinition = new AntTypeDefinitionImpl(definedTypeId, thisClassName, true, false, attributes, nestedElements, this);
    }
    else {
      myMacroDefinition.setTypeId(definedTypeId);
      myMacroDefinition.setClassName(thisClassName);
      myMacroDefinition.setIsTask(true);
      myMacroDefinition.setDefiningElement(this);
    }
    final AntStructuredElement parent = getAntProject();
    if (parent != null) {
      parent.registerCustomType(myMacroDefinition);
    }
    // define itself as nested task for sequential
    final AntAllTasksContainerImpl sequential = PsiTreeUtil.getChildOfType(this, AntAllTasksContainerImpl.class);
    if (sequential != null) {
      sequential.registerCustomType(myMacroDefinition);
      for (final AntTypeId id : myMacroDefinition.getNestedElements()) {
        sequential.registerCustomType(file.getBaseTypeDefinition(myMacroDefinition.getNestedClassName(id)));
      }
    }
  }
}
