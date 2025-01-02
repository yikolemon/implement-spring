package com.yikolemon.ioc.context;

import com.sun.istack.internal.Nullable;
import com.yikolemon.ioc.annotation.Autowired;
import com.yikolemon.ioc.annotation.Value;
import com.yikolemon.ioc.properties.PropertyResolver;
import com.yikolemon.ioc.properties.ValueInjectException;
import com.yikolemon.ioc.util.ClassUtil;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
public class AnnotationConfigApplicationContext implements Serializable {
    private static final long serialVersionUID = -7780096685700083702L;

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
        //创建bean
        createBeans();
        //注入bean
        injectBeans();
    }

    private void injectBeans(){
        nameToBeans.values().forEach(definition -> {
            //通过definition注入bean
            try {
                injectBean(definition);
            } catch (ValueInjectException | IllegalAccessException | InvocationTargetException  e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void createBeans() throws ValueInjectException {
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

    private Object getBean(Class<?> type){
        BeanDefinition beanDefinition = findPrimaryBeanDefinition(type);
        return beanDefinition.getInstance();
    }

    private Object getBean(String name, Class<?> type){
        BeanDefinition beanDefinition = findBeanDefinition(name, type);
        return beanDefinition.getInstance();
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

    private void injectBean(BeanDefinition def) throws ValueInjectException, InvocationTargetException, IllegalAccessException {
        Object instance = def.getInstance();
        injectProperties(def, def.getBeanClass(), instance);
    }

    private void injectProperties(BeanDefinition def, Class<?> clazz, Object instance) throws ValueInjectException, InvocationTargetException, IllegalAccessException {
        for (Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, def.beanClass, instance, f);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, def.beanClass, instance, m);
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null){
            injectProperties(def, superclass, instance);
        }
    }

    private void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object instance,
                                            AccessibleObject acc) throws ValueInjectException, IllegalAccessException, InvocationTargetException {
        Value valAnno = acc.getAnnotation(Value.class);
        Autowired autowiredAnno = acc.getAnnotation(Autowired.class);

        //不需要注入
        if (valAnno == null && autowiredAnno == null){
            return;
        }
        Field field = null;
        if (acc instanceof Field){
            Field f = (Field) acc;
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        }
        Method method = null;
        if (acc instanceof Method){
            Method m = (Method) acc;
            checkFieldOrMethod(m);
            if (m.getParameters().length < 1){
                throw new RuntimeException(String.format("Cannot inject a non-setter method %s for bean '%s': %s",
                        m.getName(), def.getName(), def.getBeanClass().getName()));
            }
            m.setAccessible(true);
            method = m;
        }

        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];
        //同时存在两个注解
        if (valAnno != null && autowiredAnno != null){
            throw new RuntimeException(String.format("Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s':'%s'",
                    clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName()));
        }

        //@Value注入
        if (valAnno != null){
            Object property = PropertyResolver.getRequiredProperty(valAnno.value(), accessibleType);
            if (field != null){
                //字段注入
                field.set(instance, property);
            }
            if (method != null){
                method.invoke(instance, property);
            }
        }

        if (autowiredAnno != null){
            String name = autowiredAnno.name();
            boolean required = autowiredAnno.value();
            Object depends = name.isEmpty() ? getBean(accessibleType) : getBean(name, accessibleType);
            if (required && depends == null){
                throw new RuntimeException("dependency bean not found when inject");
            }
            if (depends != null){
                if (field != null){
                    field.set(instance, depends);
                }
            }
            if (method != null){
                method.invoke(instance, depends);
            }
        }
    }

    private static void checkFieldOrMethod(Member m){
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)){
            throw new RuntimeException("cannot inject stastic field" + m);
        }
        if (Modifier.isFinal(mod)){
            if (m instanceof Field){
                throw new RuntimeException("cannot inject final field" + m);
            }
            if (m instanceof Method){
                System.out.println("inject final method should be careful because it is not called on target bean when bean is proxied and may cause NullPointerException.");
            }
        }
    }

}
