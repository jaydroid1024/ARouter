package com.alibaba.android.arouter.register.utils

import com.alibaba.android.arouter.register.core.RegisterTransform
import com.android.build.api.transform.JarInput
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Scan all class in the package: com/alibaba/android/arouter/
 * find out all routers,interceptors and providers
 * @author billy.qi email: qiyilike@163.com
 * @since 17/3/20 11:48
 */
class ScanUtil {

    /**
     * scan jar file
     * @param jarFile All jar files that are compiled into apk
     * @param destFile dest file after this transform
     *
     * 扫描jar文件
     * @param jarFile 转换前编译到apk中的所有jar文件
     * @param destFile 转换后的文件
     */
    static void scanJar(File jarFile, File destFile) {

        println "==scanJar==, 转换前编译到apk中的所有jar文件:jarFile： " + jarFile
        println "==scanJar==, 转换后的文件:destFile： " + destFile

        if (jarFile) {
            def file = new JarFile(jarFile)
            println "==scanJar==, JarFile：jar文件的封装类:file： " + file

            //返回 zip 文件条目的枚举。
            Enumeration enumeration = file.entries()
            println "==scanJar==, file.entries()：返回 zip 文件条目的枚举。:enumeration： " + enumeration

            while (enumeration.hasMoreElements()) {
                //JarEntry: 此类用于表示 JAR 文件条目。
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                println "==scanJar, JarEntry: 此类用于表示 JAR 文件条目。:jarEntry： " + jarEntry

                String entryName = jarEntry.getName()
                println "==scanJar, JarEntry: 此类用于表示 JAR 文件条目。:jarEntry.getName()： " + entryName

                // annotationProcessor 生成的类的包名的路径 com/alibaba/android/arouter/routes/
                if (entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME)) {
                    println "==scanJar, APT 生成的类的包名的路径前缀 com/alibaba/android/arouter/routes/, while entryName: " + entryName
                    InputStream inputStream = file.getInputStream(jarEntry)
                    println "==scanJar, APT 生成的类的包名的路径前缀 com/alibaba/android/arouter/routes/, while jarEntry: " + jarEntry
                    println "==scanJar, 获取JarFile输入流"
                    scanClass(inputStream)
                    inputStream.close()

                } else if (ScanSetting.GENERATE_TO_CLASS_FILE_NAME == entryName) {
                    //==scanJar, while entryName: com/alibaba/android/arouter/core/LogisticsCenter.class
                    //==scanJar, while destFile: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/app/build/intermediates/transforms/com.alibaba.arouter/debug/43.jar
                    println "==scanJar, LogisticsCenter 字节码文件所在的jar文件 ,while entryName: " + entryName
                    println "==scanJar, LogisticsCenter 字节码文件所在的jar文件 ,while destFile: " + destFile

                    //LogisticsCenter 字节码文件所在的jar文件
                    // mark this jar file contains LogisticsCenter.class
                    // After the scan is complete, we will generate register code into this file
                    //扫描完成后，我们将生成注册码到这个文件中
                    RegisterTransform.fileContainsInitClass = destFile
                }
            }
            file.close()
        }
    }


    static final Set<String> shouldExcludeDexJarSet = ["com.android.support", "android.arch", "androidx.", "org.jetbrains."]

    static boolean shouldProcessPreDexJar(JarInput jarInput) {
        if (jarInput == null || jarInput.file == null || !jarInput.file.exists()) {
            return false
        }
        boolean isShouldExclude = false
        shouldExcludeDexJarSet.each {
            if (jarInput.name.contains(it)) {
                isShouldExclude = true
            }
        }
        return !isShouldExclude
    }


    //过滤Android源码
    static boolean shouldProcessPreDexJar(String path) {
        return !path.contains("com.android.support") && !path.contains("/android/m2repository")
    }

    static boolean shouldProcessClass(String entryName) {
        return entryName != null && entryName.startsWith(ScanSetting.ROUTER_CLASS_PACKAGE_NAME)
    }

    /**
     * scan class file
     * @param class file
     */
    static void scanClass(File file) {
        scanClass(new FileInputStream(file))
    }

    static void scanClass(InputStream inputStream) {
        println "==scanClass, inputStream" + inputStream

        ClassReader cr = new ClassReader(inputStream)
        ClassWriter cw = new ClassWriter(cr, 0)
        ScanClassVisitor cv = new ScanClassVisitor(Opcodes.ASM5, cw)
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
        inputStream.close()
    }

    static class ScanClassVisitor extends ClassVisitor {

        ScanClassVisitor(int api, ClassVisitor cv) {
            super(api, cv)
        }

        void visit(int version, int access, String name, String signature,
                   String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces)
            RegisterTransform.registerList.each { ext ->
                if (ext.interfaceName && interfaces != null) {
                    interfaces.each { itName ->
                        if (itName == ext.interfaceName) {
                            //fix repeated inject init code when Multi-channel packaging
                            if (!ext.classList.contains(name)) {
                                ext.classList.add(name)
                            }
                        }
                    }
                }
            }
        }
    }

}