package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author vlan
 */
public class PyTypeCheckerInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class.getName());
  private static Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    if (LOG.isDebugEnabled())
      session.putUserData(TIME_KEY, System.nanoTime());
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    // TODO: Visit decorators with arguments
    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      final PyArgumentList args = node.getArgumentList();
      if (args != null) {
        final Map<PyGenericType, PyType> substitutions = new HashMap<PyGenericType, PyType>();
        final CallArgumentsMapping res = args.analyzeCall(resolveWithoutImplicits());
        final PyCallExpression.PyMarkedCallee markedCallee = res.getMarkedCallee();
        if (markedCallee != null) {
          final Callable callable = markedCallee.getCallable();
          final PyFunction function = callable.asMethod();
          if (function != null) {
             substitutions.putAll(PyTypeChecker.collectCallGenerics(function, node, myTypeEvalContext));
          }
        }
        for (Map.Entry<PyExpression, PyNamedParameter> entry : res.getPlainMappedParams().entrySet()) {
          final PyNamedParameter p = entry.getValue();
          if (p.isPositionalContainer() || p.isKeywordContainer()) {
            // TODO: Support *args, **kwargs
            continue;
          }
          final PyType argType = entry.getKey().getType(myTypeEvalContext);
          final PyType paramType = p.getType(myTypeEvalContext);
          checkTypes(paramType, argType, entry.getKey(), myTypeEvalContext, substitutions, true);
        }
      }
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      final PsiPolyVariantReference ref = node.getReference(resolveWithoutImplicits());
      if (ref != null) {
        final ResolveResult[] results = ref.multiResolve(false);
        String error = null;
        PyExpression arg = null;
        for (ResolveResult result : results) {
          final PsiElement resolved = result.getElement();
          if (resolved instanceof PyFunction) {
            final PyFunction fun = (PyFunction)resolved;
            PyExpression expr = PyNames.isRightOperatorName(fun.getName()) ? node.getLeftExpression() : node.getRightExpression();
            String msg = checkSingleArgumentFunction(fun, expr, false);
            if (msg == null) {
              return;
            }
            if (error == null) {
              error = msg;
              arg = expr;
            }
          }
          else {
            return;
          }
        }
        if (error != null) {
          registerProblem(arg, error);
        }
      }
    }

    @Override
    public void visitPySubscriptionExpression(PySubscriptionExpression node) {
      // TODO: Support slice PySliceExpressions
      final PsiReference ref = node.getReference(resolveWithoutImplicits());
      if (ref != null) {
        final PsiElement resolved = ref.resolve();
        if (resolved instanceof PyFunction) {
          checkSingleArgumentFunction((PyFunction)resolved, node.getIndexExpression(), true);
        }
      }
    }

    @Nullable
    private String checkSingleArgumentFunction(@NotNull PyFunction fun, @Nullable PyExpression argument, boolean registerProblem) {
      if (argument != null) {
        final PyParameter[] parameters = fun.getParameterList().getParameters();
        if (parameters.length == 2) {
          final PyNamedParameter p = parameters[1].getAsNamed();
          if (p != null) {
            final PyType argType = argument.getType(myTypeEvalContext);
            final PyType paramType = p.getType(myTypeEvalContext);
            return checkTypes(paramType, argType, argument, myTypeEvalContext, new HashMap<PyGenericType, PyType>(), registerProblem);
          }
        }
      }
      return null;
    }

    @Nullable
    private String checkTypes(@Nullable PyType superType, @Nullable PyType subType, @Nullable PsiElement node,
                              @NotNull TypeEvalContext context, @NotNull Map<PyGenericType, PyType> substitutions, boolean registerPoblem) {
      if (subType != null && superType != null) {
        if (!PyTypeChecker.match(superType, subType, context, substitutions)) {
          final String superName = PythonDocumentationProvider.getTypeName(superType, context);
          String expected = String.format("'%s'", superName);
          if (PyTypeChecker.hasGenerics(superType, context)) {
            final Ref<PyType> subst = PyTypeChecker.substitute(superType, substitutions, context);
            if (subst != null) {
              expected = String.format("'%s' (matched generic type '%s')",
                                       PythonDocumentationProvider.getTypeName(subst.get(), context),
                                       superName);

            }
          }
          final String msg = String.format("Expected type %s, got '%s' instead",
                                           expected,
                                           PythonDocumentationProvider.getTypeName(subType, context));
          if (registerPoblem) {
            registerProblem(node, msg);
          }
          return msg;
        }
      }
      return null;
    }
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session, ProblemsHolder problemsHolder) {
    if (LOG.isDebugEnabled()) {
      final Long startTime = session.getUserData(TIME_KEY);
      if (startTime != null) {
        LOG.debug(String.format("[%d] elapsed time: %d ms\n",
                                Thread.currentThread().getId(),
                                (System.nanoTime() - startTime) / 1000000));
      }
    }
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Type checker";
  }
}
