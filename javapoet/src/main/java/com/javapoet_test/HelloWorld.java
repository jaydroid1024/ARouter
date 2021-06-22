package com.javapoet_test;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.File;
import java.io.IOException;

import javax.lang.model.element.Modifier;

/**
 * Javapoet 测试代码生成
 *
 * @author jaydroid
 * @version 1.0
 * @date 6/7/21
 */
public final class HelloWorld {

    private static final String android = "Lollipop v." + 5.0;

    public static void main(String[] args) {

        System.out.println("Hello, JavaPoet!, version is " + android);
        generateByJavaPoet();
    }

    private static void generateByJavaPoet() {
        ParameterSpec parameterSpec = ParameterSpec.builder(String[].class, "args")
                .build();

        MethodSpec main = MethodSpec.methodBuilder("main")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(void.class)
                .addParameter(parameterSpec)
                .addStatement("$T.out.println($S" + "+ $N)", System.class, "Hello JavaPoet version is ", "android")
                .build();

        FieldSpec fieldSpec = FieldSpec.builder(String.class, "android")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer("$S + $L", "Lollipop v.", 5.0d)
                .build();

        TypeSpec typeSpec = TypeSpec.classBuilder("HelloWorld2")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(main)
                .addJavadoc("<p>Use {@link ($T)} to generate  this class\n"
                        + "hahahaha.\n", HelloWorld.class)
                .addField(fieldSpec)
                .build();

        JavaFile javaFile = JavaFile.builder("com.javapoet_test", typeSpec)
                .build();

        String file = javaFile.toString();
        System.out.println(file);

        File file1 = new File("/Users/xuejiewang/AndroidStudioProjects/Jay/ARouter/javapoet/src/main/java/");
        if (!file1.exists()) {
            file1.mkdir();
        } else {
            file1.delete();
        }
        try {
            javaFile.writeTo(file1);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
}
