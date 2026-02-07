# IDEA编译设置 - 解决Lombok编译错误

## 问题
```
Class com.sun.tools.javac.tree.JCTree$JCImport does not have member field 'com.sun.tools.javac.tree.JCTree qualid'
```

## 解决方案：让IDEA使用Maven编译

### 步骤1：打开设置
File → Settings （或按 Ctrl + Alt + S）

### 步骤2：配置Maven编译
1. 找到：Build, Execution, Deployment → Build Tools → Maven → Runner
2. 勾选：**Delegate IDE build/run actions to Maven**

### 步骤3：配置注解处理器
1. 找到：Build, Execution, Deployment → Compiler → Annotation Processors
2. 勾选：**Enable annotation processing**

### 步骤4：应用并重启
1. 点击 Apply → OK
2. File → Invalidate Caches → Invalidate and Restart

### 步骤5：重新启动服务
重新运行 SeckillServiceApplication

---

## 如果还不行，用命令行启动（100%成功）

```cmd
cd D:\code\cloud-demo\cloud-demo\seckill-demo\seckill-service
mvn spring-boot:run
```
