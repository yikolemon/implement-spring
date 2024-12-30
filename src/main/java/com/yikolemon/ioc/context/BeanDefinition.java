package com.yikolemon.ioc.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author duanfuqiang
 * @date 2024/12/26
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BeanDefinition {

    String name;

    Class<?> beanClass;

    Object instance = null;

    //构造方法,理应有多个
    Constructor<?> constructor;

    //工厂方法
    Method factoryMethod;

    //工厂方法名称
    String factoryName;

    //顺序
    int order;

    Boolean primary;

    String initMethodName;

    Method initMethod;

    String destroyMethodName;

    Method destoryMethod;

    Boolean configurationDefinition;

}
