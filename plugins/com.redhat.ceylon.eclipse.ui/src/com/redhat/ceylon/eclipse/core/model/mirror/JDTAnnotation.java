/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package com.redhat.ceylon.eclipse.core.model.mirror;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

import com.redhat.ceylon.compiler.loader.mirror.AnnotationMirror;

public class JDTAnnotation implements AnnotationMirror {

    private Map<String, Object> values;

    public JDTAnnotation(AnnotationBinding annotation) {
        values = new HashMap<String, Object>();
        ElementValuePair[] annotationVaues = annotation.getElementValuePairs();
        for (ElementValuePair annotationValue : annotationVaues) {
            String name = new String(annotationValue.getName());
            MethodBinding elementMethod = annotationValue.getMethodBinding();
            Object value = null;
            if (elementMethod != null) {
                value = convertValue(annotationValue.getMethodBinding().returnType, annotationValue.getValue());
            } else {
                value = JDTType.UNKNOWN_TYPE;
            }
            values.put(name, value);
        }
    }

    @Override
    public Object getValue(String fieldName) {
        return values.get(fieldName);
    }

    private Object convertValue(TypeBinding returnType, Object value) {
        if(value.getClass().isArray()){
            Object[] array = (Object[])value;
            List<Object> values = new ArrayList<Object>(array.length);
            TypeBinding elementType = ((ArrayBinding)returnType).elementsType();
            for(Object val : array)
                values.add(convertValue(elementType, val));
            return values;
        }
        if(returnType.isArrayType()){
            // got a single value but expecting array
            List<Object> values = new ArrayList<Object>(1);
            TypeBinding elementType = ((ArrayBinding)returnType).elementsType();
            values.add(convertValue(elementType, value));
            return values;
        }
        if(value instanceof AnnotationBinding){
            return new JDTAnnotation((AnnotationBinding) value);
        }
        if(value instanceof TypeBinding){
            return new JDTType((TypeBinding) value);
        }
        if(value instanceof FieldBinding){
            return new String(((FieldBinding) value).name);
        }
        if(value instanceof Constant){
            Constant constant = (Constant) value;
            return JDTUtils.fromConstant(constant);
        }
        return value;
    }

    @Override
    public Object getValue() {
        return getValue("value");
    }
}
