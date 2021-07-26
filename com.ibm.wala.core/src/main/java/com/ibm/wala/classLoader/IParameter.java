/*
 * Copyright (c) 2021 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.classLoader;

import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.ParameterReference;

/** */
public interface IParameter extends IMember {

  /** @return the canonical MethodReference of the declaring method */
  public MethodReference getMethodReference();

  /** @return the canonical TypeReference of the declared type of the parameter */
  public TypeReference getFieldTypeReference();

  /** @return canonical ParameterReference representing this parameter */
  public ParameterReference getReference();

  /** Is this parameter final? */
  public boolean isFinal();
}
