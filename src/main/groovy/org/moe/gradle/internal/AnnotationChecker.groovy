/*
Copyright 2014-2016 Intel Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.moe.gradle.internal

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

public class AnnotationChecker {

    /**
     * Returns a RegisterOnStartup AnnotationChecker for the specified input stream.
     * @param inputStream input stream
     * @return AnnotationChecker
     */
    public static AnnotationChecker getRegisterOnStartupChecker(InputStream inputStream) {
        def checker = new AnnotationChecker(inputStream, "Lorg/moe/natj/general/ann/RegisterOnStartup;")
        checker.check()
        checker
    }

    /**
     * Input stream containing class data.
     */
    private final InputStream inputStream

    /**
     * The name of the search annotation.
     */
    private final String annotationName

    /**
     * Name of the class.
     */
    private String name

    /**
     * Boolean indicating the search result.
     */
    private boolean hasAnnotation

    /**
     * Creates a new AnnotationChecker for the specified steam and annotation.
     * @param inputStream input stream to check
     * @param annotationName annotation to search for
     */
    private AnnotationChecker(InputStream inputStream, String annotationName) {
        this.inputStream = inputStream
        this.annotationName = annotationName
    }

    /**
     * Returns the class' name.
     * @return class' name
     */
    public String getName() {
        return name
    }

    /**
     * Returns the result of the search.
     * @return true if the class contains the annotation, otherwise false
     */
    public boolean hasAnnotation() {
        return hasAnnotation
    }

    /**
     * Checks for the specified annotation.
     */
    private void check() {
        ClassReader reader = new ClassReader(inputStream)
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            AnnotationVisitor visitAnnotation(String s, boolean b) {
                if (AnnotationChecker.this.annotationName == s) {
                    AnnotationChecker.this.hasAnnotation = true
                }
                return super.visitAnnotation(s, b)
            }

            @Override
            void visit(int i, int i2, String s, String s2, String s3, String[] strings) {
                AnnotationChecker.this.name = s
                super.visit(i, i2, s, s2, s3, strings)
            }
        }, 0)
    }
}
