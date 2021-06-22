package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.entity.RouteDoc;
import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.alibaba.android.arouter.facade.enums.RouteType;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.alibaba.android.arouter.facade.model.RouteMeta;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardLocation;

import static com.alibaba.android.arouter.compiler.utils.Consts.ACTIVITY;
import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_ROUTE;
import static com.alibaba.android.arouter.compiler.utils.Consts.FRAGMENT;
import static com.alibaba.android.arouter.compiler.utils.Consts.IPROVIDER_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.IROUTE_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.ITROUTE_ROOT;
import static com.alibaba.android.arouter.compiler.utils.Consts.METHOD_LOAD_INTO;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_GROUP;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_PROVIDER;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_ROOT;
import static com.alibaba.android.arouter.compiler.utils.Consts.PACKAGE_OF_GENERATE_DOCS;
import static com.alibaba.android.arouter.compiler.utils.Consts.PACKAGE_OF_GENERATE_FILE;
import static com.alibaba.android.arouter.compiler.utils.Consts.SEPARATOR;
import static com.alibaba.android.arouter.compiler.utils.Consts.SERVICE;
import static com.alibaba.android.arouter.compiler.utils.Consts.WARNING_TIPS;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * A processor used for find route.
 *
 * @author Alex <a href="mailto:zhilong.liu@aliyun.com">Contact me.</a>
 * @version 1.0
 * @since 16/8/15 下午10:08
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({ANNOTATION_TYPE_ROUTE, ANNOTATION_TYPE_AUTOWIRED})
public class RouteProcessor extends BaseProcessor {

    // ModuleName and routeMeta.
    private Map<String, Set<RouteMeta>> groupMap = new HashMap<>();

    // Map of root metas, used for generate class file in order.
    private Map<String, String> rootMap = new TreeMap<>(); // //["arouter","ARouter$$Group$$arouter"]

    private TypeMirror iProvider = null;

    // Writer used for write doc
    private Writer docWriter;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        //初始化路由表文档的空文件
        initGeneratedRouteDocJsonFile();

        //com.alibaba.android.arouter.facade.template.IProvider 接口类型
        iProvider = elementUtils.getTypeElement(Consts.IPROVIDER).asType();

        logger.info(">>> RouteProcessor init. <<<");
    }

    private void initGeneratedRouteDocJsonFile() {
        if (generateDoc) {
            try {
                docWriter = mFiler.createResource(StandardLocation.SOURCE_OUTPUT, //文件的位置。
                        PACKAGE_OF_GENERATE_DOCS, //文件的路径。
                        "arouter-map-of-" + moduleName + ".json"  //文件的名字。
                ).openWriter();
            } catch (IOException e) {
                logger.error("Create doc writer failed, because " + e.getMessage());
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param annotations
     * @param roundEnv    如果返回 {@code true}，则声明注释类型，并且不会要求后续处理器处理它们；
     *                    如果返回 {@code false}，则注释类型是无人认领的，可能会要求后续处理器处理它们。
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (CollectionUtils.isNotEmpty(annotations)) { // 注解判空
            //获取标注了 Route 注解的元素集合
            Set<? extends Element> routeElements = roundEnv.getElementsAnnotatedWith(Route.class);
            try {
                this.parseRoutes(routeElements); //解析注解
            } catch (Exception e) {
                logger.error(e);
            }
            return true; //声明注释类型，并且不会要求后续处理器处理它们；
        }

        return false; //注释类型是无人认领的，可能会要求后续处理器处理它们。
    }

    /**
     * 解析路由信息并生成类文件
     *
     * @param routeElements
     * @throws IOException
     */
    private void parseRoutes(Set<? extends Element> routeElements) throws IOException {

        if (CollectionUtils.isNotEmpty(routeElements)) { // 注解元素判空


            logger.info(">>> Found routes, size is " + routeElements.size() + " <<<");

            rootMap.clear();

            // 类型相关的准备工作
            // TypeMirror :表示 Java 编程语言中的一种类型。类型包括基本类型、声明类型（类和接口类型）、数组类型、类型变量和空类型。
            // 还表示通配符类型参数、可执行文件的签名和返回类型，以及对应于包和关键字 {@code void} 的伪类型。
            // interface of System
            //android.app.Activity
            TypeMirror type_Activity = elementUtils.getTypeElement(ACTIVITY).asType();
            //android.app.Service
            TypeMirror type_Service = elementUtils.getTypeElement(SERVICE).asType();
            //android.app.Fragment
            TypeMirror fragmentTm = elementUtils.getTypeElement(FRAGMENT).asType();
            //android.support.v4.app.Fragment
            TypeMirror fragmentTmV4 = elementUtils.getTypeElement(Consts.FRAGMENT_V4).asType();
            //TypeElement: 表示一个类或接口程序元素。提供对有关类型及其成员的信息的访问。请注意，枚举类型是一种类，注释类型是一种接口。
            // Interface of ARouter
            //com.alibaba.android.arouter.facade.template.IRouteGroup
            TypeElement type_IRouteGroup = elementUtils.getTypeElement(IROUTE_GROUP);
            //com.alibaba.android.arouter.facade.template.IInterceptor
            TypeElement type_IProviderGroup = elementUtils.getTypeElement(IPROVIDER_GROUP);
            //ClassName: 顶级类和成员类的完全限定类名。
            //com.alibaba.android.arouter.facade.model.RouteMeta  它包含基本的路由信息。
            ClassName routeMetaCn = ClassName.get(RouteMeta.class);
            //com.alibaba.android.arouter.facade.enums.RouteType  路由类型
            ClassName routeTypeCn = ClassName.get(RouteType.class);

            //注: ARouter::Compiler >>> Found routes, type_Activity android.app.Activity <<<
            //注: ARouter::Compiler >>> Found routes, type_Service android.app.Service <<<
            //注: ARouter::Compiler >>> Found routes, fragmentTm android.app.Fragment <<<
            //注: ARouter::Compiler >>> Found routes, fragmentTmV4 android.support.v4.app.Fragment <<<
            //注: ARouter::Compiler >>> Found routes, type_IRouteGroup com.alibaba.android.arouter.facade.template.IRouteGroup <<<
            //注: ARouter::Compiler >>> Found routes, type_IProviderGroup com.alibaba.android.arouter.facade.template.IProviderGroup <<<
            //注: ARouter::Compiler >>> Found routes, routeMetaCn com.alibaba.android.arouter.facade.model.RouteMeta <<<
            //注: ARouter::Compiler >>> Found routes, routeTypeCn com.alibaba.android.arouter.facade.enums.RouteType <<<
            logger.info(">>> Found routes, type_Activity " + type_Activity + " <<<");
            logger.info(">>> Found routes, type_Service " + type_Service + " <<<");
            logger.info(">>> Found routes, fragmentTm " + fragmentTm + " <<<");
            logger.info(">>> Found routes, fragmentTmV4 " + fragmentTmV4 + " <<<");
            logger.info(">>> Found routes, type_IRouteGroup " + type_IRouteGroup + " <<<");
            logger.info(">>> Found routes, type_IProviderGroup " + type_IProviderGroup + " <<<");
            logger.info(">>> Found routes, routeMetaCn " + routeMetaCn + " <<<");
            logger.info(">>> Found routes, routeTypeCn " + routeTypeCn + " <<<");


            /*
            todo  ARouter$$Group$$kotlin  IRouteGroup
            * DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER.
            public class ARouter$$Group$$kotlin implements IRouteGroup {
                @Override
                public void loadInto(Map<String, RouteMeta> atlas) {
                    atlas.put("/kotlin/java", RouteMeta.build(RouteType.ACTIVITY, TestNormalActivity.class, "/kotlin/java", "kotlin", null, -1, -2147483648));
                    atlas.put("/kotlin/test", RouteMeta.build(RouteType.ACTIVITY, KotlinTestActivity.class, "/kotlin/test", "kotlin", new java.util.HashMap<String, Integer>(){{put("name", 8); put("age", 3); }}, -1, -2147483648));
                }
            }
             */


            /*
            todo ARouter$$Root$$modulekotlin  IRouteRoot
            * DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER.
            public class ARouter$$Root$$modulekotlin implements IRouteRoot {
                @Override
                public void loadInto(Map<String, Class<? extends IRouteGroup>> routes) {
                    routes.put("kotlin", ARouter$$Group$$kotlin.class);
                }
            }
             */

            /*

            todo ARouter$$Providers$$arouterapi IProviderGroup
            * DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER.
            public class ARouter$$Providers$$arouterapi implements IProviderGroup {
                @Override
                public void loadInto(Map<String, RouteMeta> providers) {
                    providers.put("com.alibaba.android.arouter.facade.service.AutowiredService", RouteMeta.build(RouteType.PROVIDER, AutowiredServiceImpl.class, "/arouter/service/autowired", "arouter", null, -1, -2147483648));
                    providers.put("com.alibaba.android.arouter.facade.service.InterceptorService", RouteMeta.build(RouteType.PROVIDER, InterceptorServiceImpl.class, "/arouter/service/interceptor", "arouter", null, -1, -2147483648));
                }
            }
             */


            // javapoet 开始生成需要的类文件
            /*
               Build input type, format as :

               ```Map<String, Class<? extends IRouteGroup>>```
             */
            ParameterizedTypeName inputMapTypeOfRoot = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ParameterizedTypeName.get(
                            ClassName.get(Class.class),
                            WildcardTypeName.subtypeOf(ClassName.get(type_IRouteGroup))
                    )
            );


            /*

              ```Map<String, RouteMeta>```
             */
            ParameterizedTypeName inputMapTypeOfGroup = ParameterizedTypeName.get(
                    ClassName.get(Map.class),
                    ClassName.get(String.class),
                    ClassName.get(RouteMeta.class)
            );

            /*
              Build input param name.
             */

            //Map<String, Class<? extends IRouteGroup>> routes
            ParameterSpec rootParamSpec = ParameterSpec.builder(inputMapTypeOfRoot, "routes").build();
            //Map<String, RouteMeta> atlas
            ParameterSpec groupParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "atlas").build();
            //Map<String, RouteMeta> providers
            ParameterSpec providerParamSpec = ParameterSpec.builder(inputMapTypeOfGroup, "providers").build();  // Ps. its param type same as groupParamSpec!


            /*
              Build method : 'loadInto'
                 @Override
                public void loadInto(Map<String, RouteMeta> atlas) {}
             */
            MethodSpec.Builder loadIntoMethodOfRootBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(rootParamSpec);


            //  Follow a sequence, find out metas of group first, generate java file, then statistics them as root.
            //按照顺序，先找出group的metas，生成java文件，然后以root身份统计。
            for (Element element : routeElements) {
                //注: ARouter::Compiler >>> element is com.alibaba.android.arouter.core.AutowiredServiceImpl <<<
                //注: ARouter::Compiler >>> element is com.alibaba.android.arouter.core.InterceptorServiceImpl <<<
                logger.info(">>> element is 对应RouteMeta 中 rawType  " + element + " <<<");

                //标注了这个注解的类的类型，xxxActivity
                TypeMirror tm = element.asType();
                //注: ARouter::Compiler >>> 标注了这个注解的类的类型有很多吗 : com.alibaba.android.arouter.demo.module1.testactivity.Test1Activity <<<
                //注: ARouter::Compiler >>> 标注了这个注解的类的类型有很多吗 : com.alibaba.android.arouter.demo.module1.TestModuleActivity <<<
                //注: ARouter::Compiler >>> 标注了这个注解的类的类型有很多吗 : com.alibaba.android.arouter.demo.module1.BlankFragment <<<
                //注: ARouter::Compiler >>> 标注了这个注解的类的类型有很多吗 : com.alibaba.android.arouter.demo.module1.testservice.SingleService <<<
                logger.info(">>> 标注了这个注解的类的类型有很多吗 : " + tm.toString() + " <<<");

                //获取这个元素上标注的注解类
                Route route = element.getAnnotation(Route.class);
                //注: ARouter::Compiler >>> 获取这个元素上标注的注解类 : @com.alibaba.android.arouter.facade.annotation.Route(priority=-1, extras=-2147483648, name=, group=, path=/yourservicegroupname/hello) <<<
                logger.info(">>> 获取这个元素上标注的注解类 : " + route + " <<<");

                //封装路由信息的实体类
                RouteMeta routeMeta;

                // Activity or Fragment
                if (types.isSubtype(tm, type_Activity) || types.isSubtype(tm, fragmentTm) || types.isSubtype(tm, fragmentTmV4)) {
                    //页面中的参数：["name",TypeKind.STRING.ordinal()]
                    Map<String, Integer> paramsType = new HashMap<>();
                    //页面中的参数：["name",Autowired(desc=, required=true, name=boy)]
                    Map<String, Autowired> injectConfig = new HashMap<>();

                    //注入参数信息，运行时进行赋值操作 element is Activity or Fragment
                    //递归注入页面中标注了Autowired 注解的字段。缓存在map中
                    injectParamCollector(element, paramsType, injectConfig);

                    //注: ARouter::Compiler >>> paramsType is {ser=9, pac=10, ch=5, obj=11, fl=6, name=8, dou=7, boy=0, objList=11, map=11, age=3, height=3} <<<
                    logger.info(">>> paramsType is " + paramsType + " <<<");

                    if (types.isSubtype(tm, type_Activity)) {  //tm is  Activity
                        logger.info(">>> Found activity route: " + tm.toString() + " <<<");
                        //route: Route(priority=-1, extras=-2147483648, name=, group=, path=/yourservicegroupname/hello)
                        //element: com.alibaba.android.arouter.demo.module1.TestModuleActivity
                        //paramsType: is {ser=9, pac=10, ch=5, obj=11, fl=6, name=8, dou=7, boy=0, objList=11, map=11, age=3, height=3} <<<
                        routeMeta = new RouteMeta(route, element, RouteType.ACTIVITY, paramsType);
                    } else {//tm is  Fragment
                        logger.info(">>> Found fragment route: " + tm.toString() + " <<<");
                        routeMeta = new RouteMeta(route, element, RouteType.parse(FRAGMENT), paramsType);

                        // todo v4的为什么没有处理？？？  FRAGMENT_V4 = "android.support.v4.app.Fragment";
                    }
                    //将Fragment 和 Activity 中的参数的注解 Map<String, Autowired>  封装到路由bean 中
                    routeMeta.setInjectConfig(injectConfig);

                } else if (types.isSubtype(tm, iProvider)) {         // IProvider
                    logger.info(">>> Found provider route: " + tm.toString() + " <<<");
                    routeMeta = new RouteMeta(route, element, RouteType.PROVIDER, null);
                } else if (types.isSubtype(tm, type_Service)) {           // Service
                    logger.info(">>> Found service route: " + tm.toString() + " <<<");
                    routeMeta = new RouteMeta(route, element, RouteType.parse(SERVICE), null);
                } else {
                    throw new RuntimeException("The @Route is marked on unsupported class, look at [" + tm.toString() + "].");
                }


                //按组对路由数据进行排序
                categories(routeMeta);
            }






            /*
               @Override
                public void loadInto(Map<String, RouteMeta> providers) {}
             */
            MethodSpec.Builder loadIntoMethodOfProviderBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .addParameter(providerParamSpec);

            Map<String, List<RouteDoc>> docSource = new HashMap<>();


            // Start generate java source, structure is divided into upper and lower levels, used for demand initialization.
            //开始生成java源码，结构分为上下两层，用于需求初始化。
            for (Map.Entry<String, Set<RouteMeta>> entry : groupMap.entrySet()) { //遍历每个组以及每个组对应的列表
                String groupName = entry.getKey();

                /*
                 @Override
                public void loadInto(Map<String, RouteMeta> atlas) {}
                 */
                MethodSpec.Builder loadIntoMethodOfGroupBuilder = MethodSpec.methodBuilder(METHOD_LOAD_INTO)
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(groupParamSpec);

                List<RouteDoc> routeDocList = new ArrayList<>();

                // Build group method body
                Set<RouteMeta> groupData = entry.getValue(); //每个组收集到的路由集合

                for (RouteMeta routeMeta : groupData) {

                    //生成单个路由的文档信息
                    RouteDoc routeDoc = extractDocInfo(routeMeta);

                    //Element:  com.alibaba.android.arouter.demo.module1.TestModuleActivity <<<
                    ClassName className = ClassName.get((TypeElement) routeMeta.getRawType());

                    logger.info(">>> groupData: className is : " + className + " <<<");

                    // todo 追加收集到的 iProvider 路由信息
                    switch (routeMeta.getType()) {
                        case PROVIDER:  // Need cache provider's super class
                            List<? extends TypeMirror> interfaces = ((TypeElement) routeMeta.getRawType()).getInterfaces();
                            for (TypeMirror tm : interfaces) {

                                logger.info(">>> PROVIDER: tm: " + tm.toString() + " <<<");

                                routeDoc.addPrototype(tm.toString());

                                logger.info(">>> PROVIDER getPrototype: " + routeDoc.getPrototype() + " <<<");


                                //直接实现了 IProvider
                                //@Route(path = "/yourservicegroupname/single")
                                //public class SingleService implements IProvider {
                                if (types.isSameType(tm, iProvider)) {   // Its implements iProvider interface himself.
                                    // This interface extend the IProvider, so it can be used for mark provider

                                      /*
                                       @Override
                                        public void loadInto(Map<String, RouteMeta> providers) {}
                                     */

                                    // 添加语句
                                    // providers.put(
                                    // "com.alibaba.android.arouter.facade.service.AutowiredService",
                                    // RouteMeta.build(RouteType.PROVIDER,   //    PROVIDER(2, "com.alibaba.android.arouter.facade.template.IProvider"),
                                    // AutowiredServiceImpl.class,
                                    // "/arouter/service/autowired",
                                    // "arouter",
                                    // null,
                                    // -1,
                                    // -2147483648));

                                    logger.info(">>> PROVIDER: className 为什么不一样: " + className + " <<<");


                                    loadIntoMethodOfProviderBuilder.addStatement(
                                            "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                            (routeMeta.getRawType()).toString(), //"com.alibaba.android.arouter.facade.service.AutowiredService"
                                            routeMetaCn, //RouteMeta
                                            routeTypeCn, //RouteType.PROVIDER
                                            className,  //AutowiredServiceImpl.class
                                            routeMeta.getPath(), //"/arouter/service/autowired"
                                            routeMeta.getGroup()); //"arouter"

                                } else if (types.isSubtype(tm, iProvider)) {
                                    //间接实现了 IProvider
                                    //  @Route(path = "/arouter/service/autowired")
                                    //public class AutowiredServiceImpl implements AutowiredService {

                                    logger.info(">>> PROVIDER: className isSubtype 为什么不一样: " + className + " <<<");

                                    // This interface extend the IProvider, so it can be used for mark provider
                                    loadIntoMethodOfProviderBuilder.addStatement(
                                            "providers.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, null, " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                                            tm.toString(),    // So stupid, will duplicate only save class name.
                                            routeMetaCn,
                                            routeTypeCn,
                                            className,
                                            routeMeta.getPath(),
                                            routeMeta.getGroup());
                                }
                            }
                            break;
                        default:
                            break;
                    }

                    // todo 追加收集到的 Autowired 注解的参数信息

                    // Make map body for paramsType
                    StringBuilder mapBodyBuilder = new StringBuilder();
                    // intent 携带的参数类型键值对
                    Map<String, Integer> paramsType = routeMeta.getParamsType();
                    // intent 携带的参注解信息键值对
                    Map<String, Autowired> injectConfigs = routeMeta.getInjectConfig();
                    //
                    if (MapUtils.isNotEmpty(paramsType)) {
                        //[Param{key='ser', type='serializable', description='null', required=false},
                        List<RouteDoc.Param> paramList = new ArrayList<>();

                        for (Map.Entry<String, Integer> types : paramsType.entrySet()) {

                            mapBodyBuilder.append("put(\"").append(types.getKey()).append("\", ").append(types.getValue()).append("); ");

                            RouteDoc.Param param = new RouteDoc.Param();
                            Autowired injectConfig = injectConfigs.get(types.getKey());
                            param.setKey(types.getKey());
                            param.setType(TypeKind.values()[types.getValue()].name().toLowerCase());
                            param.setDescription(injectConfig.desc());
                            param.setRequired(injectConfig.required());

                            paramList.add(param);
                        }

                        //注: ARouter::Compiler >>> Autowired: paramList is :
                        // [Param{key='ser', type='serializable', description='null', required=false},
                        // Param{key='ch', type='char', description='null', required=false},
                        // Param{key='fl', type='float', description='null', required=false},
                        // Param{key='dou', type='double', description='null', required=false},
                        // Param{key='boy', type='boolean', description='null', required=true},
                        // Param{key='url', type='string', description='null', required=false},
                        // Param{key='pac', type='parcelable', description='null', required=false},
                        // Param{key='obj', type='object', description='null', required=false},
                        // Param{key='name', type='string', description='姓名', required=false},
                        // Param{key='objList', type='object', description='null', required=false},
                        // Param{key='map', type='object', description='null', required=false},
                        // Param{key='age', type='int', description='null', required=false},
                        // Param{key='height', type='int', description='null', required=false}] <<<
                        logger.info(">>> Autowired: paramList is : " + paramList + " <<<");

                        routeDoc.setParams(paramList);
                    }


                    //收集到的 参数和类型
                    String mapBody = mapBodyBuilder.toString();

                    //注: ARouter::Compiler >>> Autowired: mapBody is :
                    // put("ser", 9); put("ch", 5); put("fl", 6); put("dou", 7); put("boy", 0); put("url", 8); put("pac", 10); put("obj", 11); put("name", 8); put("objList", 11); put("map", 11); put("age", 3); put("height", 3);  <<<
                    logger.info(">>> Autowired: mapBody is : " + mapBody + " <<<");


                    //   追加语句： atlas.put(
                    //   "/kotlin/java",
                    //   RouteMeta.build(
                    //   RouteType.ACTIVITY,
                    //   TestNormalActivity.class,
                    //   "/kotlin/java",
                    //   "kotlin",
                    //   null,
                    //   -1,
                    //   -2147483648));

                    loadIntoMethodOfGroupBuilder.addStatement(
                            "atlas.put($S, $T.build($T." + routeMeta.getType() + ", $T.class, $S, $S, " + (StringUtils.isEmpty(mapBody) ? null : ("new java.util.HashMap<String, Integer>(){{" + mapBodyBuilder.toString() + "}}")) + ", " + routeMeta.getPriority() + ", " + routeMeta.getExtra() + "))",
                            routeMeta.getPath(),
                            routeMetaCn,
                            routeTypeCn,
                            className,
                            routeMeta.getPath().toLowerCase(),
                            routeMeta.getGroup().toLowerCase());

                    routeDoc.setClassName(className.toString());

                    routeDocList.add(routeDoc);
                }


                // Generate groups
                String groupFileName = NAME_OF_GROUP + groupName;
                //注: ARouter::Compiler >>> Generated group: arouter<<<
                //注: ARouter::Compiler >>> Generated NAME_OF_GROUP: ARouter$$Group$$<<<
                //注: ARouter::Compiler >>> Generated groupFileName: ARouter$$Group$$arouter<<<
                //注: ARouter::Compiler >>> Generated PACKAGE_OF_GENERATE_FILE: com.alibaba.android.arouter.routes<<<
                logger.info(">>> Generated group: " + groupName + "<<<");
                logger.info(">>> Generated NAME_OF_GROUP: " + NAME_OF_GROUP + "<<<");
                logger.info(">>> Generated groupFileName: " + groupFileName + "<<<");
                logger.info(">>> Generated PACKAGE_OF_GENERATE_FILE: " + PACKAGE_OF_GENERATE_FILE + "<<<");

                //生成Java 类
                // package com.alibaba.android.arouter.routes;
                // public class ARouter$$Group$$test implements IRouteGroup {
                JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                        TypeSpec.classBuilder(groupFileName)
                                .addJavadoc(WARNING_TIPS) //  "DO NOT EDIT THIS FILE!!! IT WAS GENERATED BY AROUTER.";
                                .addSuperinterface(ClassName.get(type_IRouteGroup)) // com.alibaba.android.arouter.facade.template.IRouteGroup
                                .addModifiers(PUBLIC)
                                .addMethod(loadIntoMethodOfGroupBuilder.build()) //    public void loadInto(Map<String, RouteMeta> atlas) {}
                                .build()
                ).build().writeTo(mFiler);


                rootMap.put(groupName, groupFileName);  //["arouter","ARouter$$Group$$arouter"]
                docSource.put(groupName, routeDocList);
            } //  end   for (Map.Entry<String, Set<RouteMeta>> entry : groupMap.entrySet()) {


            //todo     public void loadInto(Map<String, RouteMeta> atlas) {}
            if (MapUtils.isNotEmpty(rootMap)) {
                // Generate root meta by group name, it must be generated before root, then I can find out the class of group.
                //按组名生成root meta，必须在root之前生成，这样才能找出group的类。
                for (Map.Entry<String, String> entry : rootMap.entrySet()) {
                    //  routes.put("kotlin", ARouter$$Group$$kotlin.class);
                    loadIntoMethodOfRootBuilder.addStatement("routes.put($S, $T.class)", entry.getKey(), ClassName.get(PACKAGE_OF_GENERATE_FILE, entry.getValue()));
                }
            }


            //todo  arouter-map-of-modulejava.json 输出路由文档
            if (generateDoc) {
                docWriter.append(JSON.toJSONString(docSource, SerializerFeature.PrettyFormat));
                docWriter.flush();
                docWriter.close();
            }

            //todo  ARouter$$Providers$$arouterapi Write provider into disk
            String providerMapFileName = NAME_OF_PROVIDER + SEPARATOR + moduleName;
            //package com.alibaba.android.arouter.routes;
            //public class ARouter$$Providers$$modulejava implements IProviderGroup {}
            //public void loadInto(Map<String, RouteMeta> providers) {}
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(providerMapFileName)
                            .addJavadoc(WARNING_TIPS)
                            .addSuperinterface(ClassName.get(type_IProviderGroup)) //            // com.alibaba.android.arouter.facade.template.IInterceptor
                            .addModifiers(PUBLIC)
                            .addMethod(loadIntoMethodOfProviderBuilder.build())
                            .build()
            ).build().writeTo(mFiler);

            //注: ARouter::Compiler >>> Generated provider map, name is ARouter$$Providers$$arouterapi <<<
            logger.info(">>> Generated provider map, name is " + providerMapFileName + " <<<");

            // todo ARouter$$Root$$arouterapi  Write root meta into disk.
            String rootFileName = NAME_OF_ROOT + SEPARATOR + moduleName;
            JavaFile.builder(PACKAGE_OF_GENERATE_FILE,
                    TypeSpec.classBuilder(rootFileName)
                            .addJavadoc(WARNING_TIPS)
                            .addSuperinterface(ClassName.get(elementUtils.getTypeElement(ITROUTE_ROOT)))
                            .addModifiers(PUBLIC)
                            .addMethod(loadIntoMethodOfRootBuilder.build())
                            .build()
            ).build().writeTo(mFiler);

            //注: ARouter::Compiler >>> Generated root, name is ARouter$$Root$$arouterapi <<<
            logger.info(">>> Generated root, name is " + rootFileName + " <<<");
        }
    }


    /**
     * Recursive inject config collector.
     * 递归注入页面中标注了Autowired 注解的字段。缓存在map中
     * 缓存的数据如下
     * Map<String, Integer> ： ["name",TypeKind.STRING.ordinal()]
     * Map<String, Autowired> injectConfig)： ["name",Autowired(desc=, required=true, name=boy)]
     *
     * @param element current element.  element is Activity or Fragment
     */
    private void injectParamCollector(Element element,
                                      Map<String, Integer> paramsType,
                                      Map<String, Autowired> injectConfig) {

        for (Element field : element.getEnclosedElements()) { //返回被这个元素直接包围的元素。
            //注: ARouter::Compiler >>> injectParamCollector, field is onCreateView <<<
            //注: ARouter::Compiler >>> injectParamCollector, field is <init> <<<
            //注: ARouter::Compiler >>> injectParamCollector, field is onCreate <<<
            //注: ARouter::Compiler >>> injectParamCollector, field is name <<<

            logger.info(">>> injectParamCollector, field is " + field.getSimpleName() + " <<<");

            if (field.getKind().isField() // 成员属性
                    && field.getAnnotation(Autowired.class) != null // 标注了Autowired注解
                    && !types.isSubtype(field.asType(), iProvider)) { //测试一种类型是否是另一种类型的子类型。任何类型都被认为是其自身的子类型。

                // It must be field, then it has annotation, but it not be provider.
                Autowired paramConfig = field.getAnnotation(Autowired.class);
                //注: ARouter::Compiler >>> paramConfig,  is @com.alibaba.android.arouter.facade.annotation.Autowired(desc=, required=false, name=) <<<
                //注: ARouter::Compiler >>> paramConfig,  is @com.alibaba.android.arouter.facade.annotation.Autowired(desc=, required=true, name=boy) <<<

                logger.info(">>> paramConfig,  is " + paramConfig + " <<<");
                String injectName = StringUtils.isEmpty(paramConfig.name()) ? field.getSimpleName().toString() : paramConfig.name();
                //注: ARouter::Compiler >>> injectName, name is name <<<
                logger.info(">>> injectName, name is " + injectName + " <<<");
                Integer fieldType = typeUtils.typeExchange(field); // return TypeKind.STRING.ordinal(); 返回此枚举常量的序数（它在枚举声明中的位置，其中初始常量被分配零序数）。
                //注: ARouter::Compiler >>> fieldType, type is 8 <<<
                logger.info(">>> fieldType, type is " + fieldType + " <<<");

                paramsType.put(injectName, fieldType); // ["name",8]
                injectConfig.put(injectName, paramConfig);// ["name",Autowired(desc=, required=true, name=boy)]
            }
        }

        // if has parent?
        TypeMirror parent = ((TypeElement) element).getSuperclass();
        //注: ARouter::Compiler >>> TypeElement, parent is android.app.Activity <<<
        //注: ARouter::Compiler >>> TypeElement, parent is android.support.v4.app.Fragment <<<
        logger.info(">>> TypeElement, parent is " + parent + " <<<");

        if (parent instanceof DeclaredType) { //表示声明的类型，类类型或接口类型。这包括参数化类型，例如 {@code java.util.Set<String>} 以及原始类型。
            Element parentElement = ((DeclaredType) parent).asElement();
            //注: ARouter::Compiler >>> parentElement, is android.support.v4.app.Fragment <<<
            logger.info(">>> parentElement, is " + parentElement + " <<<");

            if (parentElement instanceof TypeElement) {
                String getQualifiedName = ((TypeElement) parentElement).getQualifiedName().toString();
                //注: ARouter::Compiler >>> getQualifiedName, is android.support.v4.app.Fragment <<<
                logger.info(">>> getQualifiedName, is " + getQualifiedName + " <<<");
            }
            if (parentElement instanceof TypeElement
                    && !((TypeElement) parentElement).getQualifiedName().toString().startsWith("android")) {
                logger.info(">>> instanceof, is " + parentElement + " <<<");
                injectParamCollector(parentElement, paramsType, injectConfig);
            }
        }
    }

    /**
     * Extra doc info from route meta
     * 来自路由的额外文档信息
     *
     * @param routeMeta meta
     * @return doc
     */
    private RouteDoc extractDocInfo(RouteMeta routeMeta) {
        RouteDoc routeDoc = new RouteDoc();
        routeDoc.setGroup(routeMeta.getGroup());
        routeDoc.setPath(routeMeta.getPath());
        routeDoc.setDescription(routeMeta.getName());
        routeDoc.setType(routeMeta.getType().name().toLowerCase()); //四大组件 IProvider等
        routeDoc.setMark(routeMeta.getExtra()); //附加数据，表示状态

        return routeDoc;
    }

    /**
     * Sort metas in group.
     * 按组对路由数据进行排序。
     *
     * @param routeMete metas.
     */
    private void categories(RouteMeta routeMete) {
        // 校验路由 path 设置group缺省值为 path 的 第一个/ 之前的值
        if (routeVerify(routeMete)) {
            //注: ARouter::Compiler >>> 开始按组对路由数据进行排序, group = module, path = /module/1 <<<
            logger.info(">>> 开始按组对路由数据进行排序, group = " + routeMete.getGroup() + ", path = " + routeMete.getPath() + " <<<");
            // groupMap: 类型 Map<String, TreeSet<RouteMeta>>
            Set<RouteMeta> routeMetas = groupMap.get(routeMete.getGroup()); //先从缓存中取


            //{test=[RouteMeta{type=ACTIVITY, rawType=com.alibaba.android.arouter.demo.module1.testactivity.Test1Activity, destination=null, path='/test/activity1', group='test', priority=-1, extra=-2147483648, paramsType={ser=9, ch=5, fl=6, dou=7, boy=0, url=8, pac=10, obj=11, name=8, objList=11, map=11, age=3, height=3}, name='测试用 Activity'},
            // RouteMeta{type=ACTIVITY, rawType=com.alibaba.android.arouter.demo.module1.testactivity.Test2Activity, destination=null, path='/test/activity2', group='test', priority=-1, extra=-2147483648, paramsType={key1=8}, name=''},
            // RouteMeta{type=ACTIVITY, rawType=com.alibaba.android.arouter.demo.module1.testactivity.Test3Activity, destination=null, path='/test/activity3', group='test', priority=-1, extra=-2147483648, paramsType={name=8, boy=0, age=3}, name=''},
            // RouteMeta{type=ACTIVITY, rawType=com.alibaba.android.arouter.demo.module1.testactivity.Test4Activity, destination=null, path='/test/activity4', group='test', priority=-1, extra=-2147483648, paramsType={}, name=''},
            // RouteMeta{type=FRAGMENT, rawType=com.alibaba.android.arouter.demo.module1.BlankFragment, destination=null, path='/test/fragment', group='test', priority=-1, extra=-2147483648, paramsType={ser=9, pac=10, ch=5, obj=11, fl=6, name=8, dou=7, boy=0, objList=11, map=11, age=3, height=3}, name=''}],

            // m2=[RouteMeta{type=ACTIVITY, rawType=com.alibaba.android.arouter.demo.module1.TestModule2Activity, destination=null, path='/module/2', group='m2', priority=-1, extra=-2147483648, paramsType={}, name=''}],

            // yourservicegroupname=[RouteMeta{type=PROVIDER, rawType=com.alibaba.android.arouter.demo.module1.testservice.HelloServiceImpl, destination=null, path='/yourservicegroupname/hello', group='yourservicegroupname', priority=-1, extra=-2147483648, paramsType=null, name=''},
            // RouteMeta{type=PROVIDER, rawType=com.alibaba.android.arouter.demo.module1.testservice.JsonServiceImpl, destination=null, path='/yourservicegroupname/json', group='yourservicegroupname', priority=-1, extra=-2147483648, paramsType=null, name=''},
            // RouteMeta{type=PROVIDER, rawType=com.alibaba.android.arouter.demo.module1.testservice.SingleService, destination=null, path='/yourservicegroupname/single', group='yourservicegroupname', priority=-1, extra=-2147483648, paramsType=null, name=''}],

            // module=[RouteMeta{type=ACTIVITY, rawType=com.alibaba.android.arouter.demo.module1.TestModuleActivity, destination=null, path='/module/1', group='module', priority=-1, extra=-2147483648, paramsType={}, name=''}]} <<<


            logger.info(">>> groupMap  cached is = " + groupMap + " <<<");
            //注: ARouter::Compiler >>> routeMetas  cached is = [RouteMeta{type=PROVIDER, rawType=com.alibaba.android.arouter.demo.module1.testservice.SingleService, destination=null, path='/yourservicegroupname/single', group='yourservicegroupname', priority=-1, extra=-2147483648, paramsType=null, name=''}] <<<
            logger.info(">>> routeMetas  cached is = " + routeMetas + " <<<");

            if (CollectionUtils.isEmpty(routeMetas)) { // 缓存中还没有这个组 新加一个KV
                Set<RouteMeta> routeMetaSet = new TreeSet<>(new Comparator<RouteMeta>() {
                    @Override
                    public int compare(RouteMeta r1, RouteMeta r2) {
                        try {
                            //按照字符串的自然顺序排序
                            return r1.getPath().compareTo(r2.getPath());
                        } catch (NullPointerException npe) {
                            logger.error(npe.getMessage());
                            return 0;
                        }
                    }
                });
                routeMetaSet.add(routeMete);
                groupMap.put(routeMete.getGroup(), routeMetaSet);
            } else {
                routeMetas.add(routeMete);
            }

        } else {
            logger.warning(">>> Route meta verify error, group is " + routeMete.getGroup() + " <<<");
        }
    }

    /**
     * Verify the route meta
     *
     * @param meta raw meta
     */
    private boolean routeVerify(RouteMeta meta) {
        String path = meta.getPath();

        if (StringUtils.isEmpty(path) || !path.startsWith("/")) {
            // The path must be start with '/' and not empty!
            return false;
        }

        if (StringUtils.isEmpty(meta.getGroup())) {
            // Use default group(the first word in path)
            try {
                String defaultGroup = path.substring(1, path.indexOf("/", 1));
                if (StringUtils.isEmpty(defaultGroup)) {
                    return false;
                }

                meta.setGroup(defaultGroup);
                return true;
            } catch (Exception e) {
                logger.error("Failed to extract default group! " + e.getMessage());
                return false;
            }
        }

        return true;
    }
}
