package com.alibaba.android.arouter.register.launch

import com.alibaba.android.arouter.register.core.RegisterTransform
import com.alibaba.android.arouter.register.utils.Logger
import com.alibaba.android.arouter.register.utils.ScanSetting
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Simple version of AutoRegister plugin for ARouter
 * @author billy.qi email: qiyilike@163.com
 * @since 17/12/06 15:35
 */
public class PluginLaunch implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        println "PluginLaunch, apply: " + project.name
        def isApp = project.plugins.hasPlugin(AppPlugin)

        //only application module needs this plugin to generate register code
        //仅应app模块需要此插件来生成注册码
        if (isApp) {
            Logger.make(project)

            println "PluginLaunch, Project enable arouter-register plugin: " + project.name

            Logger.i('Project enable arouter-register plugin')

            def android = project.extensions.getByType(AppExtension)

            def transformImpl = new RegisterTransform(project)

            //init arouter-auto-register settings
            ArrayList<ScanSetting> list = new ArrayList<>(3)
            list.add(new ScanSetting('IRouteRoot'))
            list.add(new ScanSetting('IInterceptorGroup'))
            list.add(new ScanSetting('IProviderGroup'))
            RegisterTransform.registerList = list


            //register this plugin
            android.registerTransform(transformImpl)
        }
    }

}
