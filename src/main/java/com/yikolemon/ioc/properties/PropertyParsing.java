package com.yikolemon.ioc.properties;

/*
  @author yikolemon
 * @date 2024/12/6
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * 配置解析实体类,对应每个@Value
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PropertyParsing {

    private String key;

    private String defaultVal;

    /**
     * 通过@Value(xxx)内的xxx构建对象
     * @param str 如 ${db.url} 或${port:8080}
     */
    public PropertyParsing(String str) throws ValueInjectException {
        if (StringUtils.isEmpty(str)){
            throw ValueInjectException.nullKey(str);
        }
        if (str.startsWith("${") && str.endsWith("}")){
            //defaultVal
            int divideIndex = str.indexOf(":");
            if (divideIndex == -1){
                //不存在分隔符
                String key = str.substring(2, str.length() - 1);
                this.defaultVal = null;
                this.key = key;
            }else{
                // 有default，提取key和defaultVal
                String key = str.substring(2, divideIndex);
                if (StringUtils.isEmpty(key)){
                    throw ValueInjectException.nullKey(str);
                }
                String defaultVal = str.substring(divideIndex, str.length() - 1);
                this.key = key;
                this.defaultVal = defaultVal;
            }
        }
    }



}
