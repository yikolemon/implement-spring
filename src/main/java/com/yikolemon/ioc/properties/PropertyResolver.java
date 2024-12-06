package com.yikolemon.ioc.properties;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author yikolemon
 * @date 2024/12/6
 **/
public class PropertyResolver {

     Map<String, String> propertiesMap= new HashMap<>();

     private String getProperty(String key){
         return propertiesMap.get(key);
     }

    public PropertyResolver(Properties props) {
        //放入系统props
        this.propertiesMap.putAll(System.getenv());
        //存储入参,遍历
        Set<String> propKeys = props.stringPropertyNames();
        propKeys.forEach(k ->{
            String v = props.getProperty(k);
            propertiesMap.put(k, v);
        });
    }
}
