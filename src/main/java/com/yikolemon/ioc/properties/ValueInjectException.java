package com.yikolemon.ioc.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author yikolemon
 * @date 2024/12/6
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class ValueInjectException extends Exception{

    private String fieldName; // 出错的字段名

    private String msg;

    private ValueInjectException(String fieldName, String msg){
        this.fieldName = fieldName;
        this.msg = msg;
    }

    public static ValueInjectException notExist(String fieldName){
        return new ValueInjectException(fieldName, "property not exist");
    }

    public static ValueInjectException nullKey(String fieldName){
        return new ValueInjectException(fieldName, "null parsing text");
    }

}
