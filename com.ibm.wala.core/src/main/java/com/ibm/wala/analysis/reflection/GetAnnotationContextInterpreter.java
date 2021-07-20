/*
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.analysis.reflection;

import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.cfg.InducedCFG;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassSyntheticClassLoader;
import com.ibm.wala.ipa.summaries.SyntheticIR;
import com.ibm.wala.shrike.shrikeCT.AnnotationsReader;
import com.ibm.wala.shrike.shrikeCT.AnnotationsReader.ArrayElementValue;
import com.ibm.wala.shrike.shrikeCT.AnnotationsReader.ConstantElementValue;
import com.ibm.wala.shrike.shrikeCT.AnnotationsReader.ElementValue;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.ipa.callgraph.propagation.FilteredPointerKey;
import java.util.ArrayList;
import com.ibm.wala.types.ClassLoaderReference;
import java.util.Iterator;
import java.util.Map;

/**
 * {@link SSAContextInterpreter} specialized to interpret Class.getAnnotation() in a {@link
 * JavaTypeContext}
 */
public class GetAnnotationContextInterpreter implements SSAContextInterpreter {

  /* BEGIN Custom change: caching */
  private final Map<String, IR> cache = HashMapFactory.make();

  /* END Custom change: caching */

  private Map<IClass, FakeAnnotationClass> annotationCache = HashMapFactory.make();

  private static final boolean DEBUG = false;

  private final IClassHierarchy cha;

  public GetAnnotationContextInterpreter(IClassHierarchy cha) {
    this.cha = cha;
  }

  @Override
  public IR getIR(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    assert understands(node);
    if (DEBUG) {
      System.err.println("generating IR for " + node);
    }
    /* BEGIN Custom change: caching */
    final Context context = node.getContext();
    final IMethod method = node.getMethod();
    final String hashKey = method.toString() + '@' + context.toString();

    IR result = cache.get(hashKey);

    if (result == null) {
      result = makeIR(method, context);
      cache.put(hashKey, result);
    }

    /* END Custom change: caching */
    return result;
  }

  @Override
  public IRView getIRView(CGNode node) {
    return getIR(node);
  }

  @Override
  public int getNumberOfStatements(CGNode node) {
    assert understands(node);
    return getIR(node).getInstructions().length;
  }

  @Override
  public boolean understands(CGNode node) {
    if (node == null) {
      throw new IllegalArgumentException("node is null");
    }
    if (!(node.getContext().isA(GetAnnotationContext.class))) {
      return false;
    }
    return node.getMethod().getReference().equals(GetAnnotationContextSelector.GET_ANNOTATION);
  }
  @Override
  public Iterator<NewSiteReference> iterateNewSites(CGNode node) {
    return EmptyIterator.instance();
  }

  @Override
  public Iterator<CallSiteReference> iterateCallSites(CGNode node) {
    return EmptyIterator.instance();
  }

  private SSAInstruction[] makeStatements(Context context, Map<Integer, ConstantValue> constants) { //TODO !!!
    ArrayList<SSAInstruction> statements = new ArrayList<>();
    int nextLocal = 3;
    int retValue = nextLocal++;
    TypeReference trRec = ((FilteredPointerKey.SingleClassFilter) context.get(ContextKey.PARAMETERS[0])).getConcreteType().getReference();
    TypeReference trParam = ((FilteredPointerKey.SingleClassFilter) context.get(ContextKey.PARAMETERS[1])).getConcreteType().getReference();

    IClass klassRec = cha.lookupClass(trRec);
    IClass klassParam = cha.lookupClass(trParam);

    Annotation annot = null;
    for (Annotation k: klassRec.getAnnotations()) {
      if (k.getType().equals(trParam)) {
        annot = k;
      }
    }
    if (annot != null) {
      SSAInstructionFactory insts = ((TypeAbstraction) context.get(ContextKey.RECEIVER))
            .getType()
            .getClassLoader()
            .getInstructionFactory();

      TypeReference trAnnot = annot.getType();

      BypassSyntheticClassLoader loader = (BypassSyntheticClassLoader) cha.getLoader(cha.getScope().getLoader(Atom.findOrCreateUnicodeAtom("Synthetic")));
      FakeAnnotationClass clAnnot = annotationCache.get(klassParam);
      if (clAnnot == null) {
        clAnnot = new FakeAnnotationClass(loader.getReference(), cha, klassParam);
      }

      loader.registerClass(TypeName.string2TypeName("Lcom/ibm/wala/FakeAnnotationClass") , clAnnot);

      Map<String, AnnotationsReader.ElementValue> annotMap = annot.getNamedArguments();
      for (String key : annotMap.keySet())  {
        clAnnot.addField(Atom.findOrCreateUnicodeAtom(key), getTRForElementValue(annotMap.get(key)));
      }

      int iindex = 0;
      NewSiteReference site = NewSiteReference.make(iindex, clAnnot.getReference());
      SSANewInstruction N = insts.NewInstruction(iindex++, retValue, site);
      statements.add(N);

      for (IField field: clAnnot.getAllFields()) {
        String value = annotMap.get(field.getName().toString()).toString();
        constants.put(nextLocal, new ConstantValue(value));
        SSAPutInstruction P = insts.PutInstruction(iindex++, retValue, nextLocal++,field.getReference());
        statements.add(P);
      }

      SSAReturnInstruction R = insts.ReturnInstruction(statements.size(), retValue, false);
      statements.add(R);
    }

    return statements.toArray(new SSAInstruction[0]);
  }

  private TypeReference getTRForElementValue(AnnotationsReader.ElementValue val) {
    if (val instanceof AnnotationsReader.ConstantElementValue) {
      if (((AnnotationsReader.ConstantElementValue) val).val instanceof String) {
        return TypeReference.JavaLangString;
      } else if (((AnnotationsReader.ConstantElementValue) val).val instanceof Integer) {
        return TypeReference.JavaLangInteger;
      } else if (((AnnotationsReader.ConstantElementValue) val).val instanceof Boolean) {
        return TypeReference.JavaLangBoolean;
      } //Other types?
    } else if (val instanceof AnnotationsReader.EnumElementValue) {
        return TypeReference.JavaLangEnum;
    } else if (val instanceof AnnotationsReader.ArrayElementValue) {
      return getTRForElementValue(((AnnotationsReader.ArrayElementValue) val).vals[0]); //return TR of first element in array
    }
    return TypeReference.JavaLangClass; //Other types?
  }

  private IR makeIR(IMethod method, Context context) {
    Map<Integer, ConstantValue> constants = HashMapFactory.make();
    SSAInstruction instrs[] = makeStatements(context, constants);
    return new SyntheticIR(
        method,
        context,
        new InducedCFG(instrs, method, context),
        instrs,
        SSAOptions.defaultOptions(),
        constants);
  }

  @Override
  public boolean recordFactoryType(CGNode node, IClass klass) {
    return false;
  }

  @Override
  public Iterator<FieldReference> iterateFieldsRead(CGNode node) {
    return EmptyIterator.instance();
  }

  @Override
  public Iterator<FieldReference> iterateFieldsWritten(CGNode node) {
    return EmptyIterator.instance();
  }

  @Override
  public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(CGNode N) {
    return getIR(N).getControlFlowGraph();
  }

  @Override
  public DefUse getDU(CGNode node) {
    return new DefUse(getIR(node));
  }
}
