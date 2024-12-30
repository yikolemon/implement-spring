package com.yikolemon.ioc.context;

import com.sun.istack.internal.Nullable;
import com.yikolemon.ioc.annotation.Autowired;
import com.yikolemon.ioc.annotation.Value;
import com.yikolemon.ioc.properties.PropertyResolver;
import com.yikolemon.ioc.properties.ValueInjectException;
import com.yikolemon.ioc.util.ClassUtil;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
public class AnnotationConfigApplicationContext {

    Map<String, BeanDefinition> nameToBeans;

    Map<Class<?>, BeanDefinition> clazzToBeans;

    Set<String> creatingBeanNames;

    public Object createBeanAsEarlySingleton(BeanDefinition def) throws ValueInjectException {
        if (!this.creatingBeanNames.add(def.getName())){
            //检测到重复创建Bean导致的循环依赖
            throw new RuntimeException("repeat create error");
        }
        //获取创建Bean的构造方法或者工厂方法
        Executable createFun = def.getFactoryName() == null ? def.getConstructor() : def.getFactoryMethod();
        Parameter[] parameters = createFun.getParameters();
        Object[] args = new Object[parameters.length];
        boolean isConfiguration = isConfigurationDefinition(def);
        for (int i = 0; i < parameters.length; i++) {
            Value value = ClassUtil.getAnnotation(parameters[i], Value.class);
            Autowired autowired = ClassUtil.getAnnotation(parameters[i], Autowired.class);
            if (isConfiguration && autowired != null){
                throw new RuntimeException("cannot specify @Autowired when creating @Configuration Bean");
            }
            //参数需要@Value或者@Autowired两者之一
            if (value != null && autowired != null){
                throw new RuntimeException("cannot specify both @Autowired and @Value at same time");
            }
            if (value == null && autowired == null) {
                throw new RuntimeException("must specify @Autowired or @Value");
            }
            //参数类型
            Class<?> type = parameters[i].getType();
            //注入@Value
            if (value != null){
                args[i] = PropertyResolver.getRequiredProperty(value.value(), type);
            }
            if (autowired != null){
                String name = def.getName();
                BeanDefinition dependencyDef = StringUtils.isEmpty(name) ? findPrimaryBeanDefinition(type) :
                        findBeanDefinition(name, type);
                Object dependencyInstance = dependencyDef.getInstance();
                if (dependencyInstance == null){
                    //依赖类需要初始化
                    dependencyInstance = createBeanAsEarlySingleton(dependencyDef);
                }
                args[i] = dependencyInstance;
            }
        }
        //创建实例
        Object instance;
        if (def.getConstructor() != null){
            //构造方法
            try {
                instance = def.getConstructor().newInstance(args);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(String.format("Exception when creating bean '%s : '%s'",
                        def.getName(), e.getMessage()));
            }
        }else{
            Object bean = getBean(def.getFactoryName());
            //使用工厂方法构建
            Method factoryMethod = def.getFactoryMethod();
            try {
                instance =  factoryMethod.invoke(bean, args);
            } catch (Exception e) {
                throw new RuntimeException("invoke factory method creating bean error");
            }
        }
        //TODO BeanPostProcessor注入
        return instance;
    }


    public AnnotationConfigApplicationContext(Class<?> configClazz, PropertyResolver propertyResolver) throws NoSuchMethodException, ValueInjectException {
        ResourceScanner resourceScanner = new ResourceScanner();
        Set<String> clazzNameSet = resourceScanner.scanForClazzName(configClazz);
        nameToBeans = resourceScanner.createBeanDefinitions(clazzNameSet);
        //创建中集合
        this.creatingBeanNames = new HashSet<>();
        //创建@Configuration类型的Bean
        List<BeanDefinition> configBeanDefList = nameToBeans.values().stream()
                .filter(BeanDefinition::getConfigurationDefinition)
                .collect(Collectors.toList());
        for (BeanDefinition def : configBeanDefList) {
            createBeanAsEarlySingleton(def);
        }
        //创建剩余的Bean
        List<BeanDefinition> restDefList = nameToBeans.values().stream()
                .filter(def -> def.getInstance() == null).collect(Collectors.toList());
        for (BeanDefinition def : restDefList) {
            createBeanAsEarlySingleton(def);
        }
        nameToBeans.values().forEach(definition -> {
            //通过definition注入bean
        });

    }

    @Nullable
    public BeanDefinition findBeanDefinition(String name){
        return this.nameToBeans.get(name);
    }

    @Nullable
    public List<BeanDefinition> findBeanDefinition(Class<?> clazz){
        return this.clazzToBeans.values().stream()
                .filter(def -> clazz.isAssignableFrom(def.getBeanClass()))
                .sorted()
                .collect(Collectors.toList());
    }


    private Object getBean(String name){
        BeanDefinition beanDefinition = nameToBeans.get(name);
        Objects.requireNonNull(beanDefinition);
        Object instance = beanDefinition.getInstance();
        Objects.requireNonNull(instance);
        return instance;
    }

    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> clazz){
        BeanDefinition beanDefinition = findBeanDefinition(name);
        if (beanDefinition == null){
            return null;
        }
        if (!clazz.isAssignableFrom(beanDefinition.getBeanClass())){
            throw new RuntimeException("error bean class type");
        }
        return beanDefinition;
    }

    @Nullable
    public BeanDefinition findPrimaryBeanDefinition(Class<?> clazz){
        List<BeanDefinition> beanDefinitionList = findBeanDefinition(clazz);
        if (beanDefinitionList == null || beanDefinitionList.isEmpty()){
            return null;
        }
        if (beanDefinitionList.size() == 1){
            return beanDefinitionList.get(0);
        }
        //遍历查找primary
        List<BeanDefinition> primaryBeanDefinitionListen = beanDefinitionList.stream()
                .filter(BeanDefinition::getPrimary)
                .collect(Collectors.toList());
        if (primaryBeanDefinitionListen.size() == 0){
            //抛出异常
            throw new RuntimeException("no primary bean");
        }else if (primaryBeanDefinitionListen.size() >= 2){
            //找出primary的res
            throw new RuntimeException("two many primary beans");
        }else{
            return primaryBeanDefinitionListen.get(0);
        }
    }


    private static boolean isConfigurationDefinition(BeanDefinition def){
        return def.getConfigurationDefinition();
    }

    private static boolean isBeanPostProcessorDefinition(BeanDefinition def){
        //TODO
        return false;
    }

    private static void injectBean(BeanDefinition def){
        Object instance = def.getInstance();
        injectProperties(def, def.getBeanClass(), def.getInstance());
    }

    private static void injectProperties(BeanDefinition def, Class<?> clazz, Object instance){
        for (Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, def.beanClass, def.getInstance(), f);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, def.beanClass, def.getInstance(), m);
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null){
            injectProperties(def, superclass, instance);
        }
    }

    private static void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object instance,
                                            AccessibleObject acc){
        Value valAnno = acc.getAnnotation(Value.class);
        Autowired autowiredAnno = acc.getAnnotation(Autowired.class);

        //不需要注入
        if (valAnno == null && autowiredAnno == null){
            return;
        }
        if (acc instanceof Field){
            Field f = (Field) acc;

        }

    }




}
