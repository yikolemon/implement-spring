package com.yikolemon.ioc.util;

import com.yikolemon.ioc.annotation.Bean;
import com.yikolemon.ioc.annotation.Component;
import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
public class ClassUtil {

    public static Method findAnnoMethod(Class<?> target, Class<? extends Annotation> annoClazz){
        Method[] methods = target.getMethods();
        List<Method> annoMethods = Arrays.stream(methods)
                .filter(method -> method.isAnnotationPresent(annoClazz))
                .peek(method -> {
                    if (method.getParameterCount() > 0){
                        throw new RuntimeException("multiple parameters exist in method");
                    }
                })
                .collect(Collectors.toList());

        if (annoMethods.isEmpty()){
            return null;
        }else if (annoMethods.size() >= 2){
            throw new RuntimeException("Multiple methods exist");
        }else{
            return annoMethods.get(0);
        }
    }


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

    public static <T extends Annotation> T getAnnotation(Parameter parameter, Class<T> annoClazz){
        Annotation[] annotations = parameter.getAnnotations();
        for (Annotation anno : annotations) {
            if (annoClazz.isInstance(anno)){
                return (T)anno;
            }
        }
        return null;
    }

    /**
     *  /TODO 忽略嵌套@Component的情况
     * @param clazz 目标类
     * @return bean名称
     */
    public static String getBeanName(Class<?> clazz){
        Component anno = clazz.getAnnotation(Component.class);
        String name = null;
        if (anno != null){
            if (!StringUtils.isEmpty(anno.value())){
                return anno.value();
            }
        }else{
            for (Annotation itemAnno : clazz.getAnnotations()){
                if (getAnnotation(itemAnno.annotationType(), Component.class) != null){
                    try {
                        name = (String) itemAnno.annotationType().getMethod("value")
                                .invoke(itemAnno);
                    } catch (Exception e) {
                        throw new RuntimeException("cannot get @Component annotation value");
                    }
                }
            }
        }
        if (StringUtils.isEmpty(name)){
            String simpleName = clazz.getSimpleName();
            name = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
        }
        return name;
    }

    public static String getBeanName(Method method){
        Bean beanAnno = method.getAnnotation(Bean.class);
        if (beanAnno == null || StringUtils.isEmpty(beanAnno.value())){
            return Character.toLowerCase(method.getName().charAt(0)) + method.getName().substring(1);
        }else{
            return beanAnno.value();
        }
    }

    public static Constructor<?> getSuitbaleConstructor(Class<?> clazz){
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0){
            cons = clazz.getDeclaredConstructors();
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
