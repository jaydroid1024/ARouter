package com.alibaba.android.arouter.compiler.processor;

import com.alibaba.android.arouter.compiler.utils.Consts;
import com.alibaba.android.arouter.facade.annotation.Autowired;
import com.alibaba.android.arouter.facade.enums.TypeKind;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import static com.alibaba.android.arouter.compiler.utils.Consts.ANNOTATION_TYPE_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.ISYRINGE;
import static com.alibaba.android.arouter.compiler.utils.Consts.JSON_SERVICE;
import static com.alibaba.android.arouter.compiler.utils.Consts.METHOD_INJECT;
import static com.alibaba.android.arouter.compiler.utils.Consts.NAME_OF_AUTOWIRED;
import static com.alibaba.android.arouter.compiler.utils.Consts.TYPE_WRAPPER;
import static com.alibaba.android.arouter.compiler.utils.Consts.WARNING_TIPS;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Processor used to create autowired helper
 *
 * @author zhilong <a href="mailto:zhilong.lzl@alibaba-inc.com">Contact me.</a>
 * @version 1.0
 * @since 2017/2/20 下午5:56
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({ANNOTATION_TYPE_AUTOWIRED})
public class AutowiredProcessor extends BaseProcessor {

    private Map<TypeElement, List<Element>> parentAndChild = new HashMap<>();   // Contain field need autowired and his super class.
    private static final ClassName ARouterClass = ClassName.get("com.alibaba.android.arouter.launcher", "ARouter");
    private static final ClassName AndroidLog = ClassName.get("android.util", "Log");

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        logger.info(">>> AutowiredProcessor init. <<<");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (CollectionUtils.isNotEmpty(set)) {
            try {
                logger.info(">>> Found autowired field, start... <<<");
                //按照页面进行分组 Map<TypeElement, List<Element>>
                categories(roundEnvironment.getElementsAnnotatedWith(Autowired.class));

                generateHelper();

            } catch (Exception e) {
                logger.error(e);
            }
            return true;
        }

        return false;
    }

    private void generateHelper() throws IOException, IllegalAccessException {
        TypeElement type_ISyringe = elementUtils.getTypeElement(ISYRINGE);
        TypeElement type_JsonService = elementUtils.getTypeElement(JSON_SERVICE);
        TypeMirror iProvider = elementUtils.getTypeElement(Consts.IPROVIDER).asType();
        TypeMirror activityTm = elementUtils.getTypeElement(Consts.ACTIVITY).asType();
        TypeMirror fragmentTm = elementUtils.getTypeElement(Consts.FRAGMENT).asType();
        TypeMirror fragmentTmV4 = elementUtils.getTypeElement(Consts.FRAGMENT_V4).asType();

        //注: ARouter::Compiler >>> generateHelper type_ISyringe is com.alibaba.android.arouter.facade.template.ISyringe. <<<
        //注: ARouter::Compiler >>> generateHelper type_JsonService is com.alibaba.android.arouter.facade.service.SerializationService. <<<
        //注: ARouter::Compiler >>> generateHelper iProvider is com.alibaba.android.arouter.facade.template.IProvider. <<<
        //注: ARouter::Compiler >>> generateHelper fragmentTm is android.app.Fragment. <<<
        //注: ARouter::Compiler >>> generateHelper fragmentTmV4 is android.support.v4.app.Fragment. <<<
        logger.info(">>> generateHelper type_ISyringe is " + type_ISyringe + ". <<<");
        logger.info(">>> generateHelper type_JsonService is " + type_JsonService + ". <<<");
        logger.info(">>> generateHelper iProvider is " + iProvider + ". <<<");
        logger.info(">>> generateHelper fragmentTm is " + fragmentTm + ". <<<");
        logger.info(">>> generateHelper fragmentTmV4 is " + fragmentTmV4 + ". <<<");


        // Build input param name.
        ParameterSpec objectParamSpec = ParameterSpec.builder(TypeName.OBJECT, "target").build();

        if (MapUtils.isNotEmpty(parentAndChild)) {
            for (Map.Entry<TypeElement, List<Element>> entry : parentAndChild.entrySet()) {
                // Build method : 'inject'
                //   @Override
                //  public void inject(Object target) {}
                MethodSpec.Builder injectMethodBuilder = MethodSpec.methodBuilder(METHOD_INJECT)
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(objectParamSpec);

                TypeElement parent = entry.getKey(); //
                List<Element> childs = entry.getValue(); //
                logger.info(">>> generateHelper parent is " + parent + ". <<<");
                logger.info(">>> generateHelper childs is " + childs + ". <<<");


                String qualifiedName = parent.getQualifiedName().toString();
                String packageName = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
                String fileName = parent.getSimpleName() + NAME_OF_AUTOWIRED;

                logger.info(">>> generateHelper qualifiedName is " + qualifiedName + ". <<<");
                logger.info(">>> generateHelper packageName is " + packageName + ". <<<");
                logger.info(">>> generateHelper fileName is " + fileName + ". <<<");

                logger.info(">>> Start process " + childs.size() + " field in " + parent.getSimpleName() + " ... <<<");

                //public class Test1Activity$$ARouter$$Autowired implements ISyringe {}
                TypeSpec.Builder helper = TypeSpec.classBuilder(fileName)
                        .addJavadoc(WARNING_TIPS)
                        .addSuperinterface(ClassName.get(type_ISyringe))
                        .addModifiers(PUBLIC);

                //  private SerializationService serializationService;
                FieldSpec jsonServiceField = FieldSpec.builder(TypeName.get(type_JsonService.asType()), "serializationService", Modifier.PRIVATE).build();
                helper.addField(jsonServiceField);


                //    serializationService = ARouter.getInstance().navigation(SerializationService.class);
                injectMethodBuilder.addStatement("serializationService = $T.getInstance().navigation($T.class)", ARouterClass, ClassName.get(type_JsonService));
                //     Test1Activity substitute = (Test1Activity)target;
                injectMethodBuilder.addStatement("$T substitute = ($T)target", ClassName.get(parent), ClassName.get(parent));


                //  substitute.age = substitute.getIntent().getIntExtra("age", substitute.age);
                //    substitute.height = substitute.getIntent().getIntExtra("height", substitute.height);
                //    substitute.girl = substitute.getIntent().getBooleanExtra("boy", substitute.girl);
                //    substitute.ch = substitute.getIntent().getCharExtra("ch", substitute.ch);
                //    substitute.fl = substitute.getIntent().getFloatExtra("fl", substitute.fl);
                //    substitute.dou = substitute.getIntent().getDoubleExtra("dou", substitute.dou);
                //    substitute.ser = (com.alibaba.android.arouter.demo.service.model.TestSerializable) substitute.getIntent().getSerializableExtra("ser");
                //    substitute.pac = substitute.getIntent().getParcelableExtra("pac");

                // Generate method body, start inject.
                for (Element element : childs) {
                    Autowired fieldConfig = element.getAnnotation(Autowired.class);
                    String fieldName = element.getSimpleName().toString();
                    //
                    if (types.isSubtype(element.asType(), iProvider)) {  // It's provider
                        if ("".equals(fieldConfig.name())) {    // User has not set service path, then use byType.

                            //    substitute.helloService = ARouter.getInstance().navigation(HelloService.class);
                            // Getter
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = $T.getInstance().navigation($T.class)",
                                    ARouterClass,
                                    ClassName.get(element.asType())
                            );
                        } else {    // use byName
                            //    substitute.helloService222 = (HelloService)ARouter.getInstance().build("yourservicegroupname/hello").navigation();
                            // Getter
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = ($T)$T.getInstance().build($S).navigation()",
                                    ClassName.get(element.asType()),
                                    ARouterClass,
                                    fieldConfig.name()
                            );
                        }

                        //  if (substitute.helloService333 == null) {
                        //      throw new RuntimeException("The field 'helloService333' is null, in class '" + Test1Activity.class.getName() + "!");
                        //    }
                        // Validator
                        if (fieldConfig.required()) {
                            injectMethodBuilder.beginControlFlow("if (substitute." + fieldName + " == null)");
                            injectMethodBuilder.addStatement(
                                    "throw new RuntimeException(\"The field '" + fieldName + "' is null, in class '\" + $T.class.getName() + \"!\")", ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        }

                    } else {    // It's normal intent value
                        String originalValue = "substitute." + fieldName;
                        //注: ARouter::Compiler >>> It's normal intent value statement is substitute.ser = (com.alibaba.android.arouter.demo.service.model.TestSerializable) substitute.. <<<
                        String statement = "substitute." + fieldName + " = " + buildCastCode(element) + "substitute.";

                        //注: ARouter::Compiler >>> It's normal intent value originalValue is substitute.name <<<
                        //注: ARouter::Compiler >>> It's normal intent value statement is substitute.name = substitute. <<<
                        logger.info(">>> It's normal intent value originalValue is " + originalValue + "<<<");
                        logger.info(">>> It's normal intent value statement is " + statement + " <<<");

                        boolean isActivity = false;
                        if (types.isSubtype(parent.asType(), activityTm)) {  // Activity, then use getIntent()
                            isActivity = true;
                            statement += "getIntent().";
                        } else if (types.isSubtype(parent.asType(), fragmentTm) || types.isSubtype(parent.asType(), fragmentTmV4)) {   // Fragment, then use getArguments()
                            statement += "getArguments().";
                        } else {
                            throw new IllegalAccessException("The field [" + fieldName + "] need autowired from intent, its parent must be activity or fragment!");
                        }

                        //注: ARouter::Compiler >>> It's normal intent value buildStatement is serializationService.parseObject(substitute.getArguments().getString($S), new com.alibaba.android.arouter.facade.model.TypeWrapper<$T>(){}.getType()) <<<
                        //注: ARouter::Compiler >>> It's normal intent value buildStatement is substitute.age = substitute.getArguments().getInt($S, substitute.age) <<<
                        //注: ARouter::Compiler >>> It's normal intent value buildStatement is substitute.girl = substitute.getArguments().getBoolean($S, substitute.girl) <<<
                        //注: ARouter::Compiler >>> It's normal intent value buildStatement is substitute.ser = (com.alibaba.android.arouter.demo.service.model.TestSerializable) substitute.getArguments().getSerializable($S) <<<
                        //注: ARouter::Compiler >>> It's normal intent value buildStatement is substitute.name = substitute.getIntent().getExtras() == null ? substitute.name : substitute.getIntent().getExtras().getString($S, substitute.name) <<<

                        //注: ARouter::Compiler >>> It's normal intent value buildStatement before is substitute.age = substitute.getIntent(). <<<
                        //注: ARouter::Compiler >>> It's normal intent value buildStatement after is substitute.age = substitute.getIntent().getIntExtra($S, substitute.age) <<<
                        logger.info(">>> It's normal intent value buildStatement before is " + statement + " <<<");

                        //todo 根据类型拼接获取参数的语句
                        statement = buildStatement(originalValue, statement, typeUtils.typeExchange(element), isActivity, isKtClass(parent));

                        logger.info(">>> It's normal intent value buildStatement after is " + statement + " <<<");

                        //   if (null != serializationService) {
                        //      substitute.obj = serializationService.parseObject(substitute.getIntent().getStringExtra("obj"), new com.alibaba.android.arouter.facade.model.TypeWrapper<TestObj>(){}.getType());
                        //    } else {
                        //      Log.e("ARouter::", "You want automatic inject the field 'obj' in class 'Test1Activity' , then you should implement 'SerializationService' to support object auto inject!");
                        //    }

                        if (statement.startsWith("serializationService.")) {   // Not mortals
                            injectMethodBuilder.beginControlFlow("if (null != serializationService)");
                            injectMethodBuilder.addStatement(
                                    "substitute." + fieldName + " = " + statement,
                                    (StringUtils.isEmpty(fieldConfig.name()) ? fieldName : fieldConfig.name()),
                                    ClassName.get(element.asType())
                            );
                            injectMethodBuilder.nextControlFlow("else");
                            injectMethodBuilder.addStatement(
                                    "$T.e(\"" + Consts.TAG + "\", \"You want automatic inject the field '" + fieldName + "' in class '$T' , then you should implement 'SerializationService' to support object auto inject!\")", AndroidLog, ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        } else {
                            //    substitute.age = substitute.getIntent().getIntExtra("age", substitute.age);
                            injectMethodBuilder.addStatement(statement, StringUtils.isEmpty(fieldConfig.name()) ? fieldName : fieldConfig.name());
                        }

                        //  substitute.url = substitute.getIntent().getExtras() == null ? substitute.url : substitute.getIntent().getExtras().getString("url", substitute.url);
                        //    if (null == substitute.url) {
                        //      Log.e("ARouter::", "The field 'url' is null, in class '" + Test1Activity.class.getName() + "!");
                        //    }
                        // Validator
                        if (fieldConfig.required() && !element.asType().getKind().isPrimitive()) {  // Primitive wont be check.
                            injectMethodBuilder.beginControlFlow("if (null == substitute." + fieldName + ")");
                            injectMethodBuilder.addStatement(
                                    "$T.e(\"" + Consts.TAG + "\", \"The field '" + fieldName + "' is null, in class '\" + $T.class.getName() + \"!\")", AndroidLog, ClassName.get(parent));
                            injectMethodBuilder.endControlFlow();
                        }
                    }
                }

                helper.addMethod(injectMethodBuilder.build());

                // Generate autowire helper
                //package com.alibaba.android.arouter.demo.module1.testactivity;
                JavaFile.builder(packageName, helper.build()).build().writeTo(mFiler);

                logger.info(">>> " + parent.getSimpleName() + " has been processed, " + fileName + " has been generated. <<<");
            }

            logger.info(">>> Autowired processor stop. <<<");
        }
    }

    private boolean isKtClass(Element element) {
        for (AnnotationMirror annotationMirror : elementUtils.getAllAnnotationMirrors(element)) {
            if (annotationMirror.getAnnotationType().toString().contains("kotlin")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加强转语句
     *
     * @param element
     * @return
     */
    private String buildCastCode(Element element) {
        if (typeUtils.typeExchange(element) == TypeKind.SERIALIZABLE.ordinal()) {
            return CodeBlock.builder().add("($T) ", ClassName.get(element.asType())).build().toString();
        }
        return "";
    }

    /**
     * Build param inject statement
     */
    private String buildStatement(String originalValue, String statement, int type, boolean isActivity, boolean isKt) {
        switch (TypeKind.values()[type]) {
            case BOOLEAN:
                statement += "getBoolean" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case BYTE:
                statement += "getByte" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case SHORT:
                statement += "getShort" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case INT:
                statement += "getInt" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case LONG:
                statement += "getLong" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case CHAR:
                statement += "getChar" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case FLOAT:
                statement += "getFloat" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case DOUBLE:
                statement += "getDouble" + (isActivity ? "Extra" : "") + "($S, " + originalValue + ")";
                break;
            case STRING:
                statement += (isActivity ? ("getExtras() == null ? " + originalValue + " : substitute.getIntent().getExtras().getString($S") : ("getString($S")) + ", " + originalValue + ")";
                break;
            case SERIALIZABLE:
                statement += (isActivity ? ("getSerializableExtra($S)") : ("getSerializable($S)"));
                break;
            case PARCELABLE:
                statement += (isActivity ? ("getParcelableExtra($S)") : ("getParcelable($S)"));
                break;
            case OBJECT:
                statement = "serializationService.parseObject(substitute." + (isActivity ? "getIntent()." : "getArguments().") + (isActivity ? "getStringExtra($S)" : "getString($S)") + ", new " + TYPE_WRAPPER + "<$T>(){}.getType())";
                break;
        }

        return statement;
    }

    /**
     * Categories field, find his papa.
     * 按照页面进行分组 Map<TypeElement, List<Element>>
     *
     * @param elements Field need autowired
     */
    private void categories(Set<? extends Element> elements) throws IllegalAccessException {
        if (CollectionUtils.isNotEmpty(elements)) {
            for (Element element : elements) {

                //注: ARouter::Compiler >>> Autowired element is age. <<<
                logger.info(">>> Autowired element is " + element + ". <<<");

                TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
                //注: ARouter::Compiler >>> Autowired enclosingElement is com.alibaba.android.arouter.demo.module1.BlankFragment. <<<
                logger.info(">>> Autowired enclosingElement is " + enclosingElement + ". <<<");
                //注: ARouter::Compiler >>> Autowired enclosingElement.getQualifiedName is com.alibaba.android.arouter.demo.module1.BlankFragment. <<<
                logger.info(">>> Autowired enclosingElement.getQualifiedName is " + enclosingElement.getQualifiedName() + ". <<<");
                Name simpleName = element.getSimpleName();
                //注: ARouter::Compiler >>> Autowired simpleName is age. <<<
                logger.info(">>> Autowired simpleName is " + simpleName + ". <<<");


                if (element.getModifiers().contains(Modifier.PRIVATE)) {
                    throw new IllegalAccessException("The inject fields CAN NOT BE 'private'!!! please check field ["
                            + element.getSimpleName() + "] in class [" + enclosingElement.getQualifiedName() + "]");
                }

                //TypeElement： com.alibaba.android.arouter.demo.module1.BlankFragment
                //按照页面进行分组 Map<TypeElement, List<Element>>
                if (parentAndChild.containsKey(enclosingElement)) { // Has categries
                    parentAndChild.get(enclosingElement).add(element);
                } else {
                    List<Element> childs = new ArrayList<>();
                    childs.add(element);
                    parentAndChild.put(enclosingElement, childs);
                }
            }

            logger.info("categories finished.");
        }
    }
}
