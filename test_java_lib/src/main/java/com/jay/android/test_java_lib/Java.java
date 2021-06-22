package com.jay.android.test_java_lib;

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/7/21
 */
class Java {
    public static void main(String[] args) {
        String moduleName = "000_33D-ddd";
        moduleName = moduleName.replaceAll("[^0-9a-zA-Z_]+", "++");
        System.out.println(moduleName);

    }
}
