package com.yikolemon.ioc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author duanfuqiang
 * @date 2024/12/5
 **/
@AllArgsConstructor
@Data
@NoArgsConstructor
public class Resource {

    private String path;

    private String name;
}
