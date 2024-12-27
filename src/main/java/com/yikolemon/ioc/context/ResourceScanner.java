package com.yikolemon.ioc.context;

import com.yikolemon.ioc.annotation.*;
import com.yikolemon.ioc.resource.ResourceResolver;
import com.yikolemon.ioc.util.ClassUtil;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
public class ResourceScanner {

    private static final ResourceResolver RESOURCE_RESOLVER = new ResourceResolver("com.yikolemon");

    /**
     *
     * @param configClazz bean配置类
     * @return bean name列表
     */
    public Set<String> scanForClazzName(Class<?> configClazz){
        Set<String> componentNameSet = componentScanForName(configClazz);
        Set<String> importNameSet = importScanForName(configClazz);
        componentNameSet.addAll(importNameSet);
        return componentNameSet;
    }

    private Set<String> componentScanForName(Class<?> configClazz){
        List<String> packageList = getScanPackage(configClazz);
        HashSet<String> beanNameSet = new HashSet<>();
        for (String pkg : packageList) {
            ResourceResolver resourceResolver = new ResourceResolver(pkg);
            List<String> beanNameList = resourceResolver.scan(resource -> {
                String name = resource.getName();
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6)
                            .replace("/", ".")
                            .replace("\\", ".");
                }
                return null;
            });
            beanNameSet.addAll(beanNameList);
        }
        return beanNameSet;
    }

    private Set<String> importScanForName(Class<?> configClazz){
        Import anno = configClazz.getAnnotation(Import.class);
        if (anno == null){
            return Collections.emptySet();
        }
        Class<?>[] importClazz = anno.value();
        if (importClazz == null || importClazz.length == 0){
            return Collections.emptySet();
        }
        return Arrays.stream(importClazz)
                .map(Class::getName)
                .collect(Collectors.toSet());
    }

    private List<String> getScanPackage(Class<?> configClazz){
        //进行组件扫描
        //查看扫描注解是否存在
        ComponentScan anno = ClassUtil.getAnnotation(configClazz, ComponentScan.class);
        if (anno == null){
            throw new RuntimeException("ComponentScan not exist");
        }
        String[] values = anno.value();
        if (values == null || values.length == 0){
            String packageName = configClazz.getPackage().getName();
            return Collections.singletonList(packageName);
        }else{
            return Arrays.asList(values);
        }
    }

    private Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet){
        HashMap<String, BeanDefinition> map = new HashMap<>();
        for (String className : classNameSet) {
            //获取class
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            //判断class是否标注了Component注解
            Component anno = ClassUtil.getAnnotation(clazz, Component.class);
            if (anno == null){
                continue;
            }
            String beanName = ClassUtil.getBeanName(clazz);
            BeanDefinition beanDefinition = BeanDefinition.builder()
                    .name(beanName)
                    .constructor(ClassUtil.getSuitbaleConstructor(clazz))
                    .beanClass(clazz)
                    .order(getOrder(clazz))
                    .primary(clazz.isAnnotationPresent(Primary.class))
//                    .instance()
                    .initMethod(ClassUtil.findAnnoMethod(clazz, PostConstruct.class))
                    .initMethodName(null)
                    .destoryMethod(ClassUtil.findAnnoMethod(clazz, PreDestroy.class))
                    .destroyMethodName(null)
//                    .factoryName()
//                    .factoryMethod()
                    .build();
            map.put(beanName, beanDefinition);
            //注入Configuration下的@Bean
            Configuration configAnno = ClassUtil.getAnnotation(clazz, Configuration.class);
            if (configAnno != null){
                //查找@Bean方法,@Bean通过方法名或注解val进行匹配, 不通过返回类型进行匹配
                scanFactoryMethods(beanName, clazz, map);
            }
        }
        return map;
    }

    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> defs) {
        for (Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
                }
                if (Modifier.isFinal(mod)) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }
                Class<?> beanClass = method.getReturnType();
                if (beanClass.isPrimitive()) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return primitive type.");
                }
                if (beanClass == void.class || beanClass == Void.class) {
                    throw new RuntimeException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }
                BeanDefinition beanDefinition = BeanDefinition.builder()
                        .name(ClassUtil.getBeanName(method))
                        .beanClass(beanClass)
                        .factoryMethod(method)
                        .factoryName(factoryBeanName)
                        .order(getOrder(method))
                        .primary(method.isAnnotationPresent(Primary.class))
                        .initMethod(null)
                        .initMethodName(bean.initMethod().isEmpty() ? null : bean.initMethod())
                        .destoryMethod(null)
                        .destroyMethodName(bean.destroyMethod().isEmpty() ? null : bean.destroyMethod())
                        .build();
                addBeanDefinitions(defs, beanDefinition);
            }
        }
    }

    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new RuntimeException("Duplicate bean name: " + def.getName());
        }
    }


    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

}
