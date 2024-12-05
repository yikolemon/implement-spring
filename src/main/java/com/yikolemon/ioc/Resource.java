package com.yikolemon.ioc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * @author duanfuqiang
 * @date 2024/12/5
 **/
@AllArgsConstructor
@Data
@NoArgsConstructor
public class Resource {

    private Path path;

    private String name;

    private ResourceType type;

}
