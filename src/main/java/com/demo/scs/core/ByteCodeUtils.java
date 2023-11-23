package com.demo.scs.core;

import java.util.List;

import javassist.ClassClassPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.util.CollectionUtils;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;

/**
 * 字节码操作工具 依赖javassist
 *
 * @Author: Hu Xin
 * @Date: 2023/1/15 13:41
 * @Desc:
 **/
@Slf4j
public class ByteCodeUtils {

    public static final String SCS_DEFINITION_COLLECTOR_CLASS = "com.demo.scs.core.ScsDefinitionCollector";

    /**
     * 生成Processor里的接口和@Input@Output注解
     *
     * @return
     * @throws Exception
     */
    public static Class<?> generateMethodForInputAndOutputBinding(List<String> inputs, List<String> outputs)
        throws Exception {
        ClassPool pool = ClassPool.getDefault();
        pool.insertClassPath(new ClassClassPath(SubscribableChannel.class));
        pool.insertClassPath(new ClassClassPath(MessageChannel.class));
        CtClass inputReturnType = pool.getCtClass(SubscribableChannel.class.getName());
        CtClass outputReturnType = pool.getCtClass(MessageChannel.class.getName());
        CtClass ctClass = pool.getCtClass(SCS_DEFINITION_COLLECTOR_CLASS);

        if (!CollectionUtils.isEmpty(inputs)) {
            for (String input : inputs) {
                // 增加的方法
                CtMethod ctMethod = new CtMethod(inputReturnType, input, new CtClass[] {}, ctClass);
                // 获取方法信息和常量池
                MethodInfo methodInfo = ctMethod.getMethodInfo();
                ConstPool constPool = methodInfo.getConstPool();

                AnnotationsAttribute methodAnnotation =
                    new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                // 增加的注解
                Annotation inputAnnotation = new Annotation(Input.class.getTypeName(), constPool);
                inputAnnotation.addMemberValue("value", new StringMemberValue(input, constPool));
                // 注解增加到方法上
                methodAnnotation.addAnnotation(inputAnnotation);
                methodInfo.addAttribute(methodAnnotation);
                ctClass.addMethod(ctMethod);
            }
        }
        if (!CollectionUtils.isEmpty(outputs)) {
            for (String output : outputs) {
                // 增加的方法
                CtMethod ctMethod = new CtMethod(outputReturnType, output, new CtClass[] {}, ctClass);
                // 获取方法信息和常量池
                MethodInfo methodInfo = ctMethod.getMethodInfo();
                ConstPool constPool = methodInfo.getConstPool();

                AnnotationsAttribute methodAnnotation =
                    new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                // 增加的注解
                Annotation inputAnnotation = new Annotation(Output.class.getTypeName(), constPool);
                inputAnnotation.addMemberValue("value", new StringMemberValue(output, constPool));
                // 注解增加到方法上
                methodAnnotation.addAnnotation(inputAnnotation);
                methodInfo.addAttribute(methodAnnotation);
                ctClass.addMethod(ctMethod);
            }
        }
        Class<?> aClass = ctClass.toClass();
        return aClass;
    }

}
