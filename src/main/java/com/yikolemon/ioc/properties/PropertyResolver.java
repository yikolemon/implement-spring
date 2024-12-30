package com.yikolemon.ioc.properties;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * @author yikolemon
 * @date 2024/12/6
 **/
public class PropertyResolver {

    private PropertyResolver() {
    }

     private static final Map<String, String> PROPERTIES_MAP = new HashMap<>();

     //类型转换方法map
    private static final Map<Class<?>, Function<String, Object>> CONVERTERS = new HashMap<>();

    static {
        //放入系统props
        PROPERTIES_MAP.putAll(System.getenv());
        //读取配置文件，加载配置项
        try {
            readPropertiesFile();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        // String类型:
        CONVERTERS.put(String.class, s -> s);
        // boolean类型:
        CONVERTERS.put(boolean.class, Boolean::parseBoolean);
        CONVERTERS.put(Boolean.class, Boolean::valueOf);
        // int类型:
        CONVERTERS.put(int.class, Integer::parseInt);
        CONVERTERS.put(Integer.class, Integer::valueOf);
        // 其他基本类型...
        // Date/Time类型:
        CONVERTERS.put(LocalDate.class, LocalDate::parse);
        CONVERTERS.put(LocalTime.class, LocalTime::parse);
        CONVERTERS.put(LocalDateTime.class, LocalDateTime::parse);
        CONVERTERS.put(ZonedDateTime.class, ZonedDateTime::parse);
        CONVERTERS.put(Duration.class, Duration::parse);
        CONVERTERS.put(ZoneId.class, ZoneId::of);
    }


    public static <T> T getRequiredProperty(String key, Class<T> tClass) throws ValueInjectException{
        T property = getProperty(key, tClass);
        Objects.requireNonNull(property);
        return property;
    }

    public static <T> T getProperty(String key, Class<T> tClass) throws ValueInjectException {
         String val = getProperty(key);
         return convert(val, tClass);
     }

    // 转换到指定Class类型:
     private static <T> T convert(String val, Class<T> tClass){
         Function<String, Object> fn = CONVERTERS.get(tClass);
         if (fn == null) {
             throw new IllegalArgumentException("Unsupported value type: " + tClass.getName());
         }
         return (T) fn.apply(val);
     }

     private static String getProperty(String key) throws ValueInjectException {
         PropertyParsing keyParsing = new PropertyParsing(key);
         String defaultVal = keyParsing.getDefaultVal();
         String mapVal = PROPERTIES_MAP.get(key);
         if (StringUtils.isEmpty(mapVal) && StringUtils.isEmpty(defaultVal)){
             throw ValueInjectException.notExist(key);
         }else if (!StringUtils.isEmpty(mapVal)){
             return mapVal;
         }else {
//             !StringUtils.isEmpty(defaultVal)
             return defaultVal;
         }
     }

     public static void addProperties(Properties props){
        if (props == null){
            return;
        }
         //存储入参,遍历
         Set<String> propKeys = props.stringPropertyNames();
         propKeys.forEach(k ->{
             String v = props.getProperty(k);
             PROPERTIES_MAP.put(k, v);
         });
     }



     private static void readPropertiesFile() throws IOException, URISyntaxException {
         ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
         Enumeration<URL> resources = classLoader.getResources("application.properties");
         while (resources.hasMoreElements()) {
             URL url = resources.nextElement();
             Path path = Paths.get(url.toURI());
             if (Files.exists(path)) {
                 Properties properties = new Properties();
                 try (InputStream inputStream = Files.newInputStream(path)) {
                     properties.load(inputStream);
                 }
                 //读取到文件中
                 addProperties(properties);
             }
         }
     }

    public static void main(String[] args) {
        PROPERTIES_MAP.forEach((key, value) -> System.out.println("key: " + key + ",value: " + value));
    }

}
