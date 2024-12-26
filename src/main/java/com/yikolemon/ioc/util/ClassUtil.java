package com.yikolemon.ioc.util;

import com.yikolemon.ioc.annotation.Component;
import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
public class ClassUtil {


    /**
     * 递归查询目标注解
     * @param target    查询类class
     * @param annoClazz 注解class
     * @return          查询道德注解
     * @param <T>       注解泛型
     */
    public static <T extends Annotation> T getAnnotation(Class<?> target, Class<T> annoClazz){
        T anno = target.getAnnotation(annoClazz);
        Annotation[] annotations = target.getAnnotations();
        for (Annotation a : annotations) {
            Class<? extends Annotation> itemAnnoType = a.annotationType();
            String itemAnnoPackageName = itemAnnoType.getPackage().getName();
            //忽略jdk内部注解
            if (itemAnnoPackageName.startsWith("java")){
                continue;
            }
            //非jdk注解进行
            T itemFound = getAnnotation(itemAnnoType, annoClazz);
            if (itemFound != null && anno != null){
                throw new RuntimeException("duplicate anno");
            }
            anno = itemFound != null ? itemFound : anno;
        }
        return anno;
    }

    /**
     *  /TODO 忽略嵌套@Component的情况
     * @param clazz 目标类
     * @return bean名称
     */
    public static String getBeanName(Class<?> clazz) {
        Component anno = clazz.getAnnotation(Component.class);
        String name;
        if (anno == null || StringUtils.isEmpty(anno.value())){
            String simpleName = clazz.getSimpleName();
            name = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        }else{
            name = anno.value();
        }
        return name;
    }

    public static Constructor<?> getSuitbaleConstructor(Class<?> clazz){
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0){
            cons = clazz.getConstructors();
            if (cons.length != 1){
                throw new RuntimeException("more than one constructor found in class" + clazz.getName());
            }
        }
        if (cons.length != 1){
            throw new RuntimeException("more than one public constructor found in class" + clazz.getName());
        }
        return cons[0];
    }
}
