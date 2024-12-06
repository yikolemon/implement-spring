package com.yikolemon.ioc;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author duanfuqiang
 * @date 2024/12/5
 **/
@AllArgsConstructor
public class ResourceResolver {

    private String basePackage;

    public <T> List<T> scan(Function<Resource, T> mapper){
        String packagePath = this.basePackage.replace(".", "/");
        try {
            return scan0(mapper, packagePath);
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> List<T> scan0(Function<Resource, T> mapper, String packagePath) throws URISyntaxException, IOException {
        ClassLoader classLoader = getClassLoader();
        //resources 包含目录和文件
        Enumeration<URL> resources = classLoader.getResources(packagePath);
        List<T> res = new ArrayList<>();
        while (resources.hasMoreElements()){
            URL url = resources.nextElement();
            URI uri = url.toURI();
            String path = removeTrailingSlash(uriToString(uri));
            //jar包下扫描
            if (path.startsWith("jar:")){
                res.addAll(scanJar(packagePath, uri, mapper));
            } else if (path.startsWith("file:")){
                //普通目录下扫描
                res.addAll(scanFile(uri, mapper));
            }
        }
        return res;
    }

    /**
     *
     * @param packagePath 包路径 /cn/hutool
     * @param uri classLoader查找出的资源的路径,在jar中的形式为/xxx/xxx/hutool.jar/cn/hutool
     * @param mapper 函数接口
     * @return 过滤后资源
     * @param <T> 过滤后资源对象泛型
     */
    private <T> List<T> scanJar(String packagePath, URI uri, Function<Resource, T> mapper) throws IOException {
        FileSystem fileSystem = null;
        try{
            fileSystem = jarUriToPath(uri);
            Path basePath = fileSystem.getPath(packagePath);
            return scan(ResourceType.JAR, basePath, mapper);
        }finally {
            if (fileSystem != null && fileSystem.isOpen()){
                fileSystem.close();
            }
        }
    }


    private <T> List<T> scanFile(URI uri, Function<Resource, T> mapper) {
        // 根据路径递归扫描目录下的资源
        Path basePath = Paths.get(uri);
        return scan(ResourceType.FILE, basePath, mapper);
    }

    private  <T> List<T> scan(ResourceType resourceType, Path basePath, Function<Resource, T> mapper){
        if (ResourceType.FILE == resourceType){
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return Collections.emptyList();
            }
        }
        // 使用 NIO 的 Files.walk() 遍历目录
        try (Stream<Path> walk  = Files.walk(basePath)){
            // 使用 Files.walk() 递归遍历目录
            return walk.filter(Files::isRegularFile)  // 只处理文件
                    .map(filePath -> {
                        // 构造 Resource 对象，可以使用 filePath 的路径和名称
                        Resource resource = new Resource(filePath,
                                filePath.getFileName().toString(), resourceType);
                        // 使用 mapper 将资源转换成目标类型 T
                        return mapper.apply(resource);
                    })
                    .collect(Collectors.toList());  // 收集所有转换后的元素到 List 中
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    /**
     *
     * @param jarUri classLoader查找出的资源的路径,在jar中的形式为/xxx/xxx/hutool.jar/cn/hutool
     * @return ZipPath
     */
    FileSystem jarUriToPath(URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Collections.emptyMap());
    }

    /**
     * 去除末尾的/斜线
     * @param s path
     * @return 去除末尾/的path
     */
    private String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private String uriToString(URI uri) throws UnsupportedEncodingException {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8.name());
    }

    private ClassLoader getClassLoader(){
        //如果是WEB应用，拿到的Thread是Servlet提供的
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader != null){
            return classLoader;
        }
        return this.getClassLoader();
    }

    public static void main(String[] args) {
        ResourceResolver resolver = new ResourceResolver("com.yikolemon");
        List<String> classList = resolver.scan(resource -> {
            String name = resource.getName();
            if (name.endsWith(".class")) {
                // 把"org/example/Hello.class"变为"org.example.Hello":
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            //非class文件
            return null;
        });
        for (String className : classList) {
            System.out.println(className);
        }
    }
}
