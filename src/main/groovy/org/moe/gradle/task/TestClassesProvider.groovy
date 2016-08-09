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

package org.moe.gradle.task

import org.moe.gradle.BasePlugin
import org.moe.gradle.util.StringUtil
import org.moe.gradle.util.TaskUtil
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Rule
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.*

import java.util.jar.JarFile

class TestClassesProvider extends BaseTask {

    static String NAME = "TestClassesProvider"

    /*
    Task inputs
     */

    @InputFiles
    Collection<File> inputFiles

    /*
    Task outputs
     */

    @OutputFile
    File classListFile

    @OutputFile
    File log

    /*
    Task action
     */

    @TaskAction
    void taskAction() {
        project.logger.debug("|--- $name : $NAME ---|")
        project.logger.debug("|< inputFiles: ${getInputFiles()}")
        project.logger.debug("|> classListFile: ${getClassListFile()}")
        project.logger.debug("|> log: ${getLog()}")

        // Reset logs
        getLog().text = ""
        getClassListFile().text = ""

        // Create class map
        ClassMap classMap = new ClassMap()

        getInputFiles().each {
            getLog().text += "Checking: ${it.absolutePath}\n"
            if (!it.exists()) {
                // Skip
            } else if (it.isDirectory()) {
                project.fileTree(dir: it).visit { element ->
                    File f = element.file
                    if (!f.getName().endsWith(".class")) {
                        return
                    }

                    ClassTestAnnotationFinder indexer = new ClassTestAnnotationFinder(new FileInputStream(f))
                    indexer.index(classMap);
                }
            } else if (it.name.endsWith(".jar")) {
                JarFile file = new JarFile(it)
                file.entries().each {
                    if (!it.getName().endsWith(".class")) {
                        return
                    }

                    ClassTestAnnotationFinder indexer = new ClassTestAnnotationFinder(file.getInputStream(it))
                    indexer.index(classMap);
                }
            } else {
                project.logger.warn("Skipping test class check in $it")
            }
        }

        classMap.resolve(getClassListFile())
    }

    /**
     * ClassTestAnnotationFinder class is a class visitor class and looks for any information on
     * whether this class contains junit tests or not.
     */
    private class ClassTestAnnotationFinder extends ClassVisitor {

        /**
         * Input stream to read the class data from.
         */
        private InputStream inputStream

        /**
         * Boolean indicating whether this class contains junit tests.
         */
        private boolean hasFoundTestIndication = false

        /**
         * Name of the parsed class.
         */
        private String className

        /**
         * Name of the parsed class' superclass.
         */
        private String superName

        /**
         * boolean indicating whether a class can be instantiated or not.
         */
        private boolean isInstantiatable

        /**
         * Creates a new ClassTestAnnotationFinder instance.
         * @param inputStream input stream to read class from
         */
        ClassTestAnnotationFinder(InputStream inputStream) {
            super(Opcodes.ASM5)
            this.inputStream = inputStream
        }

        /**
         * Indexes the class.
         * @param map ClassMap to add informaition to
         */
        public void index(ClassMap map) {
            ClassReader reader = new ClassReader(inputStream)
            reader.accept(this, 0)
            if (className.startsWith("org/junit/") || className.startsWith("junit/")) {
                return
            }
            ClassRep rep = map.add(className, superName, isInstantiatable)
            if (hasFoundTestIndication) {
                rep.testCase = ClassRep.IS_TEST
            }
        }

        /**
         * Sets the hasFoundTestIndication to true.
         */
        public void setFoundTestIndication() {
            hasFoundTestIndication = true;
            project.logger.debug("----------X found an indicator")
        }

        @Override
        void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if (this.className != null || this.superName != null) {
                throw new GradleException("Didn't really prepare for this case, please report!")
            }
            this.className = name
            this.superName = superName
            this.isInstantiatable = (access & Opcodes.ACC_ABSTRACT) == 0 && (access & Opcodes.ACC_INTERFACE) == 0
            project.logger.debug("$name from $superName (${this.isInstantiatable})")
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String desc, String sign, String[] exceptions) {
            if (hasFoundTestIndication) {
                return null
            }
            project.logger.debug("    $name - $desc")
            return new MethodTestAnnotationFinder(this)
        }
    }

    /**
     * MethodTestAnnotationFinder class is a method visitor class and looks for junit annotations on methods.
     */
    private class MethodTestAnnotationFinder extends MethodVisitor {

        /**
         * List of known junit 4 annotations.
         */
        private static final List<String> annotationClasses = []

        static {
            annotationClasses.add("Lorg/junit/Before;")
            annotationClasses.add("Lorg/junit/BeforeClass;")
            annotationClasses.add("Lorg/junit/Ignore;")
            annotationClasses.add("Lorg/junit/AfterClass;")
            annotationClasses.add("Lorg/junit/After;")
            annotationClasses.add("Lorg/junit/Test;")
        }

        /**
         * Parent visitor of this method visitor.
         */
        private final ClassTestAnnotationFinder classVisitor

        /**
         * Creates a new MethodTestAnnotationFinder instance.
         * @param classVisitor parent visitor
         */
        MethodTestAnnotationFinder(ClassTestAnnotationFinder classVisitor) {
            super(Opcodes.ASM5)
            this.classVisitor = classVisitor
        }

        @Override
        AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            project.logger.debug("        $desc")
            if (annotationClasses.contains(desc)) {
                classVisitor.setFoundTestIndication()
            }
            return null
        }
    }

    /**
     * ClassRep contains information about a class excluding it's name. Instances of class are used
     * as the values in ClassMap.
     */
    private class ClassRep {

        /**
         * Undefined state constant.
         */
        public static final int UNDEFINED = -1

        /**
         * Not a test state constant.
         */
        public static final int NOT_TEST = 0

        /**
         * Is a test state constant.
         */
        public static final int IS_TEST = 1

        /**
         * Name of the parent class.
         */
        final String parentName

        /**
         * Instantiatable information.
         */
        final boolean isInstantiatable

        /**
         * Class' test state.
         */
        int testCase = UNDEFINED

        /**
         * Creates a new ClassRep instance.
         * @param parentName parent class' name
         * @param isInstantiatable instantiatable information
         */
        ClassRep(String parentName, boolean isInstantiatable) {
            this.parentName = parentName
            this.isInstantiatable = isInstantiatable
        }
    }

    /**
     * ClassMap class resolves a collection of classes, checks which class is somehow inherited
     * from junit.framework.TestCase.
     */
    private class ClassMap {

        /**
         * Map containing the classes, key: class name, value: class representation
         */
        private final Map<String, ClassRep> map = [:]

        /**
         * Add a class to this map.
         * @param name name of the class
         * @param superName name oth the superclass
         * @param isInstantiatable instantiatable information
         * @return a newly created ClassRep for the specified class
         */
        public ClassRep add(String name, String superName, boolean isInstantiatable) {
            project.logger.debug("Creating entry: $name, $superName, $isInstantiatable")
            ClassRep rep = new ClassRep(superName, isInstantiatable)
            map.put(name, rep)
            return rep
        }

        /**
         * Resolve all classes in this map and write classes onto the output.
         * @param output output to write to
         */
        public void resolve(File output) {
            StringBuilder builder = new StringBuilder()
            map.each {
                project.logger.debug("@resolving: $it.key")
                switch (resolveClass(it.value)) {
                    case ClassRep.IS_TEST:
                        project.logger.debug("+ $it.key")
                        if (it.value.isInstantiatable) {
                            builder.append("${it.key.replaceAll("/", ".")}\n")
                        }
                        break
                    case ClassRep.NOT_TEST:
                        project.logger.debug("- $it.key")
                        // It is not a TestCase
                        break
                    default:
                        throw new GradleException("Illegal value in testCase ($it.value.testCase)")
                        break
                }
            }
            output.text = builder.toString()
        }

        /**
         * Check whether a class is inherited from TestCase.
         * @param rep class representation to check
         * @return resolved state's value
         */
        private int resolveClass(ClassRep rep) {
            // It if is set, return it
            if (rep.testCase != ClassRep.UNDEFINED) {
                return rep.testCase
            }

            // If parent is not found, default to NO
            if (rep.parentName == null || rep.parentName.length() == 0) {
                rep.testCase = ClassRep.NOT_TEST
                return rep.testCase
            }

            // Check is from TestCase
            if ("junit/framework/TestCase" == rep.parentName) {
                rep.testCase = ClassRep.IS_TEST
                return rep.testCase
            }

            // Inherit from parent class
            ClassRep parent = map.get(rep.parentName)
            if (parent == null) {
                rep.testCase = ClassRep.NOT_TEST
            } else {
                rep.testCase = resolveClass(parent)
            }
            return rep.testCase
        }
    }

    public static String getTaskName(SourceSet sourceSet) {
        return BaseTask.composeTaskName(sourceSet.name, TestClassesProvider.NAME)
    }

    public static Rule addRule(Project project, JavaPluginConvention javaConvention) {
        // Prepare constants
        final String TASK_NAME = TestClassesProvider.NAME
        final String ELEMENTS_DESC = '<SourceSet>'
        final String PATTERN = "${BasePlugin.MOE}${ELEMENTS_DESC}${TASK_NAME}"

        // Add rule
        project.tasks.addRule("Pattern: $PATTERN: Creates the classlist.txt file."
                , { String taskName ->
            project.logger.info("Evaluating for $TASK_NAME rule: $taskName")

            // Check for prefix, suffix and get elements in-between
            List<String> elements = StringUtil.getElemsInRule(taskName, BasePlugin.MOE, TASK_NAME)

            // Prefix or suffix failed
            if (elements == null) {
                return null
            }

            // Check number of elements
            TaskUtil.assertSize(elements, 1, ELEMENTS_DESC)

            // Check element values & configure task on success
            SourceSet sourceSet = TaskUtil.getSourceSet(javaConvention, elements[0])
            TestClassesProvider.create(project, sourceSet)
        })
    }

    public static TestClassesProvider create(Project project, SourceSet sourceSet) {
        // Construct default output path
        final String outPath = "${BasePlugin.MOE}/${sourceSet.name}"

        // Create task
        final String taskName = getTaskName(sourceSet)
        TestClassesProvider testClassesProviderTask = project.tasks.create(taskName, TestClassesProvider.class)
        testClassesProviderTask.description = "Generates classlist.txt file ($outPath)."

        // Add dependencies
        final String proguardTaskName = ProGuard.getTaskName(sourceSet)
        ProGuard proguardTask = (ProGuard) project.tasks.getByName(proguardTaskName)
        testClassesProviderTask.dependsOn proguardTask

        String compileJavaTaskName
        if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) {
            compileJavaTaskName = JavaPlugin.COMPILE_JAVA_TASK_NAME
        } else if (sourceSet.name == SourceSet.TEST_SOURCE_SET_NAME) {
            compileJavaTaskName = JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME
        } else {
            throw new GradleException("Unsupported SourceSet $sourceSet.name")
        }

        // Update convention mapping
        testClassesProviderTask.conventionMapping.inputFiles = {
            [proguardTask.outJar]
        }
        testClassesProviderTask.conventionMapping.classListFile = {
            project.file("${project.buildDir}/${outPath}/classlist.txt")
        }
        testClassesProviderTask.conventionMapping.log = {
            project.file("${project.buildDir}/${outPath}/ClassListProvider.log")
        }

        return testClassesProviderTask
    }
}
