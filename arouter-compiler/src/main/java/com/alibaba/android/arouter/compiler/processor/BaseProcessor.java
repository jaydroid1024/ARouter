package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Logger;
import com.alibaba.android.arouter.compiler.utils.TypeUtils;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.alibaba.android.arouter.compiler.utils.Consts.KEY_GENERATE_DOC_NAME;
import static com.alibaba.android.arouter.compiler.utils.Consts.KEY_MODULE_NAME;
import static com.alibaba.android.arouter.compiler.utils.Consts.NO_MODULE_NAME_TIPS;
import static com.alibaba.android.arouter.compiler.utils.Consts.VALUE_ENABLE;

/**
 * Base Processor
 *
 * @author zhilong [Contact me.](mailto:zhilong.lzl@alibaba-inc.com)
 * @version 1.0
 * @since 2019-03-01 12:31
 */
public abstract class BaseProcessor extends AbstractProcessor {
    Filer mFiler;
    Logger logger;
    Types types;
    Elements elementUtils;
    TypeUtils typeUtils;
    // Module name, maybe its 'app' or others
    String moduleName = null;
    // 是否需要生成路由器文档
    boolean generateDoc;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        //初始化公共的注解处理工具：文件，类型，打印等
        initCompileTools(processingEnv);
        //初始化支持的选项参数，KEY_MODULE_NAME，KEY_GENERATE_DOC_NAME
        initSupportedOptions(processingEnv);
    }

    private void initSupportedOptions(ProcessingEnvironment processingEnv) {
        // Attempt to get user configuration [moduleName]
        // 尝试获取用户配置
        Map<String, String> options = processingEnv.getOptions();
        // moduleName, generateDoc 用户配置的两个参数, 通过复写getSupportedOptions 方法注册参数的key
        if (MapUtils.isNotEmpty(options)) {
            moduleName = options.get(KEY_MODULE_NAME);
            generateDoc = VALUE_ENABLE.equals(options.get(KEY_GENERATE_DOC_NAME));
        }

        //moduleName 参数是必填的，用于分组，懒加载
        if (StringUtils.isNotEmpty(moduleName)) {
            //去除分隔符-
            moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "");
            logger.info("The user has configuration the module name, it was [" + moduleName + "]");
        } else {
            logger.error(NO_MODULE_NAME_TIPS);
            throw new RuntimeException("ARouter::Compiler >>> No module name, for more information, look at gradle log.");
        }
    }

    private void initCompileTools(ProcessingEnvironment processingEnv) {
        //生成文件的工具，通过这个工具创建的文件将为实现此接口的注释处理工具所知，从而更好地使工具能够管理它们。
        mFiler = processingEnv.getFiler();
        //元素的类型校验工具
        types = processingEnv.getTypeUtils();
        //操作元素的工具
        elementUtils = processingEnv.getElementUtils();
        //用于类型交换的工具
        typeUtils = new TypeUtils(types, elementUtils);
        //用于日志打印，统一日志格式的工具
        logger = new Logger(processingEnv.getMessager());
    }

    /**
     * @return
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * @return
     */
    @Override
    public Set<String> getSupportedOptions() {
        return new HashSet<String>() {{
            this.add(KEY_MODULE_NAME);
            this.add(KEY_GENERATE_DOC_NAME);
        }};
    }
}
