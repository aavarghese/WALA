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
package reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class Reflect26 {

  // create a custom Annotation
  @Retention(RetentionPolicy.RUNTIME)
  @interface MarvelAnnotation {
      // This annotation has two attributes.
      public String key();
    
      public String value();
  }

  @MarvelAnnotation(key = "AvengersLeader", value = "CaptainAmerica")
  public static class Marvel {

    @MarvelAnnotation(key = "AvengersPlayer", value = "Thor")
    String textField = "MARVEL";

    @MarvelAnnotation(key = "AvengersPlayer", value = "Hulk")
    public void getCustomAnnotation(@MarvelAnnotation(key = "AvengersPlayer", value = "Spiderman") String text)
    {
      System.out.println(textField + text);
    }
  }

  /** Test of Method.getAnnotation */
  public static void main(String[] args)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
    Class<?> c = Marvel.class;
    MarvelAnnotation annoC = c.getAnnotation(MarvelAnnotation.class);
    System.out.println("Key Attribute of Class Annotation: " + annoC.key());
    System.out.println("Value Attribute of Class Annotation: " + annoC.value());

    Field[] fields = c.getDeclaredFields();
    MarvelAnnotation annoF = fields[0].getAnnotation(MarvelAnnotation.class);
    System.out.println("Key Attribute of Field Annotation: " + annoF.key());
    System.out.println("Value Attribute of Field Annotation: " + annoF.value());

    Method[] methods = c.getMethods();
    MarvelAnnotation annoM = methods[0].getAnnotation(MarvelAnnotation.class);
    System.out.println("Key Attribute of Method Annotation: " + annoM.key());
    System.out.println("Value Attribute of Method Annotation: " + annoM.value());

    Parameter[] parameters = methods[0].getParameters();
    MarvelAnnotation annoP = parameters[0].getAnnotation(MarvelAnnotation.class);
    System.out.println("Key Attribute of Parameter Annotation: " + annoP.key());
    System.out.println("Value Attribute of Parameter Annotation: " + annoP.value());

  }
}