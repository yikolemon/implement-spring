package com.yikolemon.ioc.context;

import com.sun.istack.internal.Nullable;
import com.yikolemon.ioc.properties.PropertyResolver;
import com.yikolemon.ioc.resource.ResourceResolver;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
public class AnnotationConfigApplicationContext {

    Map<String, BeanDefinition> nameToBeans;

    Map<Class<?>, BeanDefinition> clazzToBeans;

    public AnnotationConfigApplicationContext(Class<?> configClazz, PropertyResolver propertyResolver) {
        ResourceScanner resourceScanner = new ResourceScanner();
        Set<String> clazzNameSet = resourceScanner.scanForClazzName(configClazz);


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
                .filter(BeanDefinition::isPrimary)
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



}
