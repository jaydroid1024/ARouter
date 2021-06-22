package com.alibaba.android.arouter.register.core

import com.alibaba.android.arouter.register.utils.Logger
import com.alibaba.android.arouter.register.utils.ScanSetting
import com.alibaba.android.arouter.register.utils.ScanUtil
import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

/**
 * transform api
 * <p>
 *     1. Scan all classes to find which classes implement the specified interface
 *     2. Generate register code into class file: {@link ScanSetting#GENERATE_TO_CLASS_FILE_NAME}
 * @author billy.qi email: qiyilike@163.com
 * @since 17/3/21 11:48
 */
class RegisterTransform extends Transform {

    // app
    Project project
    // 扫描目标类的配置
    static ArrayList<ScanSetting> registerList
    //
    static File fileContainsInitClass

    RegisterTransform(Project project) {
        this.project = project
    }

    /**
     * name of this transform
     * @return
     */
    @Override
    String getName() {
        //"com.alibaba.arouter
        return ScanSetting.PLUGIN_NAME
    }

    /**
     * 用于指明 Transform 的输入类型，可以作为输入过滤的手段。
     * 这里过滤字节码
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 用于指明 Transform 的作用域
     * 这里扫描整个项目
     */
    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 用于指明是否是增量构建
     * 返回 Transform 是否可以执行增量工作。
     * 如果是，则 TransformInput 可能包含已更改删除的文件列表，除非其他内容触发了非增量运行。
     * 这里不采用增量构建，保证每次输入都是原始的数据
     */
    @Override
    boolean isIncremental() {
        return false
    }

    /**
     * 拦截AGP的构建过程
     */

    @Override
    void transform(Context context, Collection<TransformInput> inputs
                   , Collection<TransformInput> referencedInputs
                   , TransformOutputProvider outputProvider
                   , boolean isIncremental) throws IOException, TransformException, InterruptedException {

        Logger.i('Start scan register info in jar file.')

        //Start scan register info in jar file. app
        println "Start scan register info in jar file. " + project.name


        long startTime = System.currentTimeMillis()
        boolean leftSlash = File.separator == '/'

        inputs.each { TransformInput input ->

            // scan all jars
            input.jarInputs.each { JarInput jarInput ->
                String destName = jarInput.name

                // rename jar files
                def hexName = DigestUtils.md5Hex(jarInput.file.absolutePath)
                if (destName.endsWith(".jar")) {
                    destName = destName.substring(0, destName.length() - 4)
                }

                // input file
                File src = jarInput.file
                // output file
                File dest = outputProvider.getContentLocation(destName + "_" + hexName, jarInput.contentTypes, jarInput.scopes, Format.JAR)


                //scan all jars, 预处理目标文件名字: com.android.support:support-v4:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/25794c35e1444f6a10948a9b34680931/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:appcompat-v7:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/2eb55527be73fcbd9e0a2c5d4406c969/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.alibaba:arouter-annotation:1.0.6
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.alibaba/arouter-annotation/1.0.6/667fa943838b31d3a94cce6fe9e0b786cd9445ae/arouter-annotation-1.0.6.jar
                //scan all jars, 预处理目标文件名字: com.alibaba:fastjson:1.2.48
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.alibaba/fastjson/1.2.48/24001c59a15f9293667c9e052ac34b7a4af974de/fastjson-1.2.48.jar
                //scan all jars, 预处理目标文件名字: org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.3.72
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-android-extensions-runtime/1.3.72/83423235971335be77d2ea025008bc9959738ffc/kotlin-android-extensions-runtime-1.3.72.jar
                //scan all jars, 预处理目标文件名字: org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.72
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-jdk7/1.3.72/3adfc2f4ea4243e01204be8081fe63bde6b12815/kotlin-stdlib-jdk7-1.3.72.jar
                //scan all jars, 预处理目标文件名字: com.android.support.constraint:constraint-layout:1.1.3
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/85c84b197be168451828540ef4a9a1a5/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-media-compat:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/32897013b7f3dab8afcde44c05a67fe1/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-fragment:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/7d2808d70cec4fc56e86267c80ce4624/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:animated-vector-drawable:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/d7eaeb8533390a0153eb57c5f9c44f0a/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-core-ui:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/8ff4d69cae5150d824810a98f47cfe06/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-core-utils:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/d2b634b13f67d68060e878ebbd80d111/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-vector-drawable:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/be9db72aa5246d9181e7089d4ea66f0e/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:loader:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/2825392dd5d33d6a91036dcc5d9c1b8d/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:viewpager:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/88c0e7780cdf0b8adc0b4913752c67c2/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:coordinatorlayout:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/55cbcd8c1cd4444feda2b4dfd481795f/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:drawerlayout:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/519a8742c077961e17c0d5636116d943/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:slidingpanelayout:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/e9e137f167790952f1c9dab3bc01f813/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:customview:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/de2051bfc3b721fef317421d73c58d52/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:swiperefreshlayout:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/e28119f9200e6867ec2538f9e5c61944/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:asynclayoutinflater:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/4e616c3573214b152a5e67a4dc665eeb/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-compat:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/0d831a2ba21a4de6adf0ac5b038a41c0/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:versionedparcelable:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/66c4d83d659474259d3fc91e570a3a7e/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:collections:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.android.support/collections/28.0.0/c1bcdade4d3cc2836130424a3f3e4182c666a745/collections-28.0.0.jar
                //scan all jars, 预处理目标文件名字: com.android.support:cursoradapter:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/16d3a15d18e700424c304be267e59b00/jars/classes.jar
                //scan all jars, 预处理目标文件名字: android.arch.lifecycle:runtime:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/bbfd43eb133404481c45ea1cf3caeb38/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:documentfile:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/99e405b70491dd3d545f126459dbfb25/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:localbroadcastmanager:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/2a43075e589fe6016b5c3e7a4a227079/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:print:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/99eebcec622df41cbd9f03fe677f333e/jars/classes.jar
                //scan all jars, 预处理目标文件名字: com.android.support:interpolator:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/353295de78502997fae95ce1baa55d45/jars/classes.jar
                //scan all jars, 预处理目标文件名字: android.arch.lifecycle:viewmodel:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/4c9c83e95981dc07c3580101abd52423/jars/classes.jar
                //scan all jars, 预处理目标文件名字: android.arch.lifecycle:livedata:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/194c874e55b382a2587337499b9e4bbb/jars/classes.jar
                //scan all jars, 预处理目标文件名字: android.arch.lifecycle:livedata-core:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/68f1d690e576e936922c55e383ca80e8/jars/classes.jar
                //scan all jars, 预处理目标文件名字: android.arch.lifecycle:common:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/android.arch.lifecycle/common/1.1.1/207a6efae6a3555e326de41f76bdadd9a239cbce/common-1.1.1.jar
                //scan all jars, 预处理目标文件名字: android.arch.core:runtime:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/1f5a7ad9df490aa7e12f29c78621fdb9/jars/classes.jar
                //scan all jars, 预处理目标文件名字: android.arch.core:common:1.1.1
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/android.arch.core/common/1.1.1/e55b70d1f5620db124b3e85a7f4bdc7bd48d9f95/common-1.1.1.jar
                //scan all jars, 预处理目标文件名字: com.android.support:support-annotations:28.0.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.android.support/support-annotations/28.0.0/ed73f5337a002d1fd24339d5fb08c2c9d9ca60d8/support-annotations-28.0.0.jar
                //scan all jars, 预处理目标文件名字: org.jetbrains.kotlin:kotlin-stdlib:1.3.72
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.3.72/8032138f12c0180bc4e51fe139d4c52b46db6109/kotlin-stdlib-1.3.72.jar
                //scan all jars, 预处理目标文件名字: com.android.support.constraint:constraint-layout-solver:1.1.3
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.android.support.constraint/constraint-layout-solver/1.1.3/bde0667d7414c16ed62d3cfe993cff7f9d732373/constraint-layout-solver-1.1.3.jar
                //scan all jars, 预处理目标文件名字: org.jetbrains.kotlin:kotlin-stdlib-common:1.3.72
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib-common/1.3.72/6ca8bee3d88957eaaaef077c41c908c9940492d8/kotlin-stdlib-common-1.3.72.jar
                //scan all jars, 预处理目标文件名字: org.jetbrains:annotations:13.0
                //scan all jars, 预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar
                //scan all jars, 预处理目标文件名字: :module-java
                //scan all jars, 预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/module-java/build/intermediates/intermediate-jars/debug/classes.jar
                //scan all jars, 预处理目标文件名字: :module-java-export
                //scan all jars, 预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/module-java-export/build/intermediates/intermediate-jars/debug/classes.jar
                //scan all jars, 预处理目标文件名字: :module-kotlin
                //scan all jars, 预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/module-kotlin/build/intermediates/intermediate-jars/debug/classes.jar
                //scan all jars, 预处理目标文件名字: :arouter-api
                //scan all jars, 预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/arouter-api/build/intermediates/intermediate-jars/debug/classes.jar
//                println "scan all jars, 预处理目标文件名字: " + destName
//                println "scan all jars, 预处理目标文件路径: " + src
                //todo 几个目录需要关注
                ///Applications/Android Studio.app/Contents/caches/modules-2/files-2.1
                ///Applications/Android Studio.app/Contents/caches/transforms-2/files-2.1/
                ///Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/[module-name]/build/intermediates/intermediate-jars/debug/


                //scan jar file to find classes  ，过滤掉Android源码
                // return !path.contains("com.android.support") && !path.contains("/android/m2repository")
//                if (ScanUtil.shouldProcessPreDexJar(src.absolutePath)) {


                if (ScanUtil.shouldProcessPreDexJar(jarInput)) {
                    //scan all jars, 排除系统库后的预处理目标文件名字: com.alibaba:arouter-annotation:1.0.6
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.alibaba/arouter-annotation/1.0.6/667fa943838b31d3a94cce6fe9e0b786cd9445ae/arouter-annotation-1.0.6.jar
                    //scan all jars, 排除系统库后的预处理目标文件名字: com.alibaba:fastjson:1.2.48
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/com.alibaba/fastjson/1.2.48/24001c59a15f9293667c9e052ac34b7a4af974de/fastjson-1.2.48.jar
                    //scan all jars, 排除系统库后的预处理目标文件名字: org.jetbrains:annotations:13.0
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Applications/Android Studio.app/Contents/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar
                    //scan all jars, 排除系统库后的预处理目标文件名字: :module-java
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/module-java/build/intermediates/intermediate-jars/debug/classes.jar
                    //scan all jars, 排除系统库后的预处理目标文件名字: :module-java-export
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/module-java-export/build/intermediates/intermediate-jars/debug/classes.jar
                    //scan all jars, 排除系统库后的预处理目标文件名字: :module-kotlin
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/module-kotlin/build/intermediates/intermediate-jars/debug/classes.jar
                    //scan all jars, 排除系统库后的预处理目标文件名字: :arouter-api
                    //scan all jars, 排除系统库后的预处理目标文件路径: /Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/arouter-api/build/intermediates/intermediate-jars/debug/classes.jar
                    println "scan all jars, 排除系统库后的预处理目标文件名字: " + destName
                    println "scan all jars, 排除系统库后的预处理目标文件路径: " + src


                    //扫描 Jar
                    ScanUtil.scanJar(src, dest)
                }
                //复制 Jar
                FileUtils.copyFile(src, dest)

            }
            // scan class files
            input.directoryInputs.each { DirectoryInput directoryInput ->
                File dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                String root = directoryInput.file.absolutePath
                if (!root.endsWith(File.separator))
                    root += File.separator
                directoryInput.file.eachFileRecurse { File file ->
                    def path = file.absolutePath.replace(root, '')
                    if (!leftSlash) {
                        path = path.replaceAll("\\\\", "/")
                    }
                    if (file.isFile() && ScanUtil.shouldProcessClass(path)) {
                        ScanUtil.scanClass(file)
                    }
                }

                // copy to dest
                FileUtils.copyDirectory(directoryInput.file, dest)
            }
        }

        Logger.i('Scan finish, current cost time ' + (System.currentTimeMillis() - startTime) + "ms")

        if (fileContainsInitClass) {
            registerList.each { ext ->
                Logger.i('Insert register code to file ' + fileContainsInitClass.absolutePath)

                if (ext.classList.isEmpty()) {
                    Logger.e("No class implements found for interface:" + ext.interfaceName)
                } else {
                    ext.classList.each {
                        Logger.i(it)
                    }
                    RegisterCodeGenerator.insertInitCodeTo(ext)
                }
            }
        }

        Logger.i("Generate code finish, current cost time: " + (System.currentTimeMillis() - startTime) + "ms")
    }
}
